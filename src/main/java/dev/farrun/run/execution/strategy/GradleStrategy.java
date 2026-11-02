package dev.farrun.run.execution.strategy;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import dev.farrun.run.execution.RuntimeInputs;
import dev.farrun.run.execution.gradle.GradleRuntimePreparer;
import dev.farrun.run.execution.remote.RemoteJavaProcessHandler;
import dev.farrun.server.PasswordSafeSecretStore;
import dev.farrun.server.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

/** Prepares supported Gradle application tasks locally and uses the common remote JVM engine. */
public final class GradleStrategy implements ExecutionStrategy {
    private final Project project;
    private final GradleRunConfiguration source;
    private final Server server;
    private final @NotNull RuntimeInputs runtimeInputs;

    public GradleStrategy(
            @NotNull Project project,
            @NotNull GradleRunConfiguration source,
            @NotNull Server server,
            @NotNull RuntimeInputs runtimeInputs
    ) {
        this.project = project;
        this.source = source;
        this.server = server;
        this.runtimeInputs = runtimeInputs;
    }

    @Override
    public @NotNull ProcessHandler createProcessHandler() {
        return new RemoteJavaProcessHandler(project,
                (RemoteJavaProcessHandler.ReportingRuntimeFactory) (cancellation, reporter) ->
                        new GradleRuntimePreparer().prepare(source, cancellation, reporter),
                server, new PasswordSafeSecretStore(), runtimeInputs);
    }
}
