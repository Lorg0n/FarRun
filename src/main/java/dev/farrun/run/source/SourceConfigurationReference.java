package dev.farrun.run.source;

import org.jetbrains.annotations.Nullable;

/**
 * Persisted link to a source Run Configuration.
 *
 * <p>The settings ID is authoritative. Type and name let the rename listener update an
 * ID that IntelliJ derives from a configuration name without ever using the name as a lookup key.</p>
 */
public record SourceConfigurationReference(String settingsId, String typeId, String name) {

    public static @Nullable SourceConfigurationReference from(
            @Nullable String settingsId,
            @Nullable String typeId,
            @Nullable String name
    ) {
        if (settingsId == null || typeId == null || name == null) {
            return null;
        }
        return new SourceConfigurationReference(settingsId, typeId, name);
    }
}
