package dev.farrun.run.execution.java;

import org.jetbrains.annotations.NotNull;

/** Pure Adoptium platform mapping, kept separate so it can be tested without a server. */
public record TemurinDistribution(@NotNull String architecture, @NotNull String libc) {
    public static @NotNull TemurinDistribution from(@NotNull String machine, @NotNull String libc) {
        String architecture = switch (machine.toLowerCase(java.util.Locale.ROOT)) {
            case "x86_64", "amd64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new IllegalArgumentException("Unsupported Linux architecture: " + machine);
        };
        String normalizedLibc = libc.toLowerCase(java.util.Locale.ROOT).contains("musl") ? "musl" : "glibc";
        if (!normalizedLibc.equals("glibc")) {
            throw new IllegalArgumentException("Eclipse Temurin Linux " + architecture
                    + " runtime requires glibc on this server; musl is not supported yet.");
        }
        return new TemurinDistribution(architecture, normalizedLibc);
    }
}
