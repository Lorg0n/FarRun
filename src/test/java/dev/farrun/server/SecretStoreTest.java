package dev.farrun.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;

class SecretStoreTest {

    @Test
    void deletingAServerRemovesBothKindsOfCredential() {
        InMemorySecretStore secrets = new InMemorySecretStore();
        secrets.savePassword("server", "password");
        secrets.saveKeyPassphrase("server", "passphrase");

        secrets.delete("server");

        assertNull(secrets.password("server"));
        assertNull(secrets.keyPassphrase("server"));
    }

    private static final class InMemorySecretStore implements SecretStore {
        private final Map<String, String> values = new HashMap<>();

        @Override public @Nullable String password(@NotNull String serverId) { return values.get(serverId + "/password"); }
        @Override public @Nullable String keyPassphrase(@NotNull String serverId) { return values.get(serverId + "/passphrase"); }
        @Override public void savePassword(@NotNull String serverId, @Nullable String password) { put(serverId + "/password", password); }
        @Override public void saveKeyPassphrase(@NotNull String serverId, @Nullable String passphrase) { put(serverId + "/passphrase", passphrase); }
        @Override public void delete(@NotNull String serverId) { savePassword(serverId, null); saveKeyPassphrase(serverId, null); }

        private void put(@NotNull String key, @Nullable String value) {
            if (value == null) values.remove(key); else values.put(key, value);
        }
    }
}
