package dev.farrun.run.execution.strategy;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import dev.farrun.run.execution.gradle.GradleRuntimePreparer;
import dev.farrun.run.execution.remote.RemoteJavaProcessHandler;
import dev.farrun.run.execution.console.MessageProcessHandler;
import dev.farrun.server.PasswordSafeSecretStore;
import dev.farrun.server.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

/** Prepares supported Gradle application tasks locally and uses the common remote JVM engine. */
public final class GradleStrategy implements ExecutionStrategy {
    private final Project project;
    private final GradleRunConfiguration source;
    private final Server server;

    /** Retained only for binary/source compatibility with the early placeholder test fixture. */
    @Deprecated
    public GradleStrategy() {
        project = null;
        source = null;
        server = null;
    }

    public GradleStrategy(@NotNull Project project, @NotNull GradleRunConfiguration source, @NotNull Server server) {
        this.project = project;
        this.source = source;
        this.server = server;
    }

    @Override
    public @NotNull ProcessHandler createProcessHandler() {
        if (project == null) {
            return new MessageProcessHandler(java.util.List.of(
                    "[FarRun] Source type: Gradle",
                    "[FarRun] Remote Gradle execution is not implemented yet."
            ), 1);
        }
        return new RemoteJavaProcessHandler(project,
                cancellation -> new GradleRuntimePreparer().prepare(source, cancellation),
                server, new PasswordSafeSecretStore());
    }
}
