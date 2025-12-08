package de.pdbm.janki.notifications;

import de.pdbm.janki.Vehicle;

/**
 * 车辆脱轨通知 (Delocalized)
 * 当车辆失去轨道定位时 (脱离轨道) 会发送此通知 (0x2b)
 *
 * 这通常意味着:
 * - 车辆被拿离轨道
 * - 车辆翻车
 * - 轨道扫描传感器无法识别轨道码
 */
public class DelocalizedNotification extends Notification {

    private final long timestamp;

    public DelocalizedNotification(Vehicle vehicle) {
        super(vehicle);
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取脱轨发生的时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "DelocalizedNotification{vehicle=" + getVehicle().getMacAddress() + ", timestamp=" + timestamp + "}";
    }
}
