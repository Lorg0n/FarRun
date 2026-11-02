package dev.farrun.run.execution.gradle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GradleRuntimePreparerTest {
    @Test
    void keepsTheGradleProjectPathWhenPreparingApplicationTask() {
        assertEquals(":service:installDist", GradleRuntimePreparer.replacementTask(":service:run", "installDist"));
        assertEquals(":service:bootJar", GradleRuntimePreparer.replacementTask(":service:bootRun", "bootJar"));
        assertEquals("bootJar", GradleRuntimePreparer.replacementTask("bootRun", "bootJar"));
    }

    @Test
    void extractsApplicationArgumentsWithoutPassingThemToInstallDist() {
        assertEquals(List.of("vpn-client", "--config", "remote.properties"),
                GradleRuntimePreparer.programArguments("--args='vpn-client --config remote.properties'"));
        assertEquals(List.of("one", "two words"), GradleRuntimePreparer.programArguments("--args=one \"two words\""));
    }

    @Test
    void rejectsGradleParametersOtherThanApplicationArguments() {
        assertThrows(IllegalArgumentException.class, () -> GradleRuntimePreparer.programArguments("--info"));
    }
}
