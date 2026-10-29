package dev.farrun.run.execution.strategy;

import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import dev.farrun.server.Server;
import dev.farrun.run.execution.java.JavaRuntimePreparer;
import dev.farrun.run.execution.remote.RemoteJavaProcessHandler;
import org.jetbrains.annotations.NotNull;

/** Real remote strategy for an IntelliJ Java Application configuration. */
public final class JavaApplicationStrategy implements ExecutionStrategy {
    private final @NotNull Project project;
    private final @NotNull ApplicationConfiguration source;
    private final @NotNull Server server;

    public JavaApplicationStrategy(
            @NotNull Project project,
            @NotNull ApplicationConfiguration source,
            @NotNull Server server
    ) {
        this.project = project;
        this.source = source;
        this.server = server;
    }

    @Override
    public @NotNull ProcessHandler createProcessHandler() {
        return new RemoteJavaProcessHandler(
                project,
                cancellation -> new JavaRuntimePreparer().prepare(project, source, cancellation),
                server,
                new dev.farrun.server.PasswordSafeSecretStore()
        );
    }
}
