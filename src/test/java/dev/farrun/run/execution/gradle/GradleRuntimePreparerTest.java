package dev.farrun.run.execution.gradle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradleRuntimePreparerTest {
    @Test
    void keepsTheGradleProjectPathWhenPreparingApplicationTask() {
        assertEquals(":service:installDist", GradleRuntimePreparer.replacementTask(":service:run", "installDist"));
        assertEquals(":service:bootJar", GradleRuntimePreparer.replacementTask(":service:bootRun", "bootJar"));
        assertEquals("bootJar", GradleRuntimePreparer.replacementTask("bootRun", "bootJar"));
    }
}
