package dev.farrun.actions;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Verifies that the FarRun plugin is loaded and can interact with the IntelliJ Platform.
 */
public final class HelloAction extends AnAction {

    private static final String NOTIFICATION_GROUP_ID = "FarRun Notifications";
    private static final String MESSAGE = "FarRun plugin is running successfully!";

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(MESSAGE, NotificationType.INFORMATION)
                .notify(event.getProject());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
