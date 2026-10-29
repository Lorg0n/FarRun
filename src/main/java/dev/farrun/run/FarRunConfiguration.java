package dev.farrun.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A persisted placeholder for a future FarRun remote launch. */
public final class FarRunConfiguration extends RunConfigurationBase<FarRunConfigurationOptions> {

    public static final String NOT_IMPLEMENTED_MESSAGE =
            "FarRun remote execution is not implemented yet.";

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
    public void checkConfiguration() throws RuntimeConfigurationError {
        throw new RuntimeConfigurationError(NOT_IMPLEMENTED_MESSAGE);
    }

    @Override
    public @Nullable RunProfileState getState(
            @NotNull Executor executor,
            @NotNull ExecutionEnvironment environment
    ) throws ExecutionException {
        throw new ExecutionException(NOT_IMPLEMENTED_MESSAGE);
    }
}
