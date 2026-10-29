package dev.farrun.run.configuration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import dev.farrun.run.execution.DemoRunState;
import dev.farrun.run.source.SourceConfigurationLinkUpdater;
import dev.farrun.run.source.SourceConfigurationReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A persisted FarRun configuration which currently runs a local lifecycle demonstration. */
public final class DemoRunConfiguration extends RunConfigurationBase<DemoRunConfigurationOptions> {

    private static final String DEBUG_NOT_SUPPORTED_MESSAGE = "FarRun debug is not supported yet.";

    DemoRunConfiguration(
            @NotNull Project project,
            @NotNull ConfigurationFactory factory,
            @NotNull String name
    ) {
        super(project, factory, name);
        SourceConfigurationLinkUpdater.getInstance(project);
    }

    @Override
    protected @NotNull DemoRunConfigurationOptions getOptions() {
        return (DemoRunConfigurationOptions) super.getOptions();
    }

    @Override
    protected @NotNull Class<? extends DemoRunConfigurationOptions> getOptionsClass() {
        return DemoRunConfigurationOptions.class;
    }

    @Override
    public @NotNull SettingsEditor<? extends DemoRunConfiguration> getConfigurationEditor() {
        return new ConfigurationEditor();
    }

    public @Nullable SourceConfigurationReference getSourceReference() {
        return SourceConfigurationReference.from(
                getOptions().getSourceConfigurationId(),
                getOptions().getSourceConfigurationTypeId(),
                getOptions().getSourceConfigurationName()
        );
    }

    public void setSourceReference(@Nullable SourceConfigurationReference reference) {
        getOptions().setSourceConfigurationId(reference == null ? null : reference.settingsId());
        getOptions().setSourceConfigurationTypeId(reference == null ? null : reference.typeId());
        getOptions().setSourceConfigurationName(reference == null ? null : reference.name());
    }

    @Override
    public @NotNull RunProfileState getState(
            @NotNull Executor executor,
            @NotNull ExecutionEnvironment environment
    ) throws ExecutionException {
        if (!DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) {
            throw new ExecutionException(DEBUG_NOT_SUPPORTED_MESSAGE);
        }
        return new DemoRunState(getProject(), getSourceReference());
    }
}
