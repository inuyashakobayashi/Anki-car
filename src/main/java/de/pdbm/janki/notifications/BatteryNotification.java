package de.pdbm.janki.notifications;
import de.pdbm.janki.Vehicle;

public class BatteryNotification extends Notification {
    private final int batteryLevelMs; // 毫伏

    public BatteryNotification(Vehicle vehicle, int level) {
        super(vehicle);
        this.batteryLevelMs = level;
    }

    public int getBatteryLevelMs() { return batteryLevelMs; }

    // 估算百分比 (假设满电 4100mV, 空电 3500mV)
    public double getPercentage() {
        return Math.max(0, Math.min(1.0, (batteryLevelMs - 3500) / 600.0));
    }
}