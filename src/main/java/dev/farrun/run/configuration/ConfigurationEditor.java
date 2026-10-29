package dev.farrun.run.configuration;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
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

/** Native configuration editor focused on selecting existing application and server settings. */
public final class ConfigurationEditor extends SettingsEditor<DemoRunConfiguration> {

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
            sourceStatus.setText("Create a Java Application or Gradle configuration first. It will appear here automatically.");
        } else if (selected != null && !available.contains(selected)) {
            sourceStatus.setText("The previously selected configuration is unavailable. Choose another source configuration.");
        } else if (selected != null) {
            sourceStatus.setText("FarRun reads this configuration's latest settings every time it runs.");
        } else {
            sourceStatus.setText("Choose the application or Gradle task that FarRun should use as its source.");
        }
    }

    @Override
    protected void applyEditorTo(@NotNull DemoRunConfiguration configuration) {
        configuration.setSourceReference((SourceConfigurationReference) sourceConfiguration.getSelectedItem());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        JBTextField remoteServer = disabledField("No server selected");
        JBLabel serverStatus = new JBLabel(
                "Servers are configured globally in Settings | Tools | FarRun. Select one here when available."
        );
        JBLabel demoStatus = new JBLabel(
                "Demo mode only: Run shows source diagnostics and does not connect to a server."
        );

        sourceConfiguration.setRenderer(new SourceConfigurationRenderer());
        sourceConfiguration.setToolTipText("Select an existing Java Application or Gradle configuration");

        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator("Application"))
                .addLabeledComponent("Run configuration", sourceConfiguration)
                .addComponent(sourceStatus, JBUI.scale(8))
                .addComponent(new TitledSeparator("Remote server"), JBUI.scale(16))
                .addLabeledComponent("Server", remoteServer)
                .addComponent(serverStatus, JBUI.scale(8))
                .addComponent(new TitledSeparator("Run behavior"), JBUI.scale(16))
                .addComponent(demoStatus)
                .addComponentFillVertically(new JPanel(), 0)
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
                setText("Select a Java or Gradle configuration...");
            }
            return this;
        }
    }
}
