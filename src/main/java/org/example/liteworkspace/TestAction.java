package org.example.liteworkspace;

/**
 * 通过Pulgins Devkit创建的action继承了Ananction
 *
 */
public class TestAction extends AnAction {


    /**
     * 需要实现点击事件发生之后的抽象方法
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        NotificationGroup notificationGroup = new NotificationGroup("testid", NotificationDisplayType.BALLOON, false);
        /**
         * content :  通知内容
         * type  ：通知的类型，warning,info,error
         */
        Notification notification = notificationGroup.createNotification("测试通知", MessageType.INFO);
        Notifications.Bus.notify(notification);
    }
}
