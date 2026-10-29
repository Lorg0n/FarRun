package dev.farrun.run.execution.demo;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A per-run virtual process which demonstrates FarRun's future remote lifecycle.
 * It owns and stops its scheduler, so no execution work outlives this process.
 */
public final class DemoProcessHandler extends ProcessHandler {

    public static final List<String> DEMO_MESSAGES = List.of(
            "[FarRun] Starting remote execution...",
            "[FarRun] Preparing local build...",
            "[FarRun] Local build completed.",
            "[FarRun] Connecting to SSH server...",
            "[FarRun] SSH connection established.",
            "[FarRun] Uploading application files...",
            "[FarRun] Upload completed.",
            "[FarRun] Starting remote JVM...",
            "[FarRun] Application started successfully!",
            "[FarRun] Demo execution completed."
    );

    private static final long DEMO_DELAY_MILLIS = 400;
    private static final int STOP_EXIT_CODE = 130;

    private final @NotNull List<String> messages;
    private final long delayMillis;
    private final @NotNull ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final Object lifecycleLock = new Object();

    public DemoProcessHandler() {
        this(DEMO_MESSAGES, DEMO_DELAY_MILLIS, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "FarRun demo execution");
            thread.setDaemon(true);
            return thread;
        }), true);
    }

    public DemoProcessHandler(@NotNull List<String> messages) {
        this(messages, DEMO_DELAY_MILLIS, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "FarRun demo execution");
            thread.setDaemon(true);
            return thread;
        }), true);
    }

    DemoProcessHandler(
            @NotNull List<String> messages,
            long delayMillis,
            @NotNull ScheduledExecutorService scheduler
    ) {
        this(messages, delayMillis, scheduler, false);
    }

    private DemoProcessHandler(
            @NotNull List<String> messages,
            long delayMillis,
            @NotNull ScheduledExecutorService scheduler,
            boolean ownsScheduler
    ) {
        this.messages = List.copyOf(messages);
        this.delayMillis = delayMillis;
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
    }

    @Override
    public void startNotify() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        super.startNotify();
        scheduleMessage(0);
    }

    @Override
    protected void destroyProcessImpl() {
        finish(STOP_EXIT_CODE);
    }

    @Override
    protected void detachProcessImpl() {
        finish(STOP_EXIT_CODE);
    }

    @Override
    public boolean detachIsDefault() {
        return false;
    }

    @Override
    public @Nullable OutputStream getProcessInput() {
        return null;
    }

    private void scheduleMessage(int messageIndex) {
        if (terminated.get()) {
            return;
        }
        scheduler.schedule(() -> emitMessage(messageIndex), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void emitMessage(int messageIndex) {
        synchronized (lifecycleLock) {
            if (terminated.get()) {
                return;
            }
            notifyTextAvailable(messages.get(messageIndex) + System.lineSeparator(), ProcessOutputTypes.STDOUT);
            if (messageIndex + 1 == messages.size()) {
                finish(0);
            } else {
                scheduleMessage(messageIndex + 1);
            }
        }
    }

    private void finish(int exitCode) {
        synchronized (lifecycleLock) {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            if (ownsScheduler) {
                scheduler.shutdownNow();
            }
            notifyProcessTerminated(exitCode);
        }
    }
}
