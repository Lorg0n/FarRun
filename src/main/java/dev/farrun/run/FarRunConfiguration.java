package dev.farrun.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import dev.farrun.run.execution.FarRunRunState;
import org.jetbrains.annotations.NotNull;

/** A persisted FarRun configuration which currently runs a local lifecycle demonstration. */
public final class FarRunConfiguration extends RunConfigurationBase<FarRunConfigurationOptions> {

    private static final String DEBUG_NOT_SUPPORTED_MESSAGE = "FarRun debug is not supported yet.";

    FarRunConfiguration(
            @NotNull Project project,
            @NotNull ConfigurationFactory factory,
            @NotNull String name
    ) {
        super(project, factory, name);
    }

    @Override
    protected @NotNull FarRunConfigurationOptions getOptions() {
        return (FarRunConfigurationOptions) super.getOptions();
    }

    @Override
    protected @NotNull Class<? extends FarRunConfigurationOptions> getOptionsClass() {
        return FarRunConfigurationOptions.class;
    }

    @Override
    public @NotNull SettingsEditor<? extends FarRunConfiguration> getConfigurationEditor() {
        return new FarRunSettingsEditor();
    }

    @Override
    public @NotNull RunProfileState getState(
            @NotNull Executor executor,
            @NotNull ExecutionEnvironment environment
    ) throws ExecutionException {
        if (!DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) {
            throw new ExecutionException(DEBUG_NOT_SUPPORTED_MESSAGE);
        }
        return new FarRunRunState(getProject());
    }
}
