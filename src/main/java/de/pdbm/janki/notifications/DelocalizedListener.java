package de.pdbm.janki.notifications;

/**
 * 车辆脱轨监听器
 */
public interface DelocalizedListener extends NotificationListener {

    /**
     * 当车辆脱离轨道时调用
     * @param notification 脱轨通知
     */
    void onDelocalized(DelocalizedNotification notification);
}
