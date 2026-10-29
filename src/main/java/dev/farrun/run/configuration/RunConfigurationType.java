package dev.farrun.run.configuration;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the FarRun entry in the Run/Debug Configurations dialog.
 */
public final class RunConfigurationType extends ConfigurationTypeBase {

    /**
     * This identifier is persisted in workspace configuration files. Do not change it.
     */
    public static final String ID = "FarRunRunConfiguration";

    public RunConfigurationType() {
        super(ID, "FarRun", "Runs an application through FarRun", AllIcons.RunConfigurations.Application);
        addFactory(new RunConfigurationFactory(this));
    }

    public static @NotNull RunConfigurationType getInstance() {
        return CONFIGURATION_TYPE_EP.findExtensionOrFail(RunConfigurationType.class);
    }
}
