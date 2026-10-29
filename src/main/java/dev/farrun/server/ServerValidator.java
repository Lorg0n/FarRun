package dev.farrun.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.IDN;

/** Validation shared by settings editing and connection attempts. */
public final class ServerValidator {

    private ServerValidator() {
    }

    public static @Nullable String validate(@NotNull Server server) {
        if (server.displayName().isBlank()) {
            return "Enter a display name.";
        }
        if (server.host().isBlank()) {
            return "Enter a host name or IP address.";
        }
        try {
            IDN.toASCII(server.host().trim());
        } catch (IllegalArgumentException exception) {
            return "Enter a valid host name or IP address.";
        }
        if (server.port() < 1 || server.port() > 65_535) {
            return "SSH port must be between 1 and 65535.";
        }
        if (server.username().isBlank()) {
            return "Enter an SSH username.";
        }
        if (server.authenticationMethod() == AuthenticationMethod.PRIVATE_KEY
                && (server.privateKeyPath() == null || server.privateKeyPath().isBlank())) {
            return "Choose a private key file.";
        }
        return null;
    }
}
