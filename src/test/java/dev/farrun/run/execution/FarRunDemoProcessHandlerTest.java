package dev.farrun.run.execution;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.util.Key;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarRunDemoProcessHandlerTest {

    @Test
    void emitsMessagesInOrderAndTerminatesSuccessfully() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            FarRunDemoProcessHandler handler = new FarRunDemoProcessHandler(
                    FarRunDemoProcessHandler.DEMO_MESSAGES, 1, scheduler);
            RecordingListener listener = new RecordingListener();
            handler.addProcessListener(listener);

            handler.startNotify();

            assertTrue(listener.awaitTermination());
            assertEquals(FarRunDemoProcessHandler.DEMO_MESSAGES, listener.messages());
            assertEquals(0, listener.exitCode());
            assertEquals(1, listener.terminationCount());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void stopTerminatesOnceAndPreventsFurtherOutput() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            FarRunDemoProcessHandler handler = new FarRunDemoProcessHandler(
                    FarRunDemoProcessHandler.DEMO_MESSAGES, 1_000, scheduler);
            RecordingListener listener = new RecordingListener();
            handler.addProcessListener(listener);

            handler.startNotify();
            handler.destroyProcess();

            assertTrue(listener.awaitTermination());
            int outputAtTermination = listener.messages().size();
            CountDownLatch schedulerAdvanced = new CountDownLatch(1);
            scheduler.schedule(schedulerAdvanced::countDown, 25, TimeUnit.MILLISECONDS);
            assertTrue(schedulerAdvanced.await(1, TimeUnit.SECONDS));
            handler.destroyProcess();

            assertEquals(130, listener.exitCode());
            assertEquals(1, listener.terminationCount());
            assertEquals(outputAtTermination, listener.messages().size());
            assertFalse(handler.isProcessTerminating());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void independentHandlersEachStartFromTheFirstMessage() throws Exception {
        ScheduledExecutorService firstScheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService secondScheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            FarRunDemoProcessHandler first = new FarRunDemoProcessHandler(
                    FarRunDemoProcessHandler.DEMO_MESSAGES, 1, firstScheduler);
            FarRunDemoProcessHandler second = new FarRunDemoProcessHandler(
                    FarRunDemoProcessHandler.DEMO_MESSAGES, 1, secondScheduler);
            RecordingListener firstListener = new RecordingListener();
            RecordingListener secondListener = new RecordingListener();
            first.addProcessListener(firstListener);
            second.addProcessListener(secondListener);

            first.startNotify();
            second.startNotify();

            assertTrue(firstListener.awaitTermination());
            assertTrue(secondListener.awaitTermination());
            assertEquals(FarRunDemoProcessHandler.DEMO_MESSAGES, firstListener.messages());
            assertEquals(FarRunDemoProcessHandler.DEMO_MESSAGES, secondListener.messages());
        } finally {
            firstScheduler.shutdownNow();
            secondScheduler.shutdownNow();
        }
    }

    private static final class RecordingListener extends ProcessAdapter {
        private final List<String> messages = new ArrayList<>();
        private final CountDownLatch terminated = new CountDownLatch(1);
        private final AtomicInteger terminationCount = new AtomicInteger();
        private volatile int exitCode = Integer.MIN_VALUE;

        @Override
        public synchronized void onTextAvailable(ProcessEvent event, Key outputType) {
            messages.add(event.getText().stripTrailing());
        }

        @Override
        public void processTerminated(ProcessEvent event) {
            exitCode = event.getExitCode();
            terminationCount.incrementAndGet();
            terminated.countDown();
        }

        synchronized List<String> messages() {
            return List.copyOf(messages);
        }

        boolean awaitTermination() throws InterruptedException {
            return terminated.await(2, TimeUnit.SECONDS);
        }

        int exitCode() {
            return exitCode;
        }

        int terminationCount() {
            return terminationCount.get();
        }
    }
}
