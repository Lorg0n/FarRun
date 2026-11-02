package dev.farrun.run.execution;

import dev.farrun.run.configuration.RuntimeFileMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeInputsTest {
    @TempDir
    Path project;

    @Test
    void resolvesRelativeFilesAndExpandsExplicitAliases() throws Exception {
        Path config = project.resolve("config.properties");
        Files.writeString(config, "enabled=true");
        RuntimeInputs inputs = new RuntimeInputs(project, List.of(new RuntimeFileMapping("config", "config.properties")));

        assertEquals(List.of("--config", "/tmp/farrun/run/inputs/config/config.properties"),
                inputs.expandArguments(List.of("--config", "${farrun:config}"), "/tmp/farrun/run/inputs"));
    }

    @Test
    void rejectsUnknownAliasBeforeRemoteExecution() {
        RuntimeInputs inputs = new RuntimeInputs(project, List.of());

        assertThrows(IllegalArgumentException.class,
                () -> inputs.expandArguments(List.of("${farrun:missing}"), "/tmp/farrun/run/inputs"));
    }
}
