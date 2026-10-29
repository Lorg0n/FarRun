package dev.farrun.run.configuration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** Creates persisted FarRun configurations. */
public final class RunConfigurationFactory extends ConfigurationFactory {

    /**
     * This identifier is persisted with configurations. Do not change it.
     */
    public static final String ID = "FarRunConfigurationFactory";

    RunConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new DemoRunConfiguration(project, this, "FarRun");
    }
}
