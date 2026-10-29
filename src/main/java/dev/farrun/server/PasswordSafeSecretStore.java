package dev.farrun.server;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Stores SSH secrets in the IDE's credential store, never in persisted plugin state. */
public final class PasswordSafeSecretStore implements SecretStore {

    private static final String SERVICE = "FarRun SSH";
    private static final String PASSWORD = "password";
    private static final String KEY_PASSPHRASE = "key-passphrase";

    @Override
    public @Nullable String password(@NotNull String serverId) {
        return PasswordSafe.getInstance().getPassword(attributes(serverId, PASSWORD));
    }

    @Override
    public @Nullable String keyPassphrase(@NotNull String serverId) {
        return PasswordSafe.getInstance().getPassword(attributes(serverId, KEY_PASSPHRASE));
    }

    @Override
    public void savePassword(@NotNull String serverId, @Nullable String password) {
        PasswordSafe.getInstance().set(attributes(serverId, PASSWORD), credentials(password));
    }

    @Override
    public void saveKeyPassphrase(@NotNull String serverId, @Nullable String passphrase) {
        PasswordSafe.getInstance().set(attributes(serverId, KEY_PASSPHRASE), credentials(passphrase));
    }

    @Override
    public void delete(@NotNull String serverId) {
        savePassword(serverId, null);
        saveKeyPassphrase(serverId, null);
    }

    private static @NotNull CredentialAttributes attributes(@NotNull String serverId, @NotNull String kind) {
        String serviceName = CredentialAttributesKt.generateServiceName(SERVICE, serverId + "/" + kind);
        return new CredentialAttributes(serviceName, serverId);
    }

    private static @Nullable Credentials credentials(@Nullable String value) {
        return value == null || value.isEmpty() ? null : new Credentials(null, value);
    }
}
