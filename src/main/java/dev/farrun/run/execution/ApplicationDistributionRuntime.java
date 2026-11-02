package dev.farrun.run.execution;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/** A Gradle Application distribution whose generated Unix script owns its classpath and JVM defaults. */
public record ApplicationDistributionRuntime(
        @NotNull Path distributionDirectory,
        @NotNull String scriptRelativePath,
        @NotNull List<String> programArguments,
        int requiredJavaMajor
) implements RemoteRuntime {
    public ApplicationDistributionRuntime {
        distributionDirectory = distributionDirectory.toAbsolutePath().normalize();
        scriptRelativePath = scriptRelativePath.replace('\\', '/');
        programArguments = List.copyOf(programArguments);
        if (scriptRelativePath.isBlank() || scriptRelativePath.startsWith("/")
                || scriptRelativePath.startsWith("../") || scriptRelativePath.equals("..")) {
            throw new IllegalArgumentException("Application start script must be inside its distribution.");
        }
        if (requiredJavaMajor < 8) {
            throw new IllegalArgumentException("Java runtime version must be at least 8.");
        }
    }
}
