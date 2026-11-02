package dev.farrun.run.execution.remote;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteCommandSafetyTest {
    @Test
    void redactsSensitiveValuesAndUploadedClasspath() {
        String command = RemoteJavaProcessHandler.safeCommand(List.of("java", "-cp", "/tmp/private", "-Dtoken=abc",
                "--password", "hunter2", "https://alice:secret@example.test", "Main"));
        assertTrue(command.contains("<uploaded-classpath>"));
        assertTrue(command.contains("<redacted>"));
        assertFalse(command.contains("hunter2"));
        assertFalse(command.contains("alice:secret"));
        assertFalse(command.contains("abc"));
    }

    @Test
    void preservesGeneratedDistributionFilenamesButSanitizesIndependentInputs() {
        Path dependency = Path.of("library+native 1.jar");

        assertEquals("library+native 1.jar", RemoteJavaProcessHandler.entryName(dependency, true));
        assertEquals("library_native_1.jar", RemoteJavaProcessHandler.entryName(dependency, false));
    }
}
