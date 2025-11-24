package de.pdbm.janki.notifications;
public interface BatteryListener extends NotificationListener {
    void onBatteryLevel(BatteryNotification update);
}