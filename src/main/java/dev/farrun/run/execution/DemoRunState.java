package dev.farrun.run.execution;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

/** Creates the standard Run tool-window console for one FarRun demo execution. */
public final class DemoRunState implements RunProfileState {

    private final @NotNull Project project;

    public DemoRunState(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public @NotNull ExecutionResult execute(
            @NotNull Executor executor,
            @NotNull ProgramRunner<?> runner
    ) throws ExecutionException {
        DemoProcessHandler processHandler = new DemoProcessHandler();
        Disposer.register(project, processHandler::destroyProcess);
        ConsoleView console = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .getConsole();
        console.attachToProcess(processHandler);
        processHandler.startNotify();
        return new DefaultExecutionResult(console, processHandler);
    }
}
