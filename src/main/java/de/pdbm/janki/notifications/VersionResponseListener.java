package de.pdbm.janki.notifications;

/**
 * 版本响应监听器
 */
public interface VersionResponseListener extends NotificationListener {

    /**
     * 当收到车辆的版本响应时调用
     * @param versionResponse 版本响应通知
     */
    void onVersionResponse(VersionResponse versionResponse);
}
