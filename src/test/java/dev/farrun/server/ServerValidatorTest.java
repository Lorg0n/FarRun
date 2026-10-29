package dev.farrun.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServerValidatorTest {

    @Test
    void acceptsPasswordAndPrivateKeyServerDefinitions() {
        assertNull(ServerValidator.validate(new Server(
                "id", "IPv6 host", "2001:db8::5", 22, "root", AuthenticationMethod.PASSWORD, null, null
        )));
        assertNull(ServerValidator.validate(new Server(
                "id", "Key host", "build.example.test", 2222, "deploy", AuthenticationMethod.PRIVATE_KEY,
                "/keys/deploy", null
        )));
    }

    @Test
    void reportsActionableMissingAndInvalidValues() {
        assertEquals("Enter a display name.", ServerValidator.validate(new Server(
                "id", "", "host", 22, "root", AuthenticationMethod.PASSWORD, null, null
        )));
        assertEquals("SSH port must be between 1 and 65535.", ServerValidator.validate(new Server(
                "id", "Host", "host", 0, "root", AuthenticationMethod.PASSWORD, null, null
        )));
        assertEquals("Choose a private key file.", ServerValidator.validate(new Server(
                "id", "Host", "host", 22, "root", AuthenticationMethod.PRIVATE_KEY, null, null
        )));
    }
}
