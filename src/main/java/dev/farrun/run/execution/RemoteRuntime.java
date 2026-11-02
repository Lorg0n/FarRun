package dev.farrun.run.execution;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Immutable local runtime material that can be staged and launched on a Linux SSH target. */
public interface RemoteRuntime {
    int requiredJavaMajor();

    @NotNull List<String> programArguments();
}
