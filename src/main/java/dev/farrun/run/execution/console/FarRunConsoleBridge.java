package dev.farrun.run.execution.console;

import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.Key;
import com.intellij.ui.JBColor;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

/** Renders semantic ProcessHandler output in the native console without ANSI escape sequences. */
public final class FarRunConsoleBridge implements ProcessListener {
    private static final ConsoleViewContentType FAR_INFO = type("FARRUN_INFO", JBColor.BLUE);
    private static final ConsoleViewContentType FAR_ACTIVE = type("FARRUN_ACTIVE", JBColor.BLUE);
    private static final ConsoleViewContentType FAR_SUCCESS = type("FARRUN_SUCCESS", JBColor.GREEN);
    private static final ConsoleViewContentType FAR_WARNING = type("FARRUN_WARNING", JBColor.ORANGE);
    private static final ConsoleViewContentType FAR_COMMAND = type("FARRUN_COMMAND", JBColor.GRAY);
    private final @NotNull ConsoleView console;

    public FarRunConsoleBridge(@NotNull ConsoleView console) { this.console = console; }
    public static void attach(@NotNull ProcessHandler handler, @NotNull ConsoleView console) {
        handler.addProcessListener(new FarRunConsoleBridge(console));
    }
    @Override public void onTextAvailable(ProcessEvent event, Key outputType) {
        console.print(event.getText(), resolve(outputType));
    }
    private static ConsoleViewContentType resolve(Key<?> key) {
        if (key == FarRunOutputTypes.INFO) return FAR_INFO;
        if (key == FarRunOutputTypes.ACTIVE) return FAR_ACTIVE;
        if (key == FarRunOutputTypes.SUCCESS) return FAR_SUCCESS;
        if (key == FarRunOutputTypes.WARNING) return FAR_WARNING;
        if (key == FarRunOutputTypes.COMMAND) return FAR_COMMAND;
        if (key == ProcessOutputTypes.STDERR) return ConsoleViewContentType.ERROR_OUTPUT;
        return ConsoleViewContentType.NORMAL_OUTPUT;
    }
    private static ConsoleViewContentType type(String name, JBColor color) {
        return new ConsoleViewContentType(name, new TextAttributes(color, null, null, null, java.awt.Font.PLAIN));
    }
}
