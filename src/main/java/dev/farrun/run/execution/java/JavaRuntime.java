package dev.farrun.run.execution.java;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/** Immutable runtime inputs extracted from IntelliJ's Java Application configuration. */
public record JavaRuntime(
        @NotNull String mainClass,
        @NotNull List<Path> classpath,
        @NotNull List<String> vmArguments,
        @NotNull List<String> programArguments
) {
    public JavaRuntime {
        classpath = List.copyOf(classpath);
        vmArguments = List.copyOf(vmArguments);
        programArguments = List.copyOf(programArguments);
    }
}
