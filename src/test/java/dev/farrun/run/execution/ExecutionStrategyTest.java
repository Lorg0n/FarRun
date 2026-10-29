package dev.farrun.run.execution;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import dev.farrun.run.execution.remote.PosixCommand;
import dev.farrun.run.execution.strategy.GradleStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionStrategyTest {

    @Test
    void gradleStrategyIsAnExplicitUnsupportedExecution() throws Exception {
        MessageListener listener = new MessageListener();
        ProcessHandlerTestSupport.attachAndStart(new GradleStrategy().createProcessHandler(), listener);

        assertTrue(listener.terminated.await(1, TimeUnit.SECONDS));
        assertEquals(List.of(
                "[FarRun] Source type: Gradle",
                "[FarRun] Remote Gradle execution is not implemented yet."
        ), listener.messages);
        assertEquals(1, listener.exitCode);
    }

    @Test
    void posixCommandQuotesShellSyntaxAndKeepsArgumentBoundaries() {
        assertEquals("'java' '-Dname=O'\\''Brien' '-cp' '/tmp/a b'", PosixCommand.join(List.of(
                "java", "-Dname=O'Brien", "-cp", "/tmp/a b"
        )));
    }

    private static final class MessageListener extends ProcessAdapter {
        private final List<String> messages = new ArrayList<>();
        private final CountDownLatch terminated = new CountDownLatch(1);
        private volatile int exitCode;

        @Override
        public synchronized void onTextAvailable(ProcessEvent event, Key outputType) {
            messages.add(event.getText().stripTrailing());
        }

        @Override
        public void processTerminated(ProcessEvent event) {
            exitCode = event.getExitCode();
            terminated.countDown();
        }
    }

    private static final class ProcessHandlerTestSupport {
        private static void attachAndStart(
                com.intellij.execution.process.ProcessHandler handler,
                MessageListener listener
        ) {
            handler.addProcessListener(listener);
            handler.startNotify();
        }
    }
}
