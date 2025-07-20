package org.example.liteworkspace.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;

/**
 * 通过 Plugins Devkit 创建的 Action，继承自 AnAction。
 */
public class TestAction extends AnAction {

    /**
     * 点击菜单或按钮触发此事件。
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        Notification notification = new Notification(
                "Lite Workspace",         // groupId: 建议在 plugin.xml 中注册
                "测试通知标题",              // title
                "这是一条测试通知内容。",     // content
                NotificationType.INFORMATION  // 类型：INFORMATION, WARNING, ERROR
        );
        Notifications.Bus.notify(notification);
    }
}
