package dev.farrun.run.execution.strategy;

import com.intellij.execution.process.ProcessHandler;
import dev.farrun.run.execution.console.MessageProcessHandler;
import org.jetbrains.annotations.NotNull;

/** Explicit placeholder for Gradle sources until remote Gradle execution is designed. */
public final class GradleStrategy implements ExecutionStrategy {
    @Override
    public @NotNull ProcessHandler createProcessHandler() {
        return new MessageProcessHandler(java.util.List.of(
                "[FarRun] Source type: Gradle",
                "[FarRun] Remote Gradle execution is not implemented yet."
        ), 1);
    }
}
