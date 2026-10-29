package dev.farrun.run.execution.console;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immediate ProcessHandler for validation errors and intentionally unsupported strategies. */
public final class MessageProcessHandler extends ProcessHandler {
    private final @NotNull List<String> messages;
    private final int exitCode;
    private final @NotNull AtomicBoolean terminated = new AtomicBoolean();

    public MessageProcessHandler(@NotNull List<String> messages, int exitCode) {
        this.messages = List.copyOf(messages);
        this.exitCode = exitCode;
    }

    @Override
    public void startNotify() {
        super.startNotify();
        for (String message : messages) {
            notifyTextAvailable(message + System.lineSeparator(), ProcessOutputTypes.STDOUT);
        }
        finish();
    }

    @Override
    protected void destroyProcessImpl() {
        finish();
    }

    @Override
    protected void detachProcessImpl() {
        finish();
    }

    @Override
    public boolean detachIsDefault() {
        return false;
    }

    @Override
    public @Nullable OutputStream getProcessInput() {
        return null;
    }

    private void finish() {
        if (terminated.compareAndSet(false, true)) {
            notifyProcessTerminated(exitCode);
        }
    }
}
