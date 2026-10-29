package dev.farrun.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import dev.farrun.server.PasswordSafeSecretStore;
import dev.farrun.server.ServerStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

/** Application settings entry point for FarRun infrastructure. */
public final class FarRunConfigurable implements SearchableConfigurable {

    private ServersPanel panel;

    @Override
    public @NotNull String getId() {
        return "dev.farrun.settings.servers";
    }

    @Override
    public @NotNull String getDisplayName() {
        return "FarRun";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new ServersPanel(ServerStore.getInstance(), new PasswordSafeSecretStore());
        return panel.component();
    }

    @Override
    public boolean isModified() {
        return panel != null && panel.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        if (panel != null) {
            panel.apply();
        }
    }

    @Override
    public void reset() {
        if (panel != null) {
            panel.reset();
        }
    }

    @Override
    public void disposeUIResources() {
        panel = null;
    }
}
