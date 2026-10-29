package dev.farrun.run.configuration;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import dev.farrun.run.source.SourceConfigurationReference;
import dev.farrun.run.source.SourceConfigurationResolver;
import org.jetbrains.annotations.NotNull;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.List;

/** The native Run Configuration editor for FarRun's currently unavailable settings. */
public final class ConfigurationEditor extends SettingsEditor<DemoRunConfiguration> {

    private static final String INFORMATION_MESSAGE =
            "Demo mode: FarRun currently simulates remote execution.";

    private final JComboBox<SourceConfigurationReference> sourceConfiguration = new JComboBox<>();
    private final JBLabel sourceStatus = new JBLabel();

    @Override
    protected void resetEditorFrom(@NotNull DemoRunConfiguration configuration) {
        List<SourceConfigurationReference> available = SourceConfigurationResolver.available(configuration.getProject());
        SourceConfigurationReference selected = configuration.getSourceReference();

        sourceConfiguration.setModel(new DefaultComboBoxModel<>(available.toArray(SourceConfigurationReference[]::new)));
        if (selected != null && !available.contains(selected)) {
            sourceConfiguration.addItem(selected);
        }
        sourceConfiguration.setSelectedItem(selected);
        sourceConfiguration.setEnabled(!available.isEmpty());

        if (available.isEmpty()) {
            sourceStatus.setText("No Java Application or Gradle run configurations are available.");
        } else if (selected != null && !available.contains(selected)) {
            sourceStatus.setText("The selected source configuration is no longer available.");
        } else {
            sourceStatus.setText(" ");
        }
    }

    @Override
    protected void applyEditorTo(@NotNull DemoRunConfiguration configuration) {
        configuration.setSourceReference((SourceConfigurationReference) sourceConfiguration.getSelectedItem());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        JBTextField sshServer = disabledField("Not configured yet");
        JBTextField remoteWorkingDirectory = disabledField("/opt/farrun/");

        sourceConfiguration.setRenderer(new SourceConfigurationRenderer());
        sourceConfiguration.setToolTipText("Select Java Application or Gradle run configuration");

        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Source configuration", sourceConfiguration)
                .addComponent(sourceStatus)
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

    private static final class SourceConfigurationRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SourceConfigurationReference reference) {
                setText(SourceConfigurationResolver.presentableName(reference));
            } else {
                setText("Select Java or Gradle run configuration...");
            }
            return this;
        }
    }
}
