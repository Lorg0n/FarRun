package dev.farrun.run;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JPanel;

/** The native Run Configuration editor for FarRun's currently unavailable settings. */
public final class FarRunSettingsEditor extends SettingsEditor<FarRunConfiguration> {

    private static final String INFORMATION_MESSAGE =
            "Remote execution is not implemented yet. This configuration is currently a placeholder.";

    @Override
    protected void resetEditorFrom(@NotNull FarRunConfiguration configuration) {
        // All fields are informational placeholders during the initial integration.
    }

    @Override
    protected void applyEditorTo(@NotNull FarRunConfiguration configuration) {
        // Deliberately persist no placeholder values.
    }

    @Override
    protected @NotNull JComponent createEditor() {
        JBTextField sourceConfiguration = disabledField("Select Java run configuration...");
        JBTextField sshServer = disabledField("Not configured yet");
        JBTextField remoteWorkingDirectory = disabledField("/opt/farrun/");

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Source configuration", sourceConfiguration)
                .addLabeledComponent("SSH server", sshServer)
                .addLabeledComponent("Remote working directory", remoteWorkingDirectory)
                .addComponentFillVertically(new JBLabel(INFORMATION_MESSAGE), 0)
                .getPanel();
        return panel;
    }

    private static @NotNull JBTextField disabledField(@NotNull String placeholder) {
        JBTextField field = new JBTextField();
        field.getEmptyText().setText(placeholder);
        field.setEnabled(false);
        return field;
    }
}
