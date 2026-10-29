package dev.farrun.server.connection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Deliberately sanitized outcome of an SSH authentication check. */
public record ConnectionResult(@NotNull Status status, @NotNull String message, @Nullable String hostKey) {

    public enum Status {
        SUCCESS,
        UNKNOWN_HOST_KEY,
        HOST_KEY_CHANGED,
        CONNECTION_REFUSED,
        CONNECTION_TIMEOUT,
        NETWORK_ERROR,
        AUTHENTICATION_FAILED,
        INVALID_PRIVATE_KEY,
        CANCELLED,
        FAILURE
    }

    public static @NotNull ConnectionResult success(@NotNull String message) {
        return new ConnectionResult(Status.SUCCESS, message, null);
    }

    public static @NotNull ConnectionResult failure(@NotNull Status status, @NotNull String message) {
        return new ConnectionResult(status, message, null);
    }

    public static @NotNull ConnectionResult hostKey(@NotNull Status status, @NotNull String message, @NotNull String key) {
        return new ConnectionResult(status, message, key);
    }
}
