package dev.farrun.run.execution.java;

import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.util.execution.ParametersListUtil;
import dev.farrun.run.execution.lifecycle.ExecutionCancellation;
import dev.farrun.run.execution.console.ConsoleReporter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Compiles the selected module and extracts the runtime classpath through IntelliJ APIs. */
public final class JavaRuntimePreparer {

    private static final long COMPILE_TIMEOUT_MINUTES = 15;

    public @NotNull JavaRuntime prepare(
            @NotNull Project project,
            @NotNull ApplicationConfiguration configuration,
            @NotNull ExecutionCancellation cancellation
    ) throws Exception {
        return prepare(project, configuration, cancellation, null);
    }

    public @NotNull JavaRuntime prepare(
            @NotNull Project project,
            @NotNull ApplicationConfiguration configuration,
            @NotNull ExecutionCancellation cancellation,
            ConsoleReporter reporter
    ) throws Exception {
        String mainClass = onEdt(configuration::getMainClassName);
        if (mainClass == null || mainClass.isBlank()) {
            throw new IllegalArgumentException("The Java Application has no main class.");
        }
        Module module = onEdt(() -> configuration.getConfigurationModule().getModule());
        if (module == null) {
            throw new IllegalArgumentException("The Java Application has no module.");
        }
        long compilation = System.nanoTime();
        if (reporter != null) reporter.start("Compiling local module");
        compile(project, module, mainClass, cancellation);
        if (reporter != null) reporter.success("Local module compiled", compilation);
        int requiredJavaMajor = JavaRuntimeRequirement.resolve(project, module, mainClass);

        JavaRuntime runtime = onEdt(() -> {
            JavaParameters parameters = new JavaParameters();
            parameters.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_PROVIDED);
            JavaParametersUtil.configureConfiguration(parameters, configuration);
            JavaParametersUtil.applyModifications(parameters, configuration.getClasspathModifications());
            parameters.setMainClass(mainClass);

            // A regular Java Application can be marked as module-path by IntelliJ even
            // when its main class is perfectly usable from a conventional classpath.
            // FarRun launches the remote JVM with an explicit main class, so flatten
            // both IntelliJ runtime path collections into the uploaded classpath.
            List<Path> classpath = java.util.stream.Stream.concat(
                            parameters.getClassPath().getPathList().stream(),
                            parameters.getModulePath().getPathList().stream())
                    .map(Path::of)
                    .filter(Files::exists)
                    .distinct()
                    .toList();
            if (classpath.isEmpty()) {
                throw new IllegalStateException("IntelliJ returned an empty Java runtime classpath.");
            }
            return new JavaRuntime(
                    mainClass,
                    classpath,
                    parseArguments(configuration.getVMParameters()),
                    parseArguments(configuration.getProgramParameters()),
                    requiredJavaMajor
            );
        });
        return runtime;
    }

    private static void compile(
            @NotNull Project project,
            @NotNull Module module,
            @NotNull String mainClass,
            @NotNull ExecutionCancellation cancellation
    ) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        boolean[] successful = {false};
        int[] errorCount = {0};
        onEdt(() -> {
            CompilerManager.getInstance(project).make(module, (success, errors, warnings, context) -> {
                successful[0] = success;
                errorCount[0] = errors;
                finished.countDown();
            });
            return null;
        });
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(COMPILE_TIMEOUT_MINUTES);
        while (!finished.await(250, TimeUnit.MILLISECONDS)) {
            cancellation.check();
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Local compilation timed out.");
            }
        }
        cancellation.check();
        if (!successful[0]) {
            if (errorCount[0] == 0 && onEdt(() -> compiledMainClassExists(module, mainClass))) {
                return;
            }
            throw new IllegalStateException("Local compilation failed (" + errorCount[0]
                    + " compiler error(s)). See the Build tool window for details.");
        }
    }

    private static boolean compiledMainClassExists(@NotNull Module module, @NotNull String mainClass) {
        var output = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
        if (output == null) {
            return false;
        }
        String relativeClass = mainClass.replace('.', '/') + ".class";
        return Files.isRegularFile(Path.of(output.getPath(), relativeClass));
    }

    private static @NotNull List<String> parseArguments(String value) {
        return value == null || value.isBlank() ? List.of() : ParametersListUtil.parse(value);
    }

    private static <T> T onEdt(@NotNull java.util.concurrent.Callable<T> action) throws Exception {
        if (ApplicationManager.getApplication().isDispatchThread()) {
            return action.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, ModalityState.defaultModalityState());
        Throwable throwable = failure.get();
        if (throwable != null) {
            if (throwable instanceof Exception exception) {
                throw exception;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(throwable);
        }
        return result.get();
    }
}
