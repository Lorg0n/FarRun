package dev.farrun.run.execution;

import dev.farrun.run.execution.remote.PosixCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionStrategyTest {

    @Test
    void posixCommandQuotesShellSyntaxAndKeepsArgumentBoundaries() {
        assertEquals("'java' '-Dname=O'\\''Brien' '-cp' '/tmp/a b'", PosixCommand.join(List.of(
                "java", "-Dname=O'Brien", "-cp", "/tmp/a b"
        )));
    }

}
