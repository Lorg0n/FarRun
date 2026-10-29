package dev.farrun.run.execution.strategy;

import com.intellij.execution.process.ProcessHandler;

/** One source-configuration-specific remote execution implementation. */
public interface ExecutionStrategy {
    ProcessHandler createProcessHandler();
}
