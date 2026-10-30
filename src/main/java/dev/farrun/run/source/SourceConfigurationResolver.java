package dev.farrun.run.source;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import dev.farrun.run.configuration.DemoRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Resolves supported source settings and exposes only non-sensitive diagnostic fields. */
public final class SourceConfigurationResolver {

    private static final String NOT_SET = "<not set>";
    private static final String NOT_AVAILABLE = "<not available>";

    private SourceConfigurationResolver() {
    }

    public static @NotNull List<SourceConfigurationReference> available(@NotNull Project project) {
        List<SourceConfigurationReference> references = new ArrayList<>();
        for (RunnerAndConfigurationSettings settings : RunManager.getInstance(project).getAllSettings()) {
            if (isSupported(settings.getConfiguration())) {
                references.add(referenceOf(settings));
            }
        }
        return List.copyOf(references);
    }

    public static @NotNull Optional<RunnerAndConfigurationSettings> resolve(
            @NotNull Project project,
            @NotNull SourceConfigurationReference reference
    ) {
        return RunManager.getInstance(project)
                .getAllSettings()
                .stream()
                .filter(settings -> settings.getUniqueID().equals(reference.settingsId()))
                .filter(settings -> settings.getType().getId().equals(reference.typeId()))
                .filter(settings -> isSupported(settings.getConfiguration()))
                .findFirst();
    }

    public static @NotNull SourceConfigurationReference referenceOf(
            @NotNull RunnerAndConfigurationSettings settings
    ) {
        return new SourceConfigurationReference(
                settings.getUniqueID(),
                settings.getType().getId(),
                settings.getName()
        );
    }

    public static @NotNull String presentableName(@NotNull SourceConfigurationReference reference) {
        return reference.name() + " (" + typeLabel(reference.typeId()) + ")";
    }

    public static @NotNull List<String> describe(
            @NotNull Project project,
            @Nullable SourceConfigurationReference reference
    ) {
        if (reference == null) {
            return List.of("[FarRun] Source configuration is not selected.", completionMessage());
        }

        Optional<RunnerAndConfigurationSettings> settings = resolve(project, reference);
        if (settings.isEmpty()) {
            return List.of("[FarRun] Selected source configuration is no longer available.", completionMessage());
        }

        return describe(settings.get());
    }

    private static @NotNull List<String> describe(@NotNull RunnerAndConfigurationSettings settings) {
        RunConfiguration configuration = settings.getConfiguration();
        if (configuration instanceof ApplicationConfiguration application) {
            return describeApplication(settings, application);
        }
        if (configuration instanceof GradleRunConfiguration gradle) {
            return describeGradle(settings, gradle);
        }
        return List.of("[FarRun] Unsupported source configuration.", completionMessage());
    }

    private static @NotNull List<String> describeApplication(
            @NotNull RunnerAndConfigurationSettings settings,
            @NotNull ApplicationConfiguration application
    ) {
        Module module = application.getConfigurationModule().getModule();
        String moduleName = module == null ? NOT_SET : module.getName();

        return List.of(
                "[FarRun] Source: " + settings.getName(),
                "[FarRun] Type: Java Application",
                "[FarRun] Main class: " + valueOrNotSet(application.getMainClassName()),
                "[FarRun] Module: " + moduleName,
                "[FarRun] JVM arguments: " + valueOrNotSet(application.getVMParameters()),
                "[FarRun] Program arguments: " + valueOrNotSet(application.getProgramParameters()),
                completionMessage()
        );
    }

    private static @NotNull List<String> describeGradle(
            @NotNull RunnerAndConfigurationSettings settings,
            @NotNull GradleRunConfiguration gradle
    ) {
        ExternalSystemTaskExecutionSettings taskSettings = gradle.getSettings();
        String tasks = taskSettings.getTaskNames().isEmpty()
                ? NOT_SET
                : String.join(" ", taskSettings.getTaskNames());

        return List.of(
                "[FarRun] Source: " + settings.getName(),
                "[FarRun] Type: Gradle",
                "[FarRun] Gradle project: " + valueOrNotAvailable(taskSettings.getExternalProjectPath()),
                "[FarRun] Tasks: " + tasks,
                "[FarRun] Arguments: " + valueOrNotSet(taskSettings.getScriptParameters()),
                "[FarRun] Supported application tasks: bootRun, bootJar and run.",
                completionMessage()
        );
    }

    private static boolean isSupported(@NotNull RunConfiguration configuration) {
        return !(configuration instanceof DemoRunConfiguration)
                && (configuration instanceof ApplicationConfiguration
                || configuration instanceof GradleRunConfiguration);
    }

    private static @NotNull String typeLabel(@NotNull String typeId) {
        return typeId.equals("Application") ? "Java Application" : "Gradle";
    }

    private static @NotNull String valueOrNotSet(@Nullable String value) {
        return value == null || value.isBlank() ? NOT_SET : value;
    }

    private static @NotNull String valueOrNotAvailable(@Nullable String value) {
        return value == null || value.isBlank() ? NOT_AVAILABLE : value;
    }

    private static @NotNull String completionMessage() {
        return "[FarRun] Ready for remote execution.";
    }
}
