package dev.farrun.run;

import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the FarRun entry in the Run/Debug Configurations dialog.
 */
public final class FarRunConfigurationType extends ConfigurationTypeBase {

    /**
     * This identifier is persisted in workspace configuration files. Do not change it.
     */
    public static final String ID = "FarRunRunConfiguration";

    public FarRunConfigurationType() {
        super(ID, "FarRun", "Runs an application through FarRun", AllIcons.RunConfigurations.Application);
        addFactory(new FarRunConfigurationFactory(this));
    }

    public static @NotNull FarRunConfigurationType getInstance() {
        return CONFIGURATION_TYPE_EP.findExtensionOrFail(FarRunConfigurationType.class);
    }
}
