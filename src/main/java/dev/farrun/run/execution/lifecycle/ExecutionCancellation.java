package dev.farrun.run.execution.lifecycle;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/** Per-run cancellation token shared by compilation, transfer, and remote execution. */
public final class ExecutionCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void check() {
        if (isCancelled()) {
            throw new ProcessCanceledException();
        }
    }
}
