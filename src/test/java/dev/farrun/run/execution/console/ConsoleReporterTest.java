package dev.farrun.run.execution.console;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleReporterTest {
    @Test
    void usesSemanticLabelsAndNativeConsoleOutputTypesWithoutAnsiEscapes() {
        List<String> output = new ArrayList<>();
        List<Key<?>> types = new ArrayList<>();
        ConsoleReporter reporter = new ConsoleReporter((text, type) -> { output.add(text); types.add(type); },
                Clock.fixed(Instant.parse("2026-01-01T14:03:12Z"), ZoneId.of("UTC")));

        reporter.start("Preparing local runtime");
        reporter.success("Runtime ready");
        reporter.error("Failed");

        assertTrue(output.get(0).contains("[14:03:12] [FAR ▶] Preparing local runtime"));
        assertTrue(output.get(1).contains("[14:03:12] [FAR ✓] Runtime ready"));
        assertTrue(output.get(2).contains("[14:03:12] [ERR ✗] Failed"));
        assertTrue(output.stream().noneMatch(text -> text.contains("\u001B[")));
        assertEquals(List.of(FarRunOutputTypes.ACTIVE, FarRunOutputTypes.SUCCESS, ProcessOutputTypes.STDERR), types);
    }

    @Test
    void buffersFragmentedLfAndCrlfOutputIntoIndividualLines() {
        List<String> lines = new ArrayList<>();
        LineBuffer buffer = new LineBuffer(lines::add);
        buffer.append("first\r".getBytes(), 0, 6);
        buffer.append("\nsecond".getBytes(), 0, 7);
        buffer.append(" line\nlast".getBytes(), 0, 10);
        buffer.flush();
        assertEquals(List.of("first", "second line", "last"), lines);
    }
}
