package dev.farrun.run.execution.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionSessionTest {
    @Test
    void keepsStagesAndTerminalFailureScopedToOneSession() {
        ExecutionSession first = new ExecutionSession();
        ExecutionSession second = new ExecutionSession();
        List<ExecutionStatus> updates = new ArrayList<>();
        first.addListener(snapshot -> updates.add(snapshot.status()));

        first.update(ExecutionStatus.BUILDING, ExecutionStage.BUILD, StageStatus.IN_PROGRESS);
        first.update(ExecutionStatus.CONNECTING, ExecutionStage.BUILD, StageStatus.SUCCESS);
        first.update(ExecutionStatus.CONNECTING, ExecutionStage.SSH, StageStatus.IN_PROGRESS);
        first.fail(ExecutionStatus.CONNECTION_LOST, ExecutionStage.SSH, "process status unknown");

        assertEquals(StageStatus.SUCCESS, first.snapshot().stages().get(ExecutionStage.BUILD));
        assertEquals(StageStatus.FAILED, first.snapshot().stages().get(ExecutionStage.SSH));
        assertEquals(ExecutionStatus.CONNECTION_LOST, first.snapshot().status());
        assertEquals(ExecutionStatus.PREPARING, second.snapshot().status());
        assertTrue(updates.contains(ExecutionStatus.CONNECTION_LOST));
    }

    @Test
    void exposesConfirmedRunningAndUserStopAsDistinctStates() {
        ExecutionSession session = new ExecutionSession();
        session.update(ExecutionStatus.RUNNING, ExecutionStage.JVM, StageStatus.SUCCESS);
        session.update(ExecutionStatus.RUNNING, ExecutionStage.RUNNING, StageStatus.IN_PROGRESS);
        assertEquals(ExecutionStatus.RUNNING, session.snapshot().status());
        session.update(ExecutionStatus.STOPPED, ExecutionStage.RUNNING, StageStatus.CANCELLED);
        assertEquals(StageStatus.CANCELLED, session.snapshot().stages().get(ExecutionStage.RUNNING));
        assertEquals(ExecutionStatus.STOPPED, session.snapshot().status());
    }
}
