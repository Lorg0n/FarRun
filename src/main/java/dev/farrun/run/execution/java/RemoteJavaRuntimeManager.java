package dev.farrun.run.execution.java;

import dev.farrun.run.execution.lifecycle.ExecutionCancellation;
import dev.farrun.run.execution.remote.PosixCommand;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import dev.farrun.server.connection.RemoteSshSession;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Installs immutable, user-owned Temurin runtimes below ~/.farrun without root access. */
public final class RemoteJavaRuntimeManager {
    private static final String API = "https://api.adoptium.net/v3/assets/latest/";
    private static final Pattern LINK = Pattern.compile("\\\"link\\\"\\s*:\\s*\\\"([^\\\"]+)");
    private static final Pattern CHECKSUM = Pattern.compile("\\\"checksum\\\"\\s*:\\s*\\\"([0-9a-fA-F]{64})");
    private static final Map<String, Object> INSTALL_LOCKS = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public @NotNull String ensure(
            @NotNull RemoteSshSession session,
            int major,
            @NotNull ExecutionCancellation cancellation,
            @NotNull Progress progress
    ) throws Exception {
        cancellation.check();
        TemurinDistribution distribution = detect(session, cancellation);
        String key = major + "-" + distribution.architecture();
        synchronized (INSTALL_LOCKS.computeIfAbsent(key, ignored -> new Object())) {
            String root = remoteHome(session, cancellation) + "/.farrun/runtimes/temurin-"
                    + major + "-" + distribution.architecture();
            String java = root + "/bin/java";
            if (command(session, PosixCommand.quote(java) + " -version", cancellation).exitCode() == 0) {
                progress.message("[FarRun] Using cached Temurin " + major + ".");
                return java;
            }
            progress.message("[FarRun] Downloading Eclipse Temurin " + major + "...");
            Archive archive = download(major, distribution, cancellation);
            String remoteArchive = "/tmp/farrun-temurin-" + major + "-" + System.nanoTime() + ".tar.gz";
            try {
                progress.message("[FarRun] Verifying archive...");
                verify(archive);
                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session.session())) {
                    sftp.put(archive.file(), remoteArchive);
                }
                String staging = "/tmp/farrun-runtime-" + System.nanoTime();
                command(session, "tar -tzf " + PosixCommand.quote(remoteArchive)
                        + " | while IFS= read -r entry; do case \"$entry\" in "
                        + "/*|../*|*/../*|..) exit 2;; esac; done", cancellation).requireSuccess();
                command(session, "mkdir -p " + PosixCommand.quote(staging) + " && tar -xzf "
                        + PosixCommand.quote(remoteArchive) + " -C " + PosixCommand.quote(staging)
                        + " --no-same-owner --no-same-permissions", cancellation).requireSuccess();
                command(session, "mkdir -p " + PosixCommand.quote(root.substring(0, root.lastIndexOf('/')))
                        + " && test \"$(find " + PosixCommand.quote(staging)
                        + " -mindepth 1 -maxdepth 1 -type d | wc -l)\" = 1 && mv "
                        + PosixCommand.quote(staging) + "/* " + PosixCommand.quote(root)
                        + " && test -x " + PosixCommand.quote(java), cancellation).requireSuccess();
                command(session, PosixCommand.quote(java) + " -version", cancellation).requireSuccess();
                progress.message("[FarRun] Java runtime installed successfully.");
                return java;
            } finally {
                Files.deleteIfExists(archive.file());
                try {
                    command(session, "rm -rf -- " + PosixCommand.quote(remoteArchive), cancellation);
                } catch (Exception ignored) {
                    // The SSH session may already be closed because the user stopped the run.
                }
            }
        }
    }

    private static TemurinDistribution detect(RemoteSshSession session, ExecutionCancellation cancellation) throws Exception {
        String machine = command(session, "uname -m", cancellation).requireSuccess().stdout().trim();
        String libc = command(session, "(ldd --version 2>&1 || true)", cancellation).requireSuccess().stdout();
        return TemurinDistribution.from(machine, libc);
    }

    private static String remoteHome(RemoteSshSession session, ExecutionCancellation cancellation) throws Exception {
        return command(session, "printf '%s' \"$HOME\"", cancellation).requireSuccess().stdout().trim();
    }

    private static Archive download(int major, TemurinDistribution distribution, ExecutionCancellation cancellation)
            throws Exception {
        String query = API + major + "/hotspot?architecture=" + distribution.architecture()
                + "&image_type=jdk&os=linux&vendor=eclipse&heap_size=normal&jvm_impl=hotspot&project=jdk";
        HttpRequest request = HttpRequest.newBuilder(URI.create(query)).timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<String> metadata = send(request, HttpResponse.BodyHandlers.ofString(), cancellation);
        if (metadata.statusCode() != 200) {
            throw new IOException("Temurin " + major + " is not available for Linux " + distribution.architecture() + ".");
        }
        String link = first(LINK, metadata.body());
        String checksum = first(CHECKSUM, metadata.body());
        Path file = Files.createTempFile("farrun-temurin-", ".tar.gz");
        try {
            HttpRequest download = HttpRequest.newBuilder(URI.create(link)).timeout(Duration.ofMinutes(10)).GET().build();
            HttpResponse<byte[]> response = send(download, HttpResponse.BodyHandlers.ofByteArray(), cancellation);
            cancellation.check();
            if (response.statusCode() != 200) {
                throw new IOException("Temurin archive download failed (HTTP " + response.statusCode() + ").");
            }
            Files.write(file, response.body());
            return new Archive(file, checksum);
        } catch (Exception failure) {
            Files.deleteIfExists(file);
            throw failure;
        }
    }

    private static void verify(Archive archive) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive.file()));
        String actual = java.util.HexFormat.of().formatHex(bytes);
        if (!actual.equalsIgnoreCase(archive.checksum())) {
            throw new IOException("Downloaded Temurin archive failed SHA-256 verification.");
        }
    }

    private static <T> HttpResponse<T> send(
            HttpRequest request,
            HttpResponse.BodyHandler<T> handler,
            ExecutionCancellation cancellation
    ) throws Exception {
        CompletableFuture<HttpResponse<T>> future = HTTP.sendAsync(request, handler);
        try {
            while (true) {
                cancellation.check();
                try {
                    return future.get(250, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    // Polling keeps Stop responsive while the bounded HTTP request is in flight.
                }
            }
        } catch (Exception failure) {
            future.cancel(true);
            throw failure;
        }
    }

    private static String first(Pattern pattern, String text) throws IOException {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new IOException("Adoptium metadata did not contain a usable archive.");
        }
        return matcher.group(1);
    }

    private static CommandResult command(RemoteSshSession session, String command, ExecutionCancellation cancellation)
            throws Exception {
        var channel = session.session().createExecChannel(command);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        channel.setOut(out);
        channel.setErr(err);
        try {
            channel.open().verify();
            while (channel.getExitStatus() == null) {
                cancellation.check();
                channel.waitFor(EnumSet.of(ClientChannelEvent.EXIT_STATUS, ClientChannelEvent.EXIT_SIGNAL,
                        ClientChannelEvent.CLOSED), 250);
            }
            return new CommandResult(channel.getExitStatus(), out.toString(java.nio.charset.StandardCharsets.UTF_8),
                    err.toString(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            channel.close(true);
        }
    }

    public interface Progress {
        void message(@NotNull String message);
    }

    private record Archive(Path file, String checksum) { }
    private record CommandResult(Integer exitCode, String stdout, String stderr) {
        CommandResult requireSuccess() throws IOException {
            if (exitCode == null || exitCode != 0) {
                throw new IOException(stderr.isBlank() ? "Remote runtime command failed." : stderr.trim());
            }
            return this;
        }
    }
}
