package dev.farrun.run.configuration;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredPropertyBase;
import org.jetbrains.annotations.Nullable;

/**
 * Reserved for real remote-execution settings added in later stages.
 *
 * <p>The initial configuration intentionally has no stored connection values: the
 * editor currently shows placeholders only.</p>
 */
public final class DemoRunConfigurationOptions extends RunConfigurationOptions {

    private final StoredPropertyBase<String> sourceConfigurationId = string(null);
    private final StoredPropertyBase<String> sourceConfigurationTypeId = string(null);
    private final StoredPropertyBase<String> sourceConfigurationName = string(null);

    public DemoRunConfigurationOptions() {
        sourceConfigurationId.setName("sourceConfigurationId");
        sourceConfigurationTypeId.setName("sourceConfigurationTypeId");
        sourceConfigurationName.setName("sourceConfigurationName");
    }

    public @Nullable String getSourceConfigurationId() {
        return sourceConfigurationId.getValue(this);
    }

    public void setSourceConfigurationId(@Nullable String value) {
        sourceConfigurationId.setValue(this, value);
    }

    public @Nullable String getSourceConfigurationTypeId() {
        return sourceConfigurationTypeId.getValue(this);
    }

    public void setSourceConfigurationTypeId(@Nullable String value) {
        sourceConfigurationTypeId.setValue(this, value);
    }

    public @Nullable String getSourceConfigurationName() {
        return sourceConfigurationName.getValue(this);
    }

    public void setSourceConfigurationName(@Nullable String value) {
        sourceConfigurationName.setValue(this, value);
    }
}
