package de.pdbm.janki.notifications;

/**
 * 道路中心偏移更新监听器
 */
public interface OffsetFromRoadCenterUpdateListener extends NotificationListener {

    /**
     * 当车辆横向位置发生变化时调用
     * @param update 偏移更新通知
     */
    void onOffsetUpdate(OffsetFromRoadCenterUpdate update);
}
