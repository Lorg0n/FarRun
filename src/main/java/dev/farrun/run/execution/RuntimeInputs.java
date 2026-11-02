package dev.farrun.run.execution;

import dev.farrun.run.configuration.RuntimeFileMapping;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves configured input files and expands their explicit remote-path macros. */
public final class RuntimeInputs {
    private static final Pattern MACRO = Pattern.compile("\\$\\{farrun:([A-Za-z][A-Za-z0-9_-]*)}");

    private final @NotNull Map<String, Path> files;

    public RuntimeInputs(@NotNull Path projectDirectory, @NotNull List<RuntimeFileMapping> mappings) {
        Map<String, Path> resolved = new LinkedHashMap<>();
        for (RuntimeFileMapping mapping : mappings) {
            Path path = Path.of(mapping.localPath());
            if (!path.isAbsolute()) {
                path = projectDirectory.resolve(path);
            }
            path = path.normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Runtime input '" + mapping.alias() + "' is not a regular file: " + path);
            }
            if (resolved.putIfAbsent(mapping.alias(), path) != null) {
                throw new IllegalArgumentException("Runtime input alias is duplicated: " + mapping.alias());
            }
        }
        files = Map.copyOf(resolved);
    }

    public @NotNull Map<String, Path> files() {
        return files;
    }

    public @NotNull List<String> expandArguments(@NotNull List<String> arguments, @NotNull String remoteInputsDirectory) {
        return arguments.stream().map(argument -> expand(argument, remoteInputsDirectory)).toList();
    }

    private @NotNull String expand(@NotNull String argument, @NotNull String remoteInputsDirectory) {
        Matcher matcher = MACRO.matcher(argument);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String alias = matcher.group(1);
            Path local = files.get(alias);
            if (local == null) {
                throw new IllegalArgumentException("Unknown FarRun runtime input alias: " + alias);
            }
            String remote = remoteInputsDirectory + "/" + alias + "/" + safeFileName(local.getFileName().toString());
            matcher.appendReplacement(result, Matcher.quoteReplacement(remote));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static @NotNull String safeFileName(@NotNull String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
