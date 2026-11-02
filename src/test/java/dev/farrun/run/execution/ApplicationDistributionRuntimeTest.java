package dev.farrun.run.execution;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationDistributionRuntimeTest {
    @Test
    void normalizesWindowsScriptSeparatorsForTheLinuxTarget() {
        ApplicationDistributionRuntime runtime = new ApplicationDistributionRuntime(
                Path.of("distribution"), "bin\\application", List.of(), 21);

        assertEquals("bin/application", runtime.scriptRelativePath());
    }
}
