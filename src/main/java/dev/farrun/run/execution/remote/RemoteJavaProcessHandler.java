package dev.farrun.run.execution.remote;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import dev.farrun.server.PasswordSafeSecretStore;
import dev.farrun.server.SecretStore;
import dev.farrun.server.Server;
import dev.farrun.server.connection.RemoteSshSession;
import dev.farrun.run.execution.lifecycle.ExecutionCancellation;
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

    public RemoteJavaProcessHandler(
            @NotNull Project project,
            @NotNull JavaRuntime runtime,
            @NotNull Server server
    ) {
        this(project, ignored -> runtime, server, new PasswordSafeSecretStore());
    }

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
            emit("[FarRun] Preparing local build...");
            runtime = runtimeFactory.create(cancellation);
            cancellation.check();
            emit("[FarRun] Local build completed.");
            emit("[FarRun] Connecting to " + server.username() + "@" + server.host() + ":" + server.port());
            RemoteSshSession session = RemoteSshSession.open(server, secrets);
            ssh = session;
            cancellation.check();
            emit("[FarRun] SSH connection established.");
            emit("[FarRun] Required Java: " + runtime.requiredJavaMajor());
            emit("[FarRun] Checking remote runtime...");
            remoteJava = new RemoteJavaRuntimeManager().ensure(
                    session,
                    runtime.requiredJavaMajor(),
                    cancellation,
                    this::emit
            );

            remoteDirectory = "/tmp/farrun/" + UUID.randomUUID();
            executeSetupCommand(session, "mkdir -p " + PosixCommand.quote(remoteDirectory + "/cp"));
            cancellation.check();
            emit("[FarRun] Uploading runtime classpath...");
            try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session.session())) {
                uploadClasspath(sftp);
            }
            // Some SSH servers enforce a very small MaxSessions value and account for a
            // just-closed SFTP channel briefly. Give the server a bounded release window.
            awaitChannelRelease();
            cancellation.check();
            emit("[FarRun] Starting remote JVM...");
            exitCode = executeJvm(session);
        } catch (com.intellij.openapi.progress.ProcessCanceledException cancelled) {
            exitCode = STOP_EXIT_CODE;
        } catch (Exception failure) {
            if (!cancellation.isCancelled()) {
                emitError("[FarRun] Remote execution failed: " + safeMessage(failure));
            }
            exitCode = cancellation.isCancelled() ? STOP_EXIT_CODE : 1;
        } finally {
            cleanupRemoteDirectory();
            closeActiveResources();
            worker.shutdownNow();
            finish(exitCode);
        }
    }

    private void uploadClasspath(@NotNull SftpClient sftp) throws IOException {
        JavaRuntime currentRuntime = requireRuntime();
        int index = 0;
        for (Path local : currentRuntime.classpath()) {
            cancellation.check();
            if (!Files.exists(local)) {
                throw new IOException("Runtime classpath entry does not exist: " + local.getFileName());
            }
            uploadPath(sftp, local, remoteDirectory + "/cp/" + index + "-" + safeFileName(local.getFileName().toString()));
            index++;
        }
    }

    private void uploadPath(@NotNull SftpClient sftp, @NotNull Path local, @NotNull String remote) throws IOException {
        if (Files.isDirectory(local)) {
            mkdirIfMissing(sftp, remote);
            try (var entries = Files.list(local)) {
                for (Path child : entries.sorted().toList()) {
                    cancellation.check();
                    uploadPath(sftp, child, remote + "/" + safeFileName(child.getFileName().toString()));
                }
            }
        } else if (Files.isRegularFile(local)) {
            sftp.put(local, remote);
        }
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

        ClientChannel remoteChannel = openJvmChannel(session,
                "cd " + PosixCommand.quote(remoteDirectory) + " && exec " + PosixCommand.join(command));
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
                    emitError("[FarRun] SSH connection lost before the remote JVM reported an exit code.");
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
            candidate.setOut(new ConsoleOutput(ProcessOutputTypes.STDOUT));
            candidate.setErr(new ConsoleOutput(ProcessOutputTypes.STDERR));
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

    private void emit(@NotNull String text) {
        if (!terminated.get()) {
            notifyTextAvailable(text + System.lineSeparator(), ProcessOutputTypes.STDOUT);
        }
    }

    private void emitError(@NotNull String text) {
        if (!terminated.get()) {
            notifyTextAvailable(text + System.lineSeparator(), ProcessOutputTypes.STDERR);
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

    private final class ConsoleOutput extends OutputStream {
        private final @NotNull com.intellij.openapi.util.Key<?> type;

        private ConsoleOutput(@NotNull com.intellij.openapi.util.Key<?> type) {
            this.type = type;
        }

        @Override
        public void write(int value) {
            write(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (!terminated.get() && length > 0) {
                notifyTextAvailable(new String(bytes, offset, length, java.nio.charset.StandardCharsets.UTF_8), type);
            }
        }
    }
}
