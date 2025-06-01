package de.ostfalia.ble;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Adapter-Klasse, die das BluetoothDevice aus der com.github.hypfvieh-Bibliothek
 * an die von der ursprünglichen Implementierung erwartete Schnittstelle anpasst.
 *
 * Diese Klasse stellt eine Wrapper-Implementierung dar, die die neue hypfvieh-Bluetooth-Bibliothek
 * mit dem bestehenden Code kompatibel macht.
 */
public class BluetoothDevice {

    /** Das gekapselte BluetoothDevice-Objekt aus der hypfvieh-Bibliothek */
    private final com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice device;

    /** Callback-Funktion für Verbindungsbenachrichtigungen */
    private Consumer<Boolean> connectedNotificationConsumer;

    /**
     * Konstruktor für BluetoothDevice
     *
     * @param device Das zu kapselnde hypfvieh BluetoothDevice-Objekt
     */
    public BluetoothDevice(com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice device) {
        this.device = device;
    }

    /**
     * Stellt eine Verbindung zum Bluetooth-Gerät her.
     *
     * @return true wenn die Verbindung erfolgreich hergestellt wurde, false andernfalls
     */
    public boolean connect() {
        return device.connect();
    }

    /**
     * Überprüft den aktuellen Verbindungsstatus des Geräts.
     *
     * @return true wenn das Gerät verbunden ist, false andernfalls
     */
    public boolean getConnected() {
        try {
            return device.isConnected();
        } catch (Exception e) {
            // Bei Fehlern wird angenommen, dass das Gerät nicht verbunden ist
            return false;
        }
    }

    /**
     * Trennt die Verbindung zum Bluetooth-Gerät.
     */
    public void disconnect() {
        device.disconnect();
    }

    /**
     * Gibt die MAC-Adresse des Bluetooth-Geräts zurück.
     *
     * @return Die MAC-Adresse als String
     */
    public String getAddress() {
        return device.getAddress();
    }

    /**
     * Gibt die Liste der vom Gerät bereitgestellten Service-UUIDs zurück.
     *
     * @return Liste der UUIDs in Kleinbuchstaben
     */
    public List<String> getUUIDs() {
        List<String> result = new ArrayList<>();
        String[] uuids = device.getUuids();

        if (uuids != null) {
            for (String uuid : uuids) {
                result.add(uuid.toLowerCase());
            }
        }

        return result;
    }

    /**
     * Gibt die Liste der verfügbaren GATT-Services des Geräts zurück.
     *
     * @return Liste der BluetoothGattService-Objekte
     */
    public List<BluetoothGattService> getServices() {
        List<BluetoothGattService> result = new ArrayList<>();
        List<com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService> services = device.getGattServices();

        if (services != null) {
            for (com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService service : services) {
                result.add(new BluetoothGattService(service));
            }
        }

        return result;
    }

    /**
     * Aktiviert Benachrichtigungen über Änderungen des Verbindungsstatus.
     *
     * @param callback Callback-Funktion, die bei Statusänderungen aufgerufen wird
     */
    public void enableConnectedNotifications(Consumer<Boolean> callback) {
        this.connectedNotificationConsumer = callback;


    }

    /**
     * Deaktiviert Benachrichtigungen über Verbindungsstatusänderungen.
     */
    public void disableConnectedNotifications() {
        this.connectedNotificationConsumer = null;
    }

    /**
     * Benachrichtigt registrierte Listener über Änderungen des Verbindungsstatus.
     * Diese Methode wird typischerweise vom BluetoothManager aufgerufen.
     *
     * @param connected Der aktuelle Verbindungsstatus
     */
    public void notifyConnectionState(boolean connected) {
        if (connectedNotificationConsumer != null) {
            connectedNotificationConsumer.accept(connected);
        }
    }

    /**
     * Gibt das gekapselte hypfvieh BluetoothDevice-Objekt zurück.
     * Diese Methode sollte nur verwendet werden, wenn direkter Zugriff auf die
     * zugrundeliegende Implementierung erforderlich ist.
     *
     * @return Das hypfvieh BluetoothDevice-Objekt
     */
    public com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice getWrappedDevice() {
        return device;
    }

    /**
     * Vergleicht zwei BluetoothDevice-Objekte basierend auf ihrer MAC-Adresse.
     *
     * @param obj Das zu vergleichende Objekt
     * @return true wenn die MAC-Adressen identisch sind, false andernfalls
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BluetoothDevice) {
            return this.getAddress().equals(((BluetoothDevice) obj).getAddress());
        }
        return false;
    }

    /**
     * Generiert einen Hash-Code basierend auf der MAC-Adresse des Geräts.
     *
     * @return Hash-Code der MAC-Adresse
     */
    @Override
    public int hashCode() {
        return getAddress().hashCode();
    }
}