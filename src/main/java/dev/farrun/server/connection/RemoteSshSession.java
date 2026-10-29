package dev.farrun.server.connection;

import dev.farrun.server.AuthenticationMethod;
import dev.farrun.server.SecretStore;
import dev.farrun.server.Server;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;

/** Owns one authenticated SSH client and session for exactly one FarRun launch. */
public final class RemoteSshSession implements AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final SshClient client;
    private final ClientSession session;

    private RemoteSshSession(@NotNull SshClient client, @NotNull ClientSession session) {
        this.client = client;
        this.session = session;
    }

    public static @NotNull RemoteSshSession open(
            @NotNull Server server,
            @NotNull SecretStore secrets
    ) throws Exception {
        if (server.trustedHostKey() == null || server.trustedHostKey().isBlank()) {
            throw new IOException("SSH host key is not trusted. Test the connection first to verify its fingerprint.");
        }
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((session, address, key) -> verify(server, key));
        try {
            if (server.authenticationMethod() == AuthenticationMethod.PRIVATE_KEY) {
                Path keyPath = Path.of(server.privateKeyPath());
                if (!Files.isRegularFile(keyPath)) {
                    throw new IOException("The configured private key file does not exist.");
                }
                String passphrase = secrets.keyPassphrase(server.id());
                SshClient.setKeyPairProvider(client, keyPath, false, false,
                        passphrase == null ? FilePasswordProvider.EMPTY : FilePasswordProvider.of(passphrase));
            }
            client.start();
            ClientSession session = client.connect(server.username(), server.host(), server.port())
                    .verify(TIMEOUT).getSession();
            if (server.authenticationMethod() == AuthenticationMethod.PASSWORD) {
                String password = secrets.password(server.id());
                if (password == null || password.isEmpty()) {
                    throw new IOException("No password is saved for this SSH server.");
                }
                session.addPasswordIdentity(password);
            }
            session.auth().verify(TIMEOUT);
            return new RemoteSshSession(client, session);
        } catch (Throwable failure) {
            try {
                client.stop();
            } catch (Exception ignored) {
            }
            throw failure;
        }
    }

    public @NotNull ClientSession session() {
        return session;
    }

    @Override
    public void close() {
        try {
            session.close(true);
        } finally {
            client.stop();
        }
    }

    private static boolean verify(@NotNull Server server, @NotNull PublicKey key) {
        return server.trustedHostKey().equals(PublicKeyEntry.toString(key));
    }
}
