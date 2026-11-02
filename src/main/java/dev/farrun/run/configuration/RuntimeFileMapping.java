package dev.farrun.run.configuration;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/** A user-named local file staged inside one isolated remote launch directory. */
public record RuntimeFileMapping(@NotNull String alias, @NotNull String localPath) {
    private static final Pattern ALIAS = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    public RuntimeFileMapping {
        alias = Objects.requireNonNull(alias, "alias").trim();
        localPath = Objects.requireNonNull(localPath, "localPath").trim();
        if (!ALIAS.matcher(alias).matches()) {
            throw new IllegalArgumentException("File alias must start with a letter and contain only letters, digits, _ or -.");
        }
        if (localPath.isBlank() || localPath.indexOf('\n') >= 0 || localPath.indexOf('\t') >= 0) {
            throw new IllegalArgumentException("Local file path is invalid.");
        }
    }
}
