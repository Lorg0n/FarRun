package dev.farrun.run.execution.remote;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Builds a shell-safe POSIX command from already tokenized arguments. */
public final class PosixCommand {
    private PosixCommand() {
    }

    public static @NotNull String join(@NotNull List<String> arguments) {
        return arguments.stream().map(PosixCommand::quote).reduce((left, right) -> left + " " + right).orElse("");
    }

    public static @NotNull String quote(@NotNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
