package de.pdbm.janki.notifications;

/**
 * Ping 响应监听器
 */
public interface PingResponseListener extends NotificationListener {

    /**
     * 当收到车辆的 Ping 响应时调用
     * @param pingResponse Ping 响应通知
     */
    void onPingResponse(PingResponse pingResponse);
}
