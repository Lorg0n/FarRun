package dev.farrun.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Isolates the credential store from server persistence and the settings UI. */
public interface SecretStore {

    @Nullable String password(@NotNull String serverId);

    @Nullable String keyPassphrase(@NotNull String serverId);

    void savePassword(@NotNull String serverId, @Nullable String password);

    void saveKeyPassphrase(@NotNull String serverId, @Nullable String passphrase);

    void delete(@NotNull String serverId);
}
