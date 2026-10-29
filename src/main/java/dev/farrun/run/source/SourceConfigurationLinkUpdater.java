package dev.farrun.run.source;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import dev.farrun.run.configuration.DemoRunConfiguration;
import org.jetbrains.annotations.NotNull;

/** Keeps persisted source links current when IntelliJ renames their source settings. */
@Service(Service.Level.PROJECT)
public final class SourceConfigurationLinkUpdater implements Disposable {

    private final @NotNull Project project;
    private final @NotNull MessageBusConnection connection;

    public SourceConfigurationLinkUpdater(@NotNull Project project) {
        this.project = project;
        connection = project.getMessageBus().connect(this);
        connection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
            @Override
            public void runConfigurationChanged(
                    @NotNull RunnerAndConfigurationSettings settings,
                    @NotNull String oldName
            ) {
                updateReferencesAfterRename(settings, oldName);
            }
        });
    }

    public static void getInstance(@NotNull Project project) {
        project.getService(SourceConfigurationLinkUpdater.class);
    }

    @Override
    public void dispose() {
        connection.disconnect();
    }

    private void updateReferencesAfterRename(
            @NotNull RunnerAndConfigurationSettings renamedSettings,
            @NotNull String oldName
    ) {
        SourceConfigurationReference updatedReference = SourceConfigurationResolver.referenceOf(renamedSettings);
        for (RunnerAndConfigurationSettings settings : RunManager.getInstance(project).getAllSettings()) {
            if (!(settings.getConfiguration() instanceof DemoRunConfiguration configuration)) {
                continue;
            }

            SourceConfigurationReference reference = configuration.getSourceReference();
            if (reference == null || reference.settingsId().equals(updatedReference.settingsId())) {
                continue;
            }
            if (reference.typeId().equals(updatedReference.typeId()) && reference.name().equals(oldName)) {
                configuration.setSourceReference(updatedReference);
            }
        }
    }
}
