package dev.farrun.run.execution.gradle;

import dev.farrun.run.execution.java.JavaRuntime;
import dev.farrun.run.execution.java.JavaRuntimeRequirement;
import dev.farrun.run.execution.lifecycle.ExecutionCancellation;
import dev.farrun.run.execution.console.ConsoleReporter;
import dev.farrun.run.execution.remote.PosixCommand;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds only the supported Gradle application shapes and returns their local runtime inputs. */
public final class GradleRuntimePreparer {
    private static final Pattern MAIN_CLASS = Pattern.compile("(?:APP_MAINCLASS|MAIN_CLASS)=(?:\\\"|')?([^\\\"'\\s]+)");

    public @NotNull JavaRuntime prepare(
            @NotNull GradleRunConfiguration configuration,
            @NotNull ExecutionCancellation cancellation
    ) throws Exception {
        return prepare(configuration, cancellation, null);
    }

    public @NotNull JavaRuntime prepare(
            @NotNull GradleRunConfiguration configuration,
            @NotNull ExecutionCancellation cancellation,
            ConsoleReporter reporter
    ) throws Exception {
        var settings = configuration.getSettings();
        List<String> tasks = settings.getTaskNames();
        if (tasks.size() != 1) {
            throw unsupported("FarRun supports one application task at a time; received: " + tasks);
        }
        String task = tasks.getFirst();
        String normalized = task.substring(task.lastIndexOf(':') + 1);
        if (!normalized.equals("bootRun") && !normalized.equals("run") && !normalized.equals("bootJar")) {
            throw unsupported("Gradle task '" + task + "' is not an application task. Supported tasks are bootRun, run and bootJar.");
        }
        Path project = Path.of(settings.getExternalProjectPath());
        Path wrapper = project.resolve(System.getProperty("os.name").toLowerCase().contains("win") ? "gradlew.bat" : "gradlew");
        if (!Files.isRegularFile(wrapper)) {
            throw new IOException("Gradle Wrapper was not found in " + project + ". FarRun does not install Gradle remotely.");
        }
        String buildTask = replacementTask(task, normalized.equals("run") ? "installDist" : "bootJar");
        run(wrapper, project, buildTask, cancellation, reporter);
        Path applicationProject = taskProjectDirectory(project, task);
        if (normalized.equals("run")) {
            return applicationRuntime(applicationProject, configuration.getProject());
        }
        Path jar = newest(applicationProject.resolve("build/libs"), ".jar");
        if (jar == null) {
            throw new IOException("Gradle completed but no executable JAR was found in build/libs.");
        }
        String mainClass = manifestMainClass(jar);
        if (mainClass == null) {
            throw new IOException("The Gradle JAR has no Main-Class manifest entry; FarRun cannot safely infer its launch command.");
        }
        return new JavaRuntime(mainClass, List.of(jar), List.of(), List.of(),
                dev.farrun.run.execution.java.JavaRuntimeRequirement.resolve(
                        configuration.getProject(), null, mainClass));
    }

    private static JavaRuntime applicationRuntime(Path project, com.intellij.openapi.project.Project ideaProject)
            throws IOException {
        Path install = newestDirectory(project.resolve("build/install"));
        if (install == null) {
            throw new IOException("Gradle completed but no application distribution was found.");
        }
        Path bin = install.resolve("bin");
        if (!Files.isDirectory(bin)) {
            throw new IOException("Gradle application distribution has no bin directory.");
        }
        Path script;
        try (var scripts = Files.list(bin)) {
            script = scripts.filter(Files::isRegularFile).findFirst().orElse(null);
        }
        if (script == null) {
            throw new IOException("Gradle application distribution has no start script.");
        }
        Matcher matcher = MAIN_CLASS.matcher(Files.readString(script));
        if (!matcher.find()) {
            throw new IOException("Could not determine the Gradle Application main class from its start script.");
        }
        Path lib = install.resolve("lib");
        List<Path> classpath;
        try (var libraries = Files.list(lib)) {
            classpath = libraries.filter(Files::isRegularFile).sorted().toList();
        }
        return new JavaRuntime(matcher.group(1), classpath, List.of(), List.of(),
                JavaRuntimeRequirement.resolve(ideaProject, null, matcher.group(1)));
    }

    private static void run(Path wrapper, Path project, String task, ExecutionCancellation cancellation, ConsoleReporter reporter) throws Exception {
        long started = System.nanoTime();
        if (reporter != null) {
            reporter.start("Building Gradle runtime");
            reporter.command(PosixCommand.join(List.of(wrapper.toString(), task, "--no-daemon")));
        }
        Process process = new ProcessBuilder(wrapper.toString(), task, "--no-daemon")
                .directory(project.toFile())
                .redirectErrorStream(true)
                .start();
        var tail = new java.util.ArrayDeque<String>();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var lines = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                for (String line; (line = lines.readLine()) != null;) {
                    synchronized (tail) { if (tail.size() == 50) tail.removeFirst(); tail.addLast(line); }
                }
            } catch (IOException ignored) { }
        });
        try {
            while (process.isAlive()) {
                cancellation.check();
                process.waitFor(250, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            if (process.exitValue() != 0) {
                if (reporter != null) {
                    reporter.error("Gradle task '" + task + "' failed with exit code " + process.exitValue() + ".");
                    synchronized (tail) { tail.forEach(reporter::error); }
                }
                throw new IOException("Local Gradle task '" + task + "' failed with exit code " + process.exitValue() + ".");
            }
            reader.join(1_000);
            if (reporter != null) reporter.success("Gradle runtime ready", started);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static Path newest(Path directory, String suffix) throws IOException {
        if (!Files.isDirectory(directory)) return null;
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(suffix))
                    .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified())).orElse(null);
        }
    }

    static @NotNull String replacementTask(@NotNull String originalTask, @NotNull String replacement) {
        int taskNameStart = originalTask.lastIndexOf(':') + 1;
        return originalTask.substring(0, taskNameStart) + replacement;
    }

    private static @NotNull Path taskProjectDirectory(@NotNull Path rootProject, @NotNull String task) {
        int taskNameStart = task.lastIndexOf(':');
        if (taskNameStart <= 0) {
            return rootProject;
        }
        String projectPath = task.substring(0, taskNameStart);
        Path result = rootProject;
        for (String segment : projectPath.split(":")) {
            if (!segment.isEmpty()) {
                result = result.resolve(segment);
            }
        }
        return result;
    }

    private static Path newestDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return null;
        try (var files = Files.list(directory)) {
            return files.filter(Files::isDirectory).findFirst().orElse(null);
        }
    }

    private static String manifestMainClass(Path jar) throws IOException {
        try (var jarFile = new java.util.jar.JarFile(jar.toFile())) {
            var manifest = jarFile.getManifest();
            return manifest == null ? null : manifest.getMainAttributes().getValue("Main-Class");
        }
    }

    private static IllegalArgumentException unsupported(String message) {
        return new IllegalArgumentException(message + " Configure the Gradle run configuration to use a supported task.");
    }
}
