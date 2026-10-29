package dev.farrun.server.connection;

import dev.farrun.server.AuthenticationMethod;
import dev.farrun.server.SecretStore;
import dev.farrun.server.Server;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SshConnectionTesterTest {

    @Test
    void requiresTrustForANewHostKeyThenAuthenticatesWithAPassword() throws Exception {
        SshServer sshServer = SshServer.setUpDefaultServer();
        sshServer.setHost("127.0.0.1");
        sshServer.setPort(0);
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshServer.setPasswordAuthenticator((username, password, session) ->
                username.equals("deploy") && password.equals("secret"));
        sshServer.start();
        try {
            Server server = new Server(
                    "server", "Test server", "127.0.0.1", sshServer.getPort(), "deploy",
                    AuthenticationMethod.PASSWORD, null, null
            );
            SshConnectionTester tester = new SshConnectionTester(new PasswordSecretStore("secret"));

            ConnectionResult unknown = tester.test(server);
            ConnectionResult connected = tester.test(server.withTrustedHostKey(unknown.hostKey()));

            assertEquals(ConnectionResult.Status.UNKNOWN_HOST_KEY, unknown.status());
            assertEquals(ConnectionResult.Status.SUCCESS, connected.status());
        } finally {
            sshServer.stop();
        }
    }

    @Test
    void reportsAnAbsentPrivateKeyBeforeOpeningANetworkConnection() {
        Server server = new Server(
                "server", "Build server", "127.0.0.1", 22, "deploy",
                AuthenticationMethod.PRIVATE_KEY, "/path/that/does/not/exist", null
        );

        ConnectionResult result = new SshConnectionTester(new EmptySecretStore()).test(server);

        assertEquals(ConnectionResult.Status.INVALID_PRIVATE_KEY, result.status());
        assertEquals("The selected private key file does not exist.", result.message());
    }

    @Test
    void cancelsAPendingConnectionBeforeAuthentication() {
        Server server = new Server(
                "server", "Cancelled server", "192.0.2.1", 22, "deploy",
                AuthenticationMethod.PASSWORD, null, null
        );

        ConnectionResult result = new SshConnectionTester(new PasswordSecretStore("secret")).test(server, () -> true);

        assertEquals(ConnectionResult.Status.CANCELLED, result.status());
    }

    private static class EmptySecretStore implements SecretStore {
        @Override public @Nullable String password(@NotNull String serverId) { return null; }
        @Override public @Nullable String keyPassphrase(@NotNull String serverId) { return null; }
        @Override public void savePassword(@NotNull String serverId, @Nullable String password) { }
        @Override public void saveKeyPassphrase(@NotNull String serverId, @Nullable String passphrase) { }
        @Override public void delete(@NotNull String serverId) { }
    }

    private static final class PasswordSecretStore extends EmptySecretStore {
        private final String password;

        private PasswordSecretStore(String password) {
            this.password = password;
        }

        @Override
        public @Nullable String password(@NotNull String serverId) {
            return password;
        }
    }
}
