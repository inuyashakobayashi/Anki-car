package de.pdbm.janki.notifications;

import de.pdbm.janki.Vehicle;

/**
 * Ping 响应通知
 * 当车辆收到 PING_REQUEST (0x16) 后会返回此响应 (0x17)
 * 用于检测车辆连接状态和网络延迟
 */
public class PingResponse extends Notification {

    private final long receivedTimestamp;

    public PingResponse(Vehicle vehicle) {
        super(vehicle);
        this.receivedTimestamp = System.currentTimeMillis();
    }

    /**
     * 获取收到响应的时间戳
     */
    public long getReceivedTimestamp() {
        return receivedTimestamp;
    }
}
