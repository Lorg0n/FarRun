package dev.farrun.run.execution.ui;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.function.Consumer;

/** A native Run console wrapper: status chrome above the unmodified IntelliJ console. */
public final class FarRunConsoleView implements ConsoleView, Disposable {
    private final @NotNull ConsoleView delegate;
    private final @NotNull ExecutionSession session;
    private final @NotNull String serverName;
    private final @NotNull JPanel component = new JPanel(new BorderLayout());
    private final @NotNull JLabel status = label();
    private final @NotNull JLabel details = label();
    private final @NotNull JLabel failure = label();
    private final EnumMap<ExecutionStage, JLabel> steps = new EnumMap<>(ExecutionStage.class);
    private final Consumer<ExecutionSnapshot> listener = this::render;

    public FarRunConsoleView(@NotNull ConsoleView delegate, @NotNull ExecutionSession session,
                             @NotNull String configurationName, @NotNull String serverName) {
        this.delegate = delegate;
        this.session = session;
        this.serverName = serverName;
        JPanel header = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        header.setBorder(JBUI.Borders.compound(JBUI.Borders.empty(9, 12, 8, 12),
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border())));
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.setOpaque(false);
        JLabel name = new JLabel("FarRun  ·  " + configurationName);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        title.add(name); title.add(spacer()); title.add(status);
        header.add(title, BorderLayout.NORTH);
        details.setText(serverName + " · Remote process is preparing");
        header.add(details, BorderLayout.CENTER);
        JPanel stepper = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(10), 0));
        stepper.setOpaque(false);
        for (ExecutionStage stage : ExecutionStage.values()) { JLabel step = label(); steps.put(stage, step); stepper.add(step); }
        header.add(stepper, BorderLayout.SOUTH);
        failure.setBorder(JBUI.Borders.empty(0, 12, 7, 12));
        failure.setVisible(false);
        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH); top.add(failure, BorderLayout.SOUTH);
        component.add(top, BorderLayout.NORTH);
        component.add(delegate.getComponent(), BorderLayout.CENTER);
        session.addListener(listener);
    }

    private static JLabel label() { JLabel label = new JLabel(); label.setOpaque(false); return label; }
    private static JLabel spacer() { JLabel label = new JLabel("     "); return label; }
    private void render(ExecutionSnapshot snapshot) {
        Runnable task = () -> {
            status.setText("● " + snapshot.status().title());
            status.setForeground(statusColor(snapshot.status()));
            String java = snapshot.javaVersion() == null ? "Java —" : snapshot.javaVersion();
            String pid = snapshot.remotePid() == null ? "PID —" : "PID " + snapshot.remotePid();
            details.setText(serverName + " · " + java + " · " + pid + " · " + format(snapshot.elapsed()));
            for (ExecutionStage stage : ExecutionStage.values()) {
                StageStatus state = snapshot.stages().get(stage);
                JLabel label = steps.get(stage);
                label.setText(symbol(state) + " " + stage.title());
                label.setForeground(stageColor(state));
            }
            failure.setVisible(snapshot.failure() != null);
            if (snapshot.failure() != null) { failure.setText("Failure summary: " + snapshot.failure()); failure.setForeground(JBColor.RED); }
            component.revalidate(); component.repaint();
        };
        if (SwingUtilities.isEventDispatchThread()) task.run(); else SwingUtilities.invokeLater(task);
    }
    private static String format(java.time.Duration duration) { return String.format(java.util.Locale.ROOT, "%d:%02d", duration.toMinutes(), duration.toSecondsPart()); }
    private static String symbol(StageStatus state) { return switch (state) { case SUCCESS -> "✓"; case IN_PROGRESS -> "●"; case FAILED -> "✗"; case CANCELLED -> "■"; case SKIPPED -> "–"; case PENDING -> "○"; }; }
    private static Color stageColor(StageStatus state) { return switch (state) { case SUCCESS -> JBColor.GREEN; case IN_PROGRESS -> JBColor.BLUE; case FAILED -> JBColor.RED; case CANCELLED -> JBColor.GRAY; default -> JBColor.GRAY; }; }
    private static Color statusColor(ExecutionStatus state) { return switch (state) { case RUNNING, COMPLETED -> JBColor.GREEN; case FAILED, CONNECTION_LOST -> JBColor.RED; case STOPPED -> JBColor.GRAY; default -> JBColor.BLUE; }; }

    @Override public void print(@NotNull String text, @NotNull ConsoleViewContentType type) { delegate.print(text, type); }
    @Override public void printHyperlink(@NotNull String text, HyperlinkInfo info) { delegate.printHyperlink(text, info); }
    @Override public void addMessageFilter(@NotNull Filter filter) { delegate.addMessageFilter(filter); }
    @Override public void setHelpId(@NotNull String helpId) { delegate.setHelpId(helpId); }
    @Override public void clear() { delegate.clear(); }
    @Override public void scrollTo(int offset) { delegate.scrollTo(offset); }
    @Override public void attachToProcess(@NotNull ProcessHandler processHandler) { delegate.attachToProcess(processHandler); }
    @Override public void setOutputPaused(boolean value) { delegate.setOutputPaused(value); }
    @Override public boolean isOutputPaused() { return delegate.isOutputPaused(); }
    @Override public boolean hasDeferredOutput() { return delegate.hasDeferredOutput(); }
    @Override public void performWhenNoDeferredOutput(@NotNull Runnable runnable) { delegate.performWhenNoDeferredOutput(runnable); }
    @Override public int getContentSize() { return delegate.getContentSize(); }
    @Override public boolean canPause() { return delegate.canPause(); }
    @Override public @NotNull AnAction[] createConsoleActions() { return delegate.createConsoleActions(); }
    @Override public void allowHeavyFilters() { delegate.allowHeavyFilters(); }
    @Override public @NotNull JComponent getComponent() { return component; }
    @Override public @NotNull JComponent getPreferredFocusableComponent() { return delegate.getPreferredFocusableComponent(); }
    @Override public void dispose() { session.removeListener(listener); delegate.dispose(); }
}
