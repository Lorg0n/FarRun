package dev.farrun.run.execution.remote;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import dev.farrun.server.PasswordSafeSecretStore;
import dev.farrun.server.SecretStore;
import dev.farrun.server.Server;
import dev.farrun.server.connection.RemoteSshSession;
import dev.farrun.run.execution.lifecycle.ExecutionCancellation;
import dev.farrun.run.execution.console.ConsoleReporter;
import dev.farrun.run.execution.console.LineBuffer;
import dev.farrun.run.execution.ui.ExecutionSession;
import dev.farrun.run.execution.ui.ExecutionStage;
import dev.farrun.run.execution.ui.ExecutionStatus;
import dev.farrun.run.execution.ui.StageStatus;
import dev.farrun.run.execution.java.JavaRuntime;
import dev.farrun.run.execution.java.RemoteJavaRuntimeManager;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** ProcessHandler for one remote JVM and all resources belonging to its launch. */
public class RemoteJavaProcessHandler extends ProcessHandler {
    private static final int STOP_EXIT_CODE = 130;
    private static final int CHANNEL_OPEN_ATTEMPTS = 4;
    private static final long CHANNEL_OPEN_RETRY_MILLIS = 250;

    private final @NotNull Project project;
    private final @NotNull RuntimeFactory runtimeFactory;
    private @Nullable JavaRuntime runtime;
    private final @NotNull Server server;
    private final @NotNull SecretStore secrets;
    private final @NotNull ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "FarRun remote Java execution");
        thread.setDaemon(true);
        return thread;
    });
    private final @NotNull ExecutionCancellation cancellation = new ExecutionCancellation();
    private final @NotNull AtomicBoolean started = new AtomicBoolean();
    private final @NotNull AtomicBoolean terminated = new AtomicBoolean();
    private volatile @Nullable RemoteSshSession ssh;
    private volatile @Nullable ClientChannel channel;
    private volatile @Nullable String remoteDirectory;
    private volatile @Nullable String remoteJava;
    private volatile @Nullable ExecutionSession executionSession;
    private final @NotNull ConsoleReporter reporter = new ConsoleReporter((text, type) -> {
        if (!terminated.get()) notifyTextAvailable(text, type);
    });

    public RemoteJavaProcessHandler(
            @NotNull Project project,
            @NotNull JavaRuntime runtime,
            @NotNull Server server
    ) {
        this(project, ignored -> runtime, server, new PasswordSafeSecretStore());
    }

    public void setExecutionSession(@NotNull ExecutionSession session) { executionSession = session; }

    public RemoteJavaProcessHandler(
            @NotNull Project project,
            @NotNull RuntimeFactory runtimeFactory,
            @NotNull Server server,
            @NotNull SecretStore secrets
    ) {
        this.project = project;
        this.runtimeFactory = runtimeFactory;
        this.server = server;
        this.secrets = secrets;
    }

    @Override
    public void startNotify() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        super.startNotify();
        worker.execute(this::runRemoteProcess);
    }

    @Override
    protected void destroyProcessImpl() {
        cancellation.cancel();
        state(ExecutionStatus.STOPPED, activeStage(), StageStatus.CANCELLED);
        closeActiveResources();
        finish(STOP_EXIT_CODE);
    }

    @Override
    protected void detachProcessImpl() {
        destroyProcessImpl();
    }

    @Override
    public boolean detachIsDefault() {
        return false;
    }

    @Override
    public @Nullable OutputStream getProcessInput() {
        return null;
    }

    private void runRemoteProcess() {
        int exitCode = 1;
        try {
            reporter.start("Remote execution");
            state(ExecutionStatus.BUILDING, ExecutionStage.BUILD, StageStatus.IN_PROGRESS);
            reporter.info("Target: " + server.username() + "@" + server.host() + ":" + server.port());
            long localPreparation = reporter.started();
            reporter.start("Preparing local runtime");
            runtime = runtimeFactory instanceof ReportingRuntimeFactory reporting
                    ? reporting.create(cancellation, reporter) : runtimeFactory.create(cancellation);
            cancellation.check();
            reporter.success("Local runtime ready", localPreparation);
            state(ExecutionStatus.PREPARING, ExecutionStage.BUILD, StageStatus.SUCCESS);
            long connecting = reporter.started();
            reporter.start("Connecting SSH");
            state(ExecutionStatus.CONNECTING, ExecutionStage.SSH, StageStatus.IN_PROGRESS);
            RemoteSshSession session = RemoteSshSession.open(server, secrets);
            ssh = session;
            cancellation.check();
            reporter.success("SSH connected", connecting);
            state(ExecutionStatus.PREPARING, ExecutionStage.SSH, StageStatus.SUCCESS);
            reporter.info("Required Java: " + runtime.requiredJavaMajor());
            javaVersion(runtime.requiredJavaMajor());
            long javaPreparation = reporter.started();
            reporter.start("Preparing remote Java");
            state(ExecutionStatus.INSTALLING_RUNTIME, ExecutionStage.RUNTIME, StageStatus.IN_PROGRESS);
            remoteJava = new RemoteJavaRuntimeManager().ensure(
                    session,
                    runtime.requiredJavaMajor(),
                    cancellation,
                    message -> reporter.info(message.replace("[FarRun] ", ""))
            );
            reporter.success("Remote Java ready", javaPreparation);
            state(ExecutionStatus.PREPARING, ExecutionStage.RUNTIME, StageStatus.SUCCESS);

            remoteDirectory = "/tmp/farrun/" + UUID.randomUUID();
            executeSetupCommand(session, "mkdir -p " + PosixCommand.quote(remoteDirectory + "/cp"));
            cancellation.check();
            long uploading = reporter.started();
            reporter.start("Preparing upload manifest");
            state(ExecutionStatus.UPLOADING, ExecutionStage.UPLOAD, StageStatus.IN_PROGRESS);
            try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session.session())) {
                UploadSummary uploaded = uploadClasspath(sftp);
                reporter.success("Runtime uploaded — " + uploaded.files() + " files, " + formatBytes(uploaded.bytes()), uploading);
                state(ExecutionStatus.PREPARING, ExecutionStage.UPLOAD, StageStatus.SUCCESS);
            }
            // Some SSH servers enforce a very small MaxSessions value and account for a
            // just-closed SFTP channel briefly. Give the server a bounded release window.
            awaitChannelRelease();
            cancellation.check();
            reporter.start("Starting remote JVM");
            state(ExecutionStatus.STARTING, ExecutionStage.JVM, StageStatus.IN_PROGRESS);
            exitCode = executeJvm(session);
            if (exitCode == 0) { reporter.success("Remote JVM finished"); state(ExecutionStatus.COMPLETED, ExecutionStage.RUNNING, StageStatus.SUCCESS); }
            else if (exitCode != STOP_EXIT_CODE && currentStatus() != ExecutionStatus.CONNECTION_LOST) {
                reporter.error("Remote JVM exited with code " + exitCode); fail(ExecutionStatus.FAILED, "Remote JVM exited with code " + exitCode);
            }
        } catch (com.intellij.openapi.progress.ProcessCanceledException cancelled) {
            exitCode = STOP_EXIT_CODE;
            reporter.warning("Remote execution cancelled");
            state(ExecutionStatus.STOPPED, activeStage(), StageStatus.CANCELLED);
        } catch (Exception failure) {
            if (!cancellation.isCancelled()) {
                reporter.error("Remote execution failed: " + safeMessage(failure));
            }
            exitCode = cancellation.isCancelled() ? STOP_EXIT_CODE : 1;
            if (cancellation.isCancelled()) state(ExecutionStatus.STOPPED, activeStage(), StageStatus.CANCELLED);
            else fail(ExecutionStatus.FAILED, safeMessage(failure));
        } finally {
            cleanupRemoteDirectory();
            closeActiveResources();
            worker.shutdownNow();
            finish(exitCode);
        }
    }

    private @NotNull UploadSummary uploadClasspath(@NotNull SftpClient sftp) throws IOException {
        JavaRuntime currentRuntime = requireRuntime();
        List<UploadEntry> manifest = new ArrayList<>();
        for (int index = 0; index < currentRuntime.classpath().size(); index++) {
            Path local = currentRuntime.classpath().get(index);
            cancellation.check();
            if (!Files.exists(local)) {
                throw new IOException("Runtime classpath entry does not exist: " + local.getFileName());
            }
            String target = remoteDirectory + "/cp/" + index + "-" + safeFileName(local.getFileName().toString());
            collectUploadEntries(local, target, manifest);
        }
        long total = manifest.stream().mapToLong(UploadEntry::size).sum();
        reporter.info("Upload manifest: " + manifest.size() + " files, " + formatBytes(total));
        reporter.start("Uploading runtime");
        long uploaded = 0;
        int complete = 0;
        int lastPercent = -1;
        for (UploadEntry entry : manifest) {
            cancellation.check();
            mkdirParents(sftp, entry.remote());
            sftp.put(entry.local(), entry.remote());
            uploaded += entry.size();
            complete++;
            int percent = total == 0 ? 100 : (int) (uploaded * 100 / total);
            if (percent >= lastPercent + 5 || complete == manifest.size()) {
                reporter.progress(complete + "/" + manifest.size() + " files, " + formatBytes(uploaded)
                        + " / " + formatBytes(total) + " (" + percent + "%)");
                lastPercent = percent;
            }
        }
        return new UploadSummary(manifest.size(), total);
    }

    private void collectUploadEntries(Path local, String remote, List<UploadEntry> manifest) throws IOException {
        if (Files.isRegularFile(local)) { manifest.add(new UploadEntry(local, remote, Files.size(local))); return; }
        if (Files.isDirectory(local)) {
            try (var entries = Files.list(local)) {
                for (Path child : entries.sorted(Comparator.comparing(Path::toString)).toList())
                    collectUploadEntries(child, remote + "/" + safeFileName(child.getFileName().toString()), manifest);
            }
        }
    }

    private static void mkdirParents(SftpClient sftp, String remote) {
        String[] parts = remote.substring(1, remote.lastIndexOf('/')).split("/");
        String current = "";
        for (String part : parts) { current += "/" + part; mkdirIfMissing(sftp, current); }
    }

    private int executeJvm(@NotNull RemoteSshSession session) throws IOException {
        JavaRuntime currentRuntime = requireRuntime();
        List<String> command = new java.util.ArrayList<>();
        command.add(requireRemoteJava());
        command.addAll(currentRuntime.vmArguments());
        command.add("-cp");
        command.add(remoteClasspath());
        command.add(currentRuntime.mainClass());
        command.addAll(currentRuntime.programArguments());

        reporter.command(safeCommand(command));

        ClientChannel remoteChannel = openJvmChannel(session,
                "cd " + PosixCommand.quote(remoteDirectory) + " && exec " + PosixCommand.join(command));
        // A command channel opening is the first confirmation that the remote JVM launch was accepted.
        state(ExecutionStatus.RUNNING, ExecutionStage.JVM, StageStatus.SUCCESS);
        state(ExecutionStatus.RUNNING, ExecutionStage.RUNNING, StageStatus.IN_PROGRESS);
        while (true) {
            if (cancellation.isCancelled()) {
                remoteChannel.close(true);
                return STOP_EXIT_CODE;
            }
            Set<ClientChannelEvent> events = remoteChannel.waitFor(
                    EnumSet.of(ClientChannelEvent.EXIT_STATUS, ClientChannelEvent.EXIT_SIGNAL, ClientChannelEvent.CLOSED),
                    250
            );
            if (events.contains(ClientChannelEvent.EXIT_STATUS)
                    || events.contains(ClientChannelEvent.EXIT_SIGNAL)
                    || events.contains(ClientChannelEvent.CLOSED)) {
                if (cancellation.isCancelled()) {
                    return STOP_EXIT_CODE;
                }
                Integer status = remoteChannel.getExitStatus();
                if (status == null) {
                    reporter.error("SSH connection lost before the remote JVM reported an exit code.");
                    fail(ExecutionStatus.CONNECTION_LOST, "SSH connection lost; process status unknown");
                    return 255;
                }
                return status;
            }
        }
    }

    private @NotNull ClientChannel openJvmChannel(@NotNull RemoteSshSession session, @NotNull String command)
            throws IOException {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= CHANNEL_OPEN_ATTEMPTS; attempt++) {
            cancellation.check();
            ClientChannel candidate = session.session().createExecChannel(command);
            candidate.setOut(new ConsoleOutput(false));
            candidate.setErr(new ConsoleOutput(true));
            try {
                candidate.open().verify();
                channel = candidate;
                return candidate;
            } catch (IOException failure) {
                lastFailure = failure;
                candidate.close(true);
                if (attempt < CHANNEL_OPEN_ATTEMPTS) {
                    awaitChannelRelease();
                }
            }
        }
        throw new IOException("SSH server rejected the remote command channel after " + CHANNEL_OPEN_ATTEMPTS
                + " attempts. Check the server's MaxSessions and forced-command policy.", lastFailure);
    }

    private void awaitChannelRelease() {
        for (int elapsed = 0; elapsed < CHANNEL_OPEN_RETRY_MILLIS; elapsed += 25) {
            cancellation.check();
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                cancellation.cancel();
                cancellation.check();
            }
        }
    }

    private void executeSetupCommand(@NotNull RemoteSshSession session, @NotNull String command) throws IOException {
        ClientChannel setup = session.session().createExecChannel(command);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        setup.setOut(output);
        setup.setErr(error);
        try {
            setup.open().verify();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            Integer status;
            while ((status = setup.getExitStatus()) == null && System.nanoTime() < deadline) {
                setup.waitFor(EnumSet.of(ClientChannelEvent.EXIT_STATUS, ClientChannelEvent.EXIT_SIGNAL,
                        ClientChannelEvent.CLOSED), 250);
            }
            if (status == null) {
                throw new IOException("Remote setup command timed out.");
            }
            if (status != 0) {
                String diagnostic = error.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
                if (diagnostic.isBlank()) {
                    diagnostic = output.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
                }
                throw new IOException("Remote setup command failed (exit " + status + ")"
                        + (diagnostic.isBlank() ? "." : ": " + diagnostic));
            }
        } finally {
            setup.close(true);
        }
    }

    private @NotNull String remoteClasspath() {
        JavaRuntime currentRuntime = requireRuntime();
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < currentRuntime.classpath().size(); index++) {
            if (index > 0) {
                result.append(':');
            }
            result.append(remoteDirectory).append("/cp/").append(index).append('-')
                    .append(safeFileName(currentRuntime.classpath().get(index).getFileName().toString()));
        }
        return result.toString();
    }

    private void closeActiveResources() {
        ClientChannel activeChannel = channel;
        if (activeChannel != null) {
            try {
                activeChannel.close(true);
            } catch (Exception ignored) {
            }
        }
        RemoteSshSession activeSsh = ssh;
        if (activeSsh != null) {
            activeSsh.close();
        }
    }

    private void cleanupRemoteDirectory() {
        RemoteSshSession activeSsh = ssh;
        String directory = remoteDirectory;
        if (cancellation.isCancelled() || activeSsh == null || directory == null) {
            return;
        }
        try {
            executeSetupCommand(activeSsh, "rm -rf -- " + PosixCommand.quote(directory));
        } catch (Exception ignored) {
            // Cleanup is best effort; the directory is unique to this run and never reused.
        }
    }

    private void finish(int exitCode) {
        if (terminated.compareAndSet(false, true)) {
            notifyProcessTerminated(exitCode);
        }
    }

    private static void mkdirIfMissing(@NotNull SftpClient sftp, @NotNull String path) {
        try {
            sftp.mkdir(path);
        } catch (IOException ignored) {
            // The directory may have been created by a previous recursive step.
        }
    }

    private static @NotNull String safeFileName(@NotNull String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static @NotNull String safeMessage(@NotNull Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private void state(ExecutionStatus status, ExecutionStage stage, StageStatus stageStatus) {
        ExecutionSession session = executionSession;
        if (session != null) session.update(status, stage, stageStatus);
    }
    private void javaVersion(int major) { ExecutionSession session = executionSession; if (session != null) session.javaVersion(major); }
    private void fail(ExecutionStatus status, String reason) { ExecutionSession session = executionSession; if (session != null) session.fail(status, activeStage(), reason); }
    private ExecutionStage activeStage() {
        ExecutionSession session = executionSession;
        if (session == null) return ExecutionStage.BUILD;
        for (ExecutionStage stage : ExecutionStage.values()) if (session.snapshot().stages().get(stage) == StageStatus.IN_PROGRESS) return stage;
        return ExecutionStage.JVM;
    }
    private @Nullable ExecutionStatus currentStatus() {
        ExecutionSession session = executionSession;
        return session == null ? null : session.snapshot().status();
    }

    static @NotNull String safeCommand(@NotNull List<String> arguments) {
        List<String> safe = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (argument.equals("-cp") || argument.equals("-classpath")) { safe.add(argument); if (++i < arguments.size()) safe.add("<uploaded-classpath>"); continue; }
            if (argument.contains("://")) { safe.add(redactUrl(argument)); continue; }
            if (isSensitiveName(argument)) {
                int equals = argument.indexOf('=');
                if (equals >= 0) { safe.add(argument.substring(0, equals + 1) + "<redacted>"); }
                else { safe.add(argument); if (++i < arguments.size()) safe.add("<redacted>"); }
                continue;
            }
            safe.add(redact(argument));
        }
        return PosixCommand.join(safe);
    }

    private static boolean isSensitiveName(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.matches(".*(password|passwd|token|secret|api[_-]?key|credential).*" );
    }

    private static String redact(String value) {
        if (isSensitiveName(value)) {
            int equals = value.indexOf('=');
            return equals >= 0 ? value.substring(0, equals + 1) + "<redacted>" : "<redacted>";
        }
        return redactUrl(value);
    }

    private static String redactUrl(String value) {
        return value.replaceAll("([a-zA-Z][a-zA-Z0-9+.-]*://)([^/@\\s:]+):([^@\\s]+)@", "$1<redacted>@");
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / 1024.0 / 1024.0);
    }

    private record UploadEntry(Path local, String remote, long size) { }
    private record UploadSummary(int files, long bytes) { }

    private @NotNull JavaRuntime requireRuntime() {
        JavaRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            throw new IllegalStateException("Java runtime was not prepared.");
        }
        return currentRuntime;
    }

    private @NotNull String requireRemoteJava() {
        String java = remoteJava;
        if (java == null) {
            throw new IllegalStateException("Remote Java runtime was not prepared.");
        }
        return java;
    }

    @FunctionalInterface
    public interface RuntimeFactory {
        JavaRuntime create(@NotNull ExecutionCancellation cancellation) throws Exception;
    }

    /** Optional richer preparation hook; retained alongside RuntimeFactory for compatibility. */
    @FunctionalInterface
    public interface ReportingRuntimeFactory extends RuntimeFactory {
        JavaRuntime create(@NotNull ExecutionCancellation cancellation, @NotNull ConsoleReporter reporter) throws Exception;

        @Override
        default JavaRuntime create(@NotNull ExecutionCancellation cancellation) throws Exception {
            return create(cancellation, new ConsoleReporter((text, type) -> { }));
        }
    }

    private final class ConsoleOutput extends OutputStream {
        private final @NotNull LineBuffer buffer;

        private ConsoleOutput(boolean stderr) {
            buffer = new LineBuffer(line -> { if (!terminated.get()) reporter.application(line, stderr); });
        }

        @Override
        public void write(int value) {
            write(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (!terminated.get() && length > 0) {
                buffer.append(bytes, offset, length);
            }
        }

        @Override public void close() { buffer.flush(); }
    }
}
