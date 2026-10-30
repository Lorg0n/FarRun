package dev.farrun.run.execution.java;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves a conservative Java launch requirement from IntelliJ's source configuration. */
public final class JavaRuntimeRequirement {
    private static final Pattern VERSION = Pattern.compile("(?:^|[^0-9])(\\d{1,3})(?:[.+_-]|$)");

    private JavaRuntimeRequirement() {
    }

    public static int resolve(@NotNull Project project, Module module, @NotNull String sourceName) {
        Sdk sdk = module == null ? ProjectRootManager.getInstance(project).getProjectSdk()
                : ModuleRootManager.getInstance(module).getSdk();
        if (sdk == null) {
            sdk = ProjectRootManager.getInstance(project).getProjectSdk();
        }
        Integer sdkMajor = sdk == null ? null : parse(sdk.getVersionString());
        Integer languageMajor = languageLevel(project, module);
        if (sdkMajor == null && languageMajor == null) {
            throw new IllegalArgumentException("Cannot determine the Java version for '" + sourceName
                    + "'. Configure a Project SDK or Module SDK with a Java language level.");
        }
        if (sdkMajor != null && languageMajor != null && sdkMajor < languageMajor) {
            throw new IllegalArgumentException("The SDK for '" + sourceName + "' is Java " + sdkMajor
                    + ", but its language level requires Java " + languageMajor + ".");
        }
        return Math.max(sdkMajor == null ? 0 : sdkMajor, languageMajor == null ? 0 : languageMajor);
    }

    private static Integer languageLevel(@NotNull Project project, Module module) {
        var level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
        return level == null ? null : parse(level.toString());
    }

    private static Integer parse(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("1.")) {
            return Integer.valueOf(value.substring(2, 3));
        }
        Matcher matcher = VERSION.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
