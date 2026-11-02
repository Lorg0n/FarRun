package dev.farrun.run.execution.java;

import dev.farrun.run.execution.RemoteRuntime;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/** Immutable runtime inputs extracted from IntelliJ's Java Application configuration. */
public record JavaRuntime(
        @NotNull String mainClass,
        @NotNull List<Path> classpath,
        @NotNull List<String> vmArguments,
        @NotNull List<String> programArguments,
        int requiredJavaMajor
) implements RemoteRuntime {
    public JavaRuntime(
            @NotNull String mainClass,
            @NotNull List<Path> classpath,
            @NotNull List<String> vmArguments,
            @NotNull List<String> programArguments
    ) {
        this(mainClass, classpath, vmArguments, programArguments, 17);
    }

    public JavaRuntime {
        classpath = List.copyOf(classpath);
        vmArguments = List.copyOf(vmArguments);
        programArguments = List.copyOf(programArguments);
        if (requiredJavaMajor < 8) {
            throw new IllegalArgumentException("Java runtime version must be at least 8.");
        }
    }
}
