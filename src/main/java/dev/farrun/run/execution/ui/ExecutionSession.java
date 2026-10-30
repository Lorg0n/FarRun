package dev.farrun.run.execution.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Thread-safe execution presentation state; deliberately independent of Swing and SSH. */
public final class ExecutionSession {
    private final EnumMap<ExecutionStage, StageStatus> stages = new EnumMap<>(ExecutionStage.class);
    private final List<Consumer<ExecutionSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final long startedNanos = System.nanoTime();
    private ExecutionStatus status = ExecutionStatus.PREPARING;
    private @Nullable String javaVersion;
    private @Nullable String remotePid;
    private @Nullable String failure;

    public ExecutionSession() { for (ExecutionStage stage : ExecutionStage.values()) stages.put(stage, StageStatus.PENDING); }
    public synchronized void update(ExecutionStatus status, @Nullable ExecutionStage active, @Nullable StageStatus stageStatus) {
        this.status = status;
        if (active != null && stageStatus != null) stages.put(active, stageStatus);
        publish();
    }
    public synchronized void javaVersion(int major) { javaVersion = "Java " + major; publish(); }
    public synchronized void remotePid(@Nullable String value) { remotePid = value; publish(); }
    public synchronized void fail(ExecutionStatus status, @NotNull ExecutionStage stage, @NotNull String reason) {
        this.status = status; stages.put(stage, StageStatus.FAILED); failure = reason; publish();
    }
    public void addListener(@NotNull Consumer<ExecutionSnapshot> listener) { listeners.add(listener); listener.accept(snapshot()); }
    public void removeListener(@NotNull Consumer<ExecutionSnapshot> listener) { listeners.remove(listener); }
    public synchronized ExecutionSnapshot snapshot() {
        return new ExecutionSnapshot(status, Map.copyOf(stages), javaVersion, remotePid, failure,
                Duration.ofNanos(System.nanoTime() - startedNanos));
    }
    private void publish() { ExecutionSnapshot snapshot = snapshot(); listeners.forEach(listener -> listener.accept(snapshot)); }
}
