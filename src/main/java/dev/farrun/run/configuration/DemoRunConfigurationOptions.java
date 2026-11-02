package dev.farrun.run.configuration;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredPropertyBase;
import org.jetbrains.annotations.NotNull;
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
    private final StoredPropertyBase<String> serverId = string(null);
    private final StoredPropertyBase<String> runtimeFiles = string("");

    public DemoRunConfigurationOptions() {
        sourceConfigurationId.setName("sourceConfigurationId");
        sourceConfigurationTypeId.setName("sourceConfigurationTypeId");
        sourceConfigurationName.setName("sourceConfigurationName");
        serverId.setName("serverId");
        runtimeFiles.setName("runtimeFiles");
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

    public @Nullable String getServerId() {
        return serverId.getValue(this);
    }

    public void setServerId(@Nullable String value) {
        serverId.setValue(this, value);
    }

    public @NotNull String getRuntimeFiles() {
        String value = runtimeFiles.getValue(this);
        return value == null ? "" : value;
    }

    public void setRuntimeFiles(@NotNull String value) {
        runtimeFiles.setValue(this, value);
    }
}
