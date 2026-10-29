package dev.farrun.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Non-sensitive, application-wide SSH server definition. */
public record Server(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull String host,
        int port,
        @NotNull String username,
        @NotNull AuthenticationMethod authenticationMethod,
        @Nullable String privateKeyPath,
        @Nullable String trustedHostKey
) {
    public Server {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(authenticationMethod, "authenticationMethod");
    }

    public static @NotNull Server newServer() {
        return new Server(UUID.randomUUID().toString(), "", "", 22, "", AuthenticationMethod.PASSWORD, null, null);
    }

    public @NotNull Server withTrustedHostKey(@Nullable String encodedHostKey) {
        return new Server(id, displayName, host, port, username, authenticationMethod, privateKeyPath, encodedHostKey);
    }

    @Override
    public String toString() {
        return displayName.isBlank() ? "Unnamed server" : displayName;
    }
}
