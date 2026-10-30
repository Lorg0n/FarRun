package dev.farrun.run.execution.console;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Turns fragmented SSH byte chunks into complete console lines. */
public final class LineBuffer {
    private final StringBuilder pending = new StringBuilder();
    private final @NotNull Consumer<String> lines;
    private boolean previousCarriageReturn;

    public LineBuffer(@NotNull Consumer<String> lines) { this.lines = lines; }

    public synchronized void append(byte @NotNull [] bytes, int offset, int length) {
        append(new String(bytes, offset, length, StandardCharsets.UTF_8));
    }

    public synchronized void append(@NotNull String text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\n') {
                if (!previousCarriageReturn) emit();
                previousCarriageReturn = false;
            } else if (character == '\r') {
                emit();
                previousCarriageReturn = true;
            } else {
                previousCarriageReturn = false;
                pending.append(character);
            }
        }
    }

    public synchronized void flush() {
        if (!pending.isEmpty()) emit();
        previousCarriageReturn = false;
    }

    private void emit() { lines.accept(pending.toString()); pending.setLength(0); }
}
