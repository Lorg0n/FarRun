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
import dev.farrun.run.execution.FarRunRunState;
import dev.farrun.run.source.SourceConfigurationLinkUpdater;
import dev.farrun.run.source.SourceConfigurationReference;
import dev.farrun.server.Server;
import dev.farrun.server.ServerStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A persisted FarRun configuration with source-specific execution strategies. */
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

    public @Nullable String getServerId() {
        return getOptions().getServerId();
    }

    public @Nullable Server getServer() {
        String serverId = getServerId();
        return serverId == null ? null : ServerStore.getInstance().find(serverId);
    }

    public void setServer(@Nullable Server server) {
        getOptions().setServerId(server == null ? null : server.id());
    }

    public @NotNull List<RuntimeFileMapping> getRuntimeFiles() {
        List<RuntimeFileMapping> result = new ArrayList<>();
        for (String line : getOptions().getRuntimeFiles().split("\\n")) {
            if (line.isBlank()) {
                continue;
            }
            int separator = line.indexOf('\t');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalStateException("Stored FarRun runtime file mapping is invalid.");
            }
            result.add(new RuntimeFileMapping(line.substring(0, separator), line.substring(separator + 1)));
        }
        return List.copyOf(result);
    }

    public void setRuntimeFiles(@NotNull List<RuntimeFileMapping> mappings) {
        getOptions().setRuntimeFiles(mappings.stream()
                .map(mapping -> mapping.alias() + "\t" + mapping.localPath())
                .collect(java.util.stream.Collectors.joining("\n")));
    }

    @Override
    public @NotNull RunProfileState getState(
            @NotNull Executor executor,
            @NotNull ExecutionEnvironment environment
    ) throws ExecutionException {
        if (!DefaultRunExecutor.EXECUTOR_ID.equals(executor.getId())) {
            throw new ExecutionException(DEBUG_NOT_SUPPORTED_MESSAGE);
        }
        SourceConfigurationReference sourceReference = getSourceReference();
        if (sourceReference == null) {
            throw new ExecutionException("FarRun requires a source Run Configuration.");
        }
        return new FarRunRunState(getProject(), sourceReference, getServer(), getRuntimeFiles());
    }
}
