package dev.farrun.run.execution.console;

import com.intellij.openapi.util.Key;

/** Process output keys that preserve FarRun log semantics until the console renders them. */
public final class FarRunOutputTypes {
    public static final Key<?> INFO = Key.create("FARRUN_INFO");
    public static final Key<?> ACTIVE = Key.create("FARRUN_ACTIVE");
    public static final Key<?> SUCCESS = Key.create("FARRUN_SUCCESS");
    public static final Key<?> WARNING = Key.create("FARRUN_WARNING");
    public static final Key<?> COMMAND = Key.create("FARRUN_COMMAND");
    private FarRunOutputTypes() { }
}
