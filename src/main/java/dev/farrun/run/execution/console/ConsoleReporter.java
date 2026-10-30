package dev.farrun.run.execution.console;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Formats FarRun lifecycle messages consistently for the standard IDE console. */
public final class ConsoleReporter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final @NotNull BiConsumer<String, Key<?>> output;
    private final @NotNull Clock clock;

    public ConsoleReporter(@NotNull BiConsumer<String, Key<?>> output) {
        this(output, Clock.systemDefaultZone());
    }

    public ConsoleReporter(@NotNull BiConsumer<String, Key<?>> output, @NotNull Clock clock) {
        this.output = Objects.requireNonNull(output);
        this.clock = Objects.requireNonNull(clock);
    }

    public void info(@NotNull String message) { far("FAR", message, FarRunOutputTypes.INFO); }
    public void start(@NotNull String message) { far("FAR ▶", message, FarRunOutputTypes.ACTIVE); }
    public void success(@NotNull String message) { far("FAR ✓", message, FarRunOutputTypes.SUCCESS); }
    public void progress(@NotNull String message) { far("FAR ↑", message, FarRunOutputTypes.ACTIVE); }
    public void warning(@NotNull String message) { far("FAR !", message, FarRunOutputTypes.WARNING); }
    public void error(@NotNull String message) { far("ERR ✗", message, ProcessOutputTypes.STDERR); }
    public void command(@NotNull String command) { far("FAR $", command, FarRunOutputTypes.COMMAND); }

    public long started() { return System.nanoTime(); }
    public void success(@NotNull String message, long startedNanos) {
        success(message + " — " + duration(System.nanoTime() - startedNanos));
    }

    public void application(@NotNull String line, boolean stderr) {
        String prefix = "[" + LocalTime.now(clock).format(TIME) + "] [" + (stderr ? "ERR" : "APP") + "] ";
        output.accept(prefix + line + System.lineSeparator(), stderr ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT);
    }

    private void far(String label, String message, Key<?> type) {
        String text = "[" + LocalTime.now(clock).format(TIME) + "] [" + label + "] " + message;
        output.accept(text + System.lineSeparator(), type);
    }

    public static @NotNull String duration(long nanos) {
        double seconds = Duration.ofNanos(Math.max(0, nanos)).toMillis() / 1000.0;
        return String.format(java.util.Locale.ROOT, "%.1f s", seconds);
    }
}
