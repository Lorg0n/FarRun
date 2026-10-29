package dev.farrun.server.connection;

import dev.farrun.server.AuthenticationMethod;
import dev.farrun.server.SecretStore;
import dev.farrun.server.Server;
import dev.farrun.server.ServerValidator;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.future.Cancellable;
import org.apache.sshd.common.future.WaitableFuture;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Performs one authenticated SSH handshake without opening a shell or executing a command. */
public final class SshConnectionTester {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(10);

    private final @NotNull SecretStore secrets;

    public SshConnectionTester(@NotNull SecretStore secrets) {
        this.secrets = secrets;
    }

    public @NotNull ConnectionResult test(@NotNull Server server) {
        return test(server, () -> false);
    }

    /** Executes a bounded handshake and actively closes pending SSH futures when cancelled. */
    public @NotNull ConnectionResult test(@NotNull Server server, @NotNull BooleanSupplier cancelled) {
        String validationMessage = ServerValidator.validate(server);
        if (validationMessage != null) {
            return ConnectionResult.failure(ConnectionResult.Status.FAILURE, validationMessage);
        }
        if (server.authenticationMethod() == AuthenticationMethod.PRIVATE_KEY
                && !Files.isRegularFile(Path.of(server.privateKeyPath()))) {
            return ConnectionResult.failure(ConnectionResult.Status.INVALID_PRIVATE_KEY, "The selected private key file does not exist.");
        }

        AtomicReference<HostKeyProblem> hostKeyProblem = new AtomicReference<>();
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.setServerKeyVerifier((session, address, key) -> verifyHostKey(server, key, hostKeyProblem));
            configurePrivateKey(client, server);
            client.start();
            ConnectFuture connection = client.connect(server.username(), server.host(), server.port());
            if (!await(connection, connection, CONNECT_TIMEOUT, cancelled)) {
                return ConnectionResult.failure(ConnectionResult.Status.CANCELLED, "Connection test cancelled.");
            }
            try (ClientSession session = connection.verify().getSession()) {
                if (server.authenticationMethod() == AuthenticationMethod.PASSWORD) {
                    String password = secrets.password(server.id());
                    if (password == null || password.isEmpty()) {
                        return ConnectionResult.failure(ConnectionResult.Status.AUTHENTICATION_FAILED,
                                "No password is saved for this server.");
                    }
                    session.addPasswordIdentity(password);
                }
                AuthFuture authentication = session.auth();
                if (!await(authentication, authentication, AUTH_TIMEOUT, cancelled)) {
                    return ConnectionResult.failure(ConnectionResult.Status.CANCELLED, "Connection test cancelled.");
                }
                authentication.verify();
                return ConnectionResult.success("Successfully connected to " + endpoint(server) + ".");
            } finally {
                client.stop();
            }
        } catch (Exception exception) {
            HostKeyProblem problem = hostKeyProblem.get();
            if (problem != null) {
                return ConnectionResult.hostKey(problem.status(), problem.message(), problem.encodedKey());
            }
            return classify(exception);
        }
    }

    private static boolean await(
            @NotNull WaitableFuture future,
            @NotNull Cancellable cancellable,
            @NotNull Duration timeout,
            @NotNull BooleanSupplier cancelled
    ) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!future.isDone()) {
            if (cancelled.getAsBoolean()) {
                cancellable.cancel();
                return false;
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                cancellable.cancel();
                throw new SocketTimeoutException("SSH operation timed out");
            }
            future.await(Math.max(1, Math.min(Duration.ofNanos(remainingNanos).toMillis(), 100)));
        }
        return !cancelled.getAsBoolean();
    }

    private void configurePrivateKey(@NotNull SshClient client, @NotNull Server server) throws Exception {
        if (server.authenticationMethod() != AuthenticationMethod.PRIVATE_KEY) {
            return;
        }
        String passphrase = secrets.keyPassphrase(server.id());
        SshClient.setKeyPairProvider(
                client,
                Path.of(server.privateKeyPath()),
                false,
                false,
                passphrase == null ? FilePasswordProvider.EMPTY : FilePasswordProvider.of(passphrase)
        );
    }

    private static boolean verifyHostKey(
            @NotNull Server server,
            @NotNull java.security.PublicKey key,
            @NotNull AtomicReference<HostKeyProblem> problem
    ) {
        String encoded = PublicKeyEntry.toString(key);
        String fingerprint = KeyUtils.getFingerPrint(key);
        if (server.trustedHostKey() == null || server.trustedHostKey().isBlank()) {
            problem.set(new HostKeyProblem(
                    ConnectionResult.Status.UNKNOWN_HOST_KEY,
                    "The server host key is unknown. Verify fingerprint " + fingerprint + " before trusting it.",
                    encoded
            ));
            return false;
        }
        if (!server.trustedHostKey().equals(encoded)) {
            problem.set(new HostKeyProblem(
                    ConnectionResult.Status.HOST_KEY_CHANGED,
                    "The server host key has changed (presented fingerprint " + fingerprint + "). Connection was blocked.",
                    encoded
            ));
            return false;
        }
        return true;
    }

    private static @NotNull ConnectionResult classify(@NotNull Exception exception) {
        Throwable cause = rootCause(exception);
        if (cause instanceof ConnectException) {
            return ConnectionResult.failure(ConnectionResult.Status.CONNECTION_REFUSED, "Connection refused by the SSH server.");
        }
        if (cause instanceof SocketTimeoutException || hasMessage(cause, "timeout")) {
            return ConnectionResult.failure(ConnectionResult.Status.CONNECTION_TIMEOUT, "Connection timed out.");
        }
        if (cause instanceof NoRouteToHostException || hasMessage(cause, "network is unreachable")) {
            return ConnectionResult.failure(ConnectionResult.Status.NETWORK_ERROR, "Network is unreachable.");
        }
        if (hasMessage(cause, "auth") || hasMessage(cause, "permission denied")) {
            return ConnectionResult.failure(ConnectionResult.Status.AUTHENTICATION_FAILED, "SSH authentication failed.");
        }
        if (hasMessage(cause, "key") || hasMessage(cause, "pem")) {
            return ConnectionResult.failure(ConnectionResult.Status.INVALID_PRIVATE_KEY, "The private key cannot be read or decrypted.");
        }
        return ConnectionResult.failure(ConnectionResult.Status.FAILURE, "Unable to establish an SSH connection.");
    }

    private static @NotNull Throwable rootCause(@NotNull Throwable exception) {
        Throwable result = exception;
        while (result.getCause() != null && result.getCause() != result) {
            result = result.getCause();
        }
        return result;
    }

    private static boolean hasMessage(@NotNull Throwable throwable, @NotNull String fragment) {
        String message = throwable.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(fragment);
    }

    private static @NotNull String endpoint(@NotNull Server server) {
        return server.username() + "@" + server.host() + ":" + server.port();
    }

    private record HostKeyProblem(
            @NotNull ConnectionResult.Status status,
            @NotNull String message,
            @NotNull String encodedKey
    ) {
    }
}
