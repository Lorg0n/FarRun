package dev.farrun.run.execution;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import dev.farrun.run.source.SourceConfigurationReference;
import dev.farrun.run.source.SourceConfigurationResolver;
import dev.farrun.server.Server;
import dev.farrun.run.execution.console.MessageProcessHandler;
import dev.farrun.run.execution.strategy.ExecutionStrategy;
import dev.farrun.run.execution.strategy.GradleStrategy;
import dev.farrun.run.execution.strategy.JavaApplicationStrategy;
import dev.farrun.run.execution.remote.RemoteJavaProcessHandler;
import dev.farrun.run.execution.ui.ExecutionSession;
import dev.farrun.run.execution.ui.FarRunConsoleView;
import dev.farrun.run.execution.ui.ExecutionStage;
import dev.farrun.run.execution.ui.ExecutionStatus;
import dev.farrun.run.execution.console.FarRunConsoleBridge;
import org.jetbrains.annotations.NotNull;

/** Selects a source-specific execution strategy while preserving one FarRun user flow. */
public final class FarRunRunState implements RunProfileState {
    private final @NotNull Project project;
    private final @NotNull SourceConfigurationReference sourceReference;
    private final Server server;

    public FarRunRunState(
            @NotNull Project project,
            @NotNull SourceConfigurationReference sourceReference,
            Server server
    ) {
        this.project = project;
        this.sourceReference = sourceReference;
        this.server = server;
    }

    @Override
    public @NotNull ExecutionResult execute(
            @NotNull Executor executor,
            @NotNull ProgramRunner<?> runner
    ) throws ExecutionException {
        ExecutionStrategy strategy = selectStrategy();
        var processHandler = strategy.createProcessHandler();
        ExecutionSession session = new ExecutionSession();
        if (processHandler instanceof RemoteJavaProcessHandler remote) remote.setExecutionSession(session);
        else session.fail(ExecutionStatus.FAILED, ExecutionStage.BUILD, "FarRun could not start this configuration.");
        Disposer.register(project, processHandler::destroyProcess);
        ConsoleView baseConsole = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .getConsole();
        ConsoleView console = new FarRunConsoleView(baseConsole, session, sourceReference.name(),
                server == null ? "No server selected" : server.toString());
        FarRunConsoleBridge.attach(processHandler, baseConsole);
        processHandler.startNotify();
        return new DefaultExecutionResult(console, processHandler);
    }

    private @NotNull ExecutionStrategy selectStrategy() {
        var settings = SourceConfigurationResolver.resolve(project, sourceReference);
        if (settings.isEmpty()) {
            return () -> new MessageProcessHandler(java.util.List.of(
                    "[FarRun] Selected source configuration is no longer available."
            ), 1);
        }
        var source = settings.get().getConfiguration();
        if (source instanceof ApplicationConfiguration application) {
            if (server == null) {
                return () -> new MessageProcessHandler(java.util.List.of(
                        "[FarRun] Select an SSH server before running Java Application."
                ), 1);
            }
            return new JavaApplicationStrategy(project, application, server);
        }
        if (source instanceof org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration) {
            if (server == null) {
                return () -> new MessageProcessHandler(java.util.List.of(
                        "[FarRun] Select an SSH server before running Gradle application."
                ), 1);
            }
            return new GradleStrategy(project,
                    (org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration) source, server);
        }
        return () -> new MessageProcessHandler(java.util.List.of(
                "[FarRun] Unsupported source configuration type."
        ), 1);
    }
}
