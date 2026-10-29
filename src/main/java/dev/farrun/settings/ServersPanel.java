package dev.farrun.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import dev.farrun.server.AuthenticationMethod;
import dev.farrun.server.SecretStore;
import dev.farrun.server.Server;
import dev.farrun.server.ServerStore;
import dev.farrun.server.ServerValidator;
import dev.farrun.server.connection.ConnectionResult;
import dev.farrun.server.connection.SshConnectionTester;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native two-pane editor for globally saved SSH server definitions. */
final class ServersPanel {

    private final @NotNull ServerStore store;
    private final @NotNull SecretStore secrets;
    private final DefaultListModel<Server> listModel = new DefaultListModel<>();
    private final JBList<Server> serverList = new JBList<>(listModel);
    private final JBTextField displayName = new JBTextField();
    private final JBTextField host = new JBTextField();
    private final JBTextField port = new JBTextField();
    private final JBTextField username = new JBTextField();
    private final JComboBox<AuthenticationMethod> authentication = new JComboBox<>(AuthenticationMethod.values());
    private final JBTextField privateKeyText = new JBTextField();
    private final TextFieldWithBrowseButton privateKey = new TextFieldWithBrowseButton(privateKeyText);
    private final JBPasswordField password = new JBPasswordField();
    private final JBPasswordField keyPassphrase = new JBPasswordField();
    private final JButton clearPassword = new JButton("Clear saved password");
    private final JButton clearPassphrase = new JButton("Clear saved passphrase");
    private final JButton testConnection = new JButton("Test Connection");
    private final JBLabel secretHint = new JBLabel();
    private final JBLabel connectionStatus = new JBLabel();
    private final CardLayout authenticationLayout = new CardLayout();
    private final JPanel authenticationDetails = new JPanel(authenticationLayout);
    private final JPanel editor;
    private final JPanel component;
    private final Map<String, SecretChange> secretChanges = new HashMap<>();
    private final Set<String> deletedIds = new HashSet<>();

    private boolean changingSelection;
    private @Nullable Server selected;

    ServersPanel(@NotNull ServerStore store, @NotNull SecretStore secrets) {
        this.store = store;
        this.secrets = secrets;
        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !changingSelection) {
                saveCurrent();
                show(serverList.getSelectedValue());
            }
        });

        privateKey.addBrowseFolderListener(new TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        ));
        authentication.addActionListener(event -> updateAuthenticationFields());
        clearPassword.addActionListener(event -> clearSecret(SecretKind.PASSWORD));
        clearPassphrase.addActionListener(event -> clearSecret(SecretKind.KEY_PASSPHRASE));
        testConnection.addActionListener(event -> testCurrentServer());

        editor = createEditor();
        component = new JPanel(new BorderLayout());
        component.setBorder(JBUI.Borders.empty(8));
        component.add(createContent(), BorderLayout.CENTER);
        reset();
    }

    @NotNull JComponent component() {
        return component;
    }

    boolean isModified() {
        saveCurrent();
        return !servers().equals(store.servers()) || !secretChanges.isEmpty() || !deletedIds.isEmpty();
    }

    void apply() throws ConfigurationException {
        saveCurrent();
        for (Server server : servers()) {
            String error = ServerValidator.validate(server);
            if (error != null) {
                select(server);
                throw new ConfigurationException(error, "Invalid SSH server");
            }
        }
        List<Server> savedServers = servers();
        store.replace(savedServers);
        Map<String, SecretChange> changes = Map.copyOf(secretChanges);
        Set<String> removed = Set.copyOf(deletedIds);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            removed.forEach(secrets::delete);
            changes.forEach((serverId, change) -> change.apply(serverId, secrets));
        });
        secretChanges.clear();
        deletedIds.clear();
    }

    void reset() {
        changingSelection = true;
        listModel.clear();
        store.servers().forEach(listModel::addElement);
        changingSelection = false;
        secretChanges.clear();
        deletedIds.clear();
        if (!listModel.isEmpty()) {
            serverList.setSelectedIndex(0);
        } else {
            show(null);
        }
    }

    private @NotNull JComponent createContent() {
        ToolbarDecorator toolbar = ToolbarDecorator.createDecorator(serverList)
                .setAddAction(button -> addServer())
                .setEditAction(button -> focusEditor())
                .setRemoveAction(button -> removeSelected())
                .setAddActionName("Add SSH server")
                .setEditActionName("Edit selected server")
                .setRemoveActionName("Remove selected server");
        JPanel listPanel = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        listPanel.add(new JBLabel("SSH servers"), BorderLayout.NORTH);
        listPanel.add(toolbar.createPanel(), BorderLayout.CENTER);

        OnePixelSplitter split = new OnePixelSplitter(false, 0.28f);
        split.setFirstComponent(listPanel);
        split.setSecondComponent(editor);
        split.setDividerWidth(JBUI.scale(1));
        split.setHonorComponentsMinimumSize(false);
        return split;
    }

    private @NotNull JPanel createEditor() {
        displayName.getEmptyText().setText("Production server");
        host.getEmptyText().setText("example.com or 192.0.2.10");
        port.setText("22");
        username.getEmptyText().setText("deploy (or root)");
        authentication.setPreferredSize(new Dimension(
                JBUI.scale(280),
                authentication.getPreferredSize().height
        ));
        privateKeyText.getEmptyText().setText("Path to an OpenSSH private key");
        password.getEmptyText().setText("Leave empty to keep the saved password");
        keyPassphrase.getEmptyText().setText("Leave empty to keep the saved passphrase");
        secretHint.setText("Secrets are stored in the IDE Password Safe, never in FarRun settings.");
        connectionStatus.setVisible(false);

        authenticationDetails.add(createPasswordAuthenticationPanel(), AuthenticationMethod.PASSWORD.name());
        authenticationDetails.add(createKeyAuthenticationPanel(), AuthenticationMethod.PRIVATE_KEY.name());

        JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("Server details"))
                .addLabeledComponent("Display name", displayName)
                .addLabeledComponent("Host", host)
                .addLabeledComponent("SSH port", port)
                .addLabeledComponent("Username", username)
                .addSeparator(8)
                .addLabeledComponent("Authentication", authentication)
                .addComponent(authenticationDetails, JBUI.scale(6))
                .addComponent(secretHint, JBUI.scale(8))
                .addComponent(testConnection, JBUI.scale(12))
                .addComponent(connectionStatus, JBUI.scale(6))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        panel.setBorder(JBUI.Borders.emptyLeft(16));
        return panel;
    }

    private @NotNull JPanel createPasswordAuthenticationPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Password", password)
                .addComponent(clearPassword, JBUI.scale(4))
                .getPanel();
    }

    private @NotNull JPanel createKeyAuthenticationPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Private key", privateKey)
                .addLabeledComponent("Key passphrase", keyPassphrase)
                .addComponent(clearPassphrase, JBUI.scale(4))
                .getPanel();
    }

    private void addServer() {
        saveCurrent();
        Server server = Server.newServer();
        listModel.addElement(server);
        select(server);
        displayName.requestFocusInWindow();
    }

    private void removeSelected() {
        Server server = serverList.getSelectedValue();
        if (server == null) {
            return;
        }
        int answer = Messages.showYesNoDialog(
                component,
                "Remove SSH server '" + server.displayName() + "'? Its saved password and passphrase will also be removed.",
                "Remove SSH server",
                Messages.getWarningIcon()
        );
        if (answer != Messages.YES) {
            return;
        }
        int index = serverList.getSelectedIndex();
        listModel.removeElement(server);
        secretChanges.remove(server.id());
        deletedIds.add(server.id());
        if (!listModel.isEmpty()) {
            serverList.setSelectedIndex(Math.min(index, listModel.size() - 1));
        } else {
            show(null);
        }
    }

    private void focusEditor() {
        displayName.requestFocusInWindow();
    }

    private void clearSecret(@NotNull SecretKind kind) {
        if (selected == null) {
            return;
        }
        SecretChange change = secretChanges.computeIfAbsent(selected.id(), ignored -> new SecretChange());
        if (kind == SecretKind.PASSWORD) {
            change.password = null;
            change.clearPassword = true;
            password.setText("");
        } else {
            change.passphrase = null;
            change.clearPassphrase = true;
            keyPassphrase.setText("");
        }
        updateSecretHint();
    }

    private void saveCurrent() {
        if (selected == null || changingSelection) {
            return;
        }
        int parsedPort = parsePort(port.getText());
        Server updated = new Server(
                selected.id(),
                displayName.getText().trim(),
                host.getText().trim(),
                parsedPort,
                username.getText().trim(),
                (AuthenticationMethod) authentication.getSelectedItem(),
                emptyToNull(privateKey.getText().trim()),
                selected.host().equals(host.getText().trim()) && selected.port() == parsedPort
                        ? selected.trustedHostKey()
                        : null
        );
        replaceInList(updated);
        selected = updated;
        rememberSecret(selected.id(), password.getPassword(), SecretKind.PASSWORD);
        rememberSecret(selected.id(), keyPassphrase.getPassword(), SecretKind.KEY_PASSPHRASE);
    }

    private void rememberSecret(@NotNull String serverId, char @NotNull [] value, @NotNull SecretKind kind) {
        try {
            if (value.length == 0) {
                return;
            }
            SecretChange change = secretChanges.computeIfAbsent(serverId, ignored -> new SecretChange());
            if (kind == SecretKind.PASSWORD) {
                change.password = new String(value);
                change.clearPassword = false;
            } else {
                change.passphrase = new String(value);
                change.clearPassphrase = false;
            }
        } finally {
            Arrays.fill(value, '\0');
        }
    }

    private void show(@Nullable Server server) {
        changingSelection = true;
        selected = server;
        boolean enabled = server != null;
        displayName.setText(enabled ? server.displayName() : "");
        host.setText(enabled ? server.host() : "");
        port.setText(enabled ? String.valueOf(server.port()) : "22");
        username.setText(enabled ? server.username() : "");
        authentication.setSelectedItem(enabled ? server.authenticationMethod() : AuthenticationMethod.PASSWORD);
        privateKey.setText(enabled && server.privateKeyPath() != null ? server.privateKeyPath() : "");
        password.setText("");
        keyPassphrase.setText("");
        setEditorEnabled(enabled);
        updateAuthenticationFields();
        updateSecretHint();
        connectionStatus.setVisible(false);
        changingSelection = false;
    }

    private void setEditorEnabled(boolean enabled) {
        displayName.setEnabled(enabled);
        host.setEnabled(enabled);
        port.setEnabled(enabled);
        username.setEnabled(enabled);
        authentication.setEnabled(enabled);
        privateKey.setEnabled(enabled);
        password.setEnabled(enabled);
        keyPassphrase.setEnabled(enabled);
        clearPassword.setEnabled(enabled);
        clearPassphrase.setEnabled(enabled);
        testConnection.setEnabled(enabled);
    }

    private void updateAuthenticationFields() {
        AuthenticationMethod method = (AuthenticationMethod) authentication.getSelectedItem();
        authenticationLayout.show(authenticationDetails, (method == null ? AuthenticationMethod.PASSWORD : method).name());
        editor.revalidate();
        editor.repaint();
    }

    private void updateSecretHint() {
        if (selected == null) {
            secretHint.setText("Select or add a server to configure secure credentials.");
            return;
        }
        SecretChange change = secretChanges.get(selected.id());
        boolean changed = change != null && (change.password != null || change.passphrase != null
                || change.clearPassword || change.clearPassphrase);
        secretHint.setText(changed
                ? "Credential changes will be saved in the IDE Password Safe when you apply settings."
                : "Secrets are stored in the IDE Password Safe. Leave fields empty to keep an existing secret.");
    }

    private void testCurrentServer() {
        saveCurrent();
        Server server = selected;
        if (server == null) {
            return;
        }
        String validation = ServerValidator.validate(server);
        if (validation != null) {
            showConnectionStatus(validation);
            return;
        }
        testConnection.setEnabled(false);
        connectionStatus.setText("Testing SSH connection…");
        connectionStatus.setVisible(true);
        new Task.Backgroundable(null, "Testing FarRun SSH connection", true) {
            private ConnectionResult result;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Connecting to " + server.host());
                if (indicator.isCanceled()) {
                    result = ConnectionResult.failure(ConnectionResult.Status.CANCELLED, "Connection test cancelled.");
                    return;
                }
                result = new SshConnectionTester(new PendingSecrets(secrets, secretChanges)).test(server, indicator::isCanceled);
                if (indicator.isCanceled()) {
                    result = ConnectionResult.failure(ConnectionResult.Status.CANCELLED, "Connection test cancelled.");
                }
            }

            @Override
            public void onSuccess() {
                testConnection.setEnabled(selected != null);
                handleConnectionResult(server, result);
            }

            @Override
            public void onCancel() {
                testConnection.setEnabled(selected != null);
                showConnectionStatus("Connection test cancelled.");
            }
        }.queue();
    }

    private void handleConnectionResult(@NotNull Server server, @Nullable ConnectionResult result) {
        if (result == null) {
            showConnectionStatus("Unable to complete the SSH connection test.");
            return;
        }
        if (result.status() == ConnectionResult.Status.UNKNOWN_HOST_KEY && result.hostKey() != null) {
            int answer = Messages.showYesNoDialog(
                    component,
                    result.message() + "\n\nTrust this key and retry the connection?",
                    "Trust SSH host key",
                    Messages.getWarningIcon()
            );
            if (answer == Messages.YES) {
                Server trusted = server.withTrustedHostKey(result.hostKey());
                replaceInList(trusted);
                selected = trusted;
                testCurrentServer();
                return;
            }
        }
        showConnectionStatus(result.message());
    }

    private void showConnectionStatus(@NotNull String message) {
        connectionStatus.setText(message);
        connectionStatus.setVisible(true);
    }

    private void replaceInList(@NotNull Server replacement) {
        for (int index = 0; index < listModel.size(); index++) {
            if (listModel.get(index).id().equals(replacement.id())) {
                listModel.set(index, replacement);
                return;
            }
        }
    }

    private void select(@NotNull Server server) {
        for (int index = 0; index < listModel.size(); index++) {
            if (listModel.get(index).id().equals(server.id())) {
                serverList.setSelectedIndex(index);
                return;
            }
        }
    }

    private @NotNull List<Server> servers() {
        List<Server> servers = new ArrayList<>(listModel.size());
        for (int index = 0; index < listModel.size(); index++) {
            servers.add(listModel.get(index));
        }
        return servers;
    }

    private static int parsePort(@NotNull String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static @Nullable String emptyToNull(@NotNull String text) {
        return text.isEmpty() ? null : text;
    }

    private enum SecretKind { PASSWORD, KEY_PASSPHRASE }

    private static final class SecretChange {
        private String password;
        private String passphrase;
        private boolean clearPassword;
        private boolean clearPassphrase;

        void apply(@NotNull String serverId, @NotNull SecretStore secrets) {
            if (clearPassword) {
                secrets.savePassword(serverId, null);
            } else if (password != null) {
                secrets.savePassword(serverId, password);
            }
            if (clearPassphrase) {
                secrets.saveKeyPassphrase(serverId, null);
            } else if (passphrase != null) {
                secrets.saveKeyPassphrase(serverId, passphrase);
            }
        }
    }

    private static final class PendingSecrets implements SecretStore {
        private final SecretStore delegate;
        private final Map<String, SecretChange> changes;

        PendingSecrets(@NotNull SecretStore delegate, @NotNull Map<String, SecretChange> changes) {
            this.delegate = delegate;
            this.changes = Map.copyOf(changes);
        }

        @Override
        public @Nullable String password(@NotNull String serverId) {
            SecretChange change = changes.get(serverId);
            return change == null ? delegate.password(serverId) : change.clearPassword ? null : change.password != null ? change.password : delegate.password(serverId);
        }

        @Override
        public @Nullable String keyPassphrase(@NotNull String serverId) {
            SecretChange change = changes.get(serverId);
            return change == null ? delegate.keyPassphrase(serverId) : change.clearPassphrase ? null : change.passphrase != null ? change.passphrase : delegate.keyPassphrase(serverId);
        }

        @Override public void savePassword(@NotNull String serverId, @Nullable String password) { delegate.savePassword(serverId, password); }
        @Override public void saveKeyPassphrase(@NotNull String serverId, @Nullable String passphrase) { delegate.saveKeyPassphrase(serverId, passphrase); }
        @Override public void delete(@NotNull String serverId) { delegate.delete(serverId); }
    }
}
