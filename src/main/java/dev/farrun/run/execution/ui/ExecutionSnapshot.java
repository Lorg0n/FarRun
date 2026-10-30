package dev.farrun.run.execution.ui;

import org.jetbrains.annotations.Nullable;
import java.time.Duration;
import java.util.Map;

public record ExecutionSnapshot(
        ExecutionStatus status, Map<ExecutionStage, StageStatus> stages, @Nullable String javaVersion,
        @Nullable String remotePid, @Nullable String failure, Duration elapsed
) { }
