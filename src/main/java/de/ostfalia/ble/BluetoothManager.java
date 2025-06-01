package de.ostfalia.ble;

import com.github.hypfvieh.bluetooth.DeviceManager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter-Klasse für Bluetooth-Verwaltungsfunktionen der hypfvieh-Bibliothek.
 *
 * Diese Klasse stellt eine Singleton-Implementierung für die Bluetooth-Verwaltung bereit
 * und behandelt die Kommunikation mit Bluetooth-Low-Energy-Geräten über DBus.
 */
public class BluetoothManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BluetoothManager.class);

    /** Singleton-Instanz des BluetoothManagers */
    private static BluetoothManager instance;

    /** Der zugrunde liegende DeviceManager aus der hypfvieh-Bibliothek */
    private DeviceManager deviceManager;

    /** Cache für bereits erkannte Bluetooth-Geräte */
    private final Map<String, BluetoothDevice> deviceCache = new ConcurrentHashMap<>();

    /** Zuordnung zwischen DBus-Pfaden und GATT-Charakteristiken für Benachrichtigungen */
    private final Map<String, BluetoothGattCharacteristic> characteristicsByPath = new ConcurrentHashMap<>();

    /**
     * Privater Konstruktor für Singleton-Pattern.
     * Initialisiert den DeviceManager und konfiguriert DBus-Signal-Handler.
     */
    private BluetoothManager() {
        try {
            deviceManager = DeviceManager.createInstance(false);
            setupSignalHandlers();
            LOGGER.info("Bluetooth-Manager erfolgreich initialisiert");
        } catch (Exception e) {
            LOGGER.error("Fehler bei der Initialisierung des Bluetooth-Managers: {}", e.getMessage(), e);
        }
    }

    /**
     * Gibt die Singleton-Instanz des BluetoothManagers zurück.
     *
     * @return Die BluetoothManager-Instanz
     */
    public static BluetoothManager getBluetoothManager() {
        if (instance == null) {
            instance = new BluetoothManager();
        }
        return instance;
    }

    /**
     * Konfiguriert DBus-Signal-Handler für Bluetooth-Ereignisse.
     *
     * Diese Methode registriert Handler für PropertiesChanged-Signale,
     * die für die Überwachung von GATT-Charakteristik-Änderungen verwendet werden.
     */
    private void setupSignalHandlers() {
        try {
            LOGGER.debug("Registriere DBus-Signal-Handler...");

            PropertiesChangedHandler propHandler = new PropertiesChangedHandler();
            deviceManager.registerPropertyHandler(propHandler);

            LOGGER.debug("DBus-Signal-Handler erfolgreich registriert");
        } catch (Exception e) {
            LOGGER.error("Fehler beim Einrichten der DBus-Signal-Handler: {}", e.getMessage(), e);
        }
    }

    /**
     * Registriert eine GATT-Charakteristik für Wertänderungs-Benachrichtigungen.
     *
     * @param characteristic Die zu registrierende GATT-Charakteristik
     */
    public void registerCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (characteristic != null) {
            String path = characteristic.getWrappedCharacteristic().getDbusPath();
            characteristicsByPath.put(path, characteristic);
            LOGGER.debug("GATT-Charakteristik registriert: Pfad={}, UUID={}", path, characteristic.getUUID());
        }
    }

    /**
     * Gibt eine Liste aller verfügbaren Bluetooth-Geräte zurück.
     *
     * Diese Methode führt bei Bedarf eine Geräteerkennung durch und
     * gibt sowohl bereits bekannte als auch neu erkannte Geräte zurück.
     *
     * @return Liste der verfügbaren BluetoothDevice-Objekte
     */
    public List<BluetoothDevice> getDevices() {
        List<BluetoothDevice> result = new ArrayList<>();

        try {
            List<com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter> adapters = deviceManager.getAdapters();

            if (adapters.isEmpty()) {
                LOGGER.warn("Keine Bluetooth-Adapter gefunden");
                return result;
            }

            com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter adapter = adapters.get(0);

            // Adapter einschalten falls notwendig
            ensureAdapterPowered(adapter);

            // Geräteerkennung durchführen
            List<com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice> devices = performDeviceDiscovery(adapter);

            // Geräte in Adapter-Objekte konvertieren
            for (com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice device : devices) {
                BluetoothDevice adaptedDevice = getOrCreateDevice(device);
                result.add(adaptedDevice);
            }

            LOGGER.debug("Insgesamt {} Bluetooth-Geräte gefunden", result.size());

        } catch (Exception e) {
            LOGGER.error("Fehler beim Abrufen der Geräteliste: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Stellt sicher, dass der Bluetooth-Adapter eingeschaltet ist.
     *
     * @param adapter Der zu prüfende Bluetooth-Adapter
     * @throws InterruptedException falls der Thread unterbrochen wird
     */
    private void ensureAdapterPowered(com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter adapter)
            throws InterruptedException {
        if (!adapter.isPowered()) {
            LOGGER.debug("Schalte Bluetooth-Adapter ein...");
            adapter.setPowered(true);
            Thread.sleep(2000); // Warten bis Adapter bereit ist
        }
    }

    /**
     * Führt die Bluetooth-Geräteerkennung durch.
     *
     * @param adapter Der zu verwendende Bluetooth-Adapter
     * @return Liste der erkannten Geräte
     * @throws InterruptedException falls der Thread unterbrochen wird
     */
    private List<com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice> performDeviceDiscovery(
            com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter adapter) throws InterruptedException {

        // Zunächst bereits bekannte Geräte abrufen
        List<com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice> devices =
                deviceManager.scanForBluetoothDevices(adapter.getAddress(), 1000);

        // Falls keine Geräte gefunden wurden, aktive Erkennung starten
        if (devices.isEmpty()) {
            LOGGER.debug("Starte aktive Geräteerkennung...");

            adapter.startDiscovery();
            Thread.sleep(5000); // Erkennungszeit
            adapter.stopDiscovery();

            devices = deviceManager.scanForBluetoothDevices(adapter.getAddress(), 5000);
        }

        return devices;
    }

    /**
     * Holt ein Gerät aus dem Cache oder erstellt ein neues Adapter-Objekt.
     *
     * @param device Das hypfvieh BluetoothDevice-Objekt
     * @return Das entsprechende BluetoothDevice-Adapter-Objekt
     */
    private BluetoothDevice getOrCreateDevice(com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice device) {
        String address = device.getAddress();

        return deviceCache.computeIfAbsent(address, k -> {
            LOGGER.debug("Erstelle neues BluetoothDevice für Adresse: {}", address);
            return new BluetoothDevice(device);
        });
    }

    /**
     * Schließt den BluetoothManager und gibt Ressourcen frei.
     */
    public void close() {
        if (deviceManager != null) {
            DBusConnection connection = deviceManager.getDbusConnection();
            if (connection != null) {
                try {
                    connection.disconnect();
                    LOGGER.debug("DBus-Verbindung erfolgreich geschlossen");
                } catch (Exception e) {
                    LOGGER.error("Fehler beim Schließen der DBus-Verbindung: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Innere Klasse für die Behandlung von DBus PropertiesChanged-Signalen.
     *
     * Diese Klasse überwacht Änderungen an GATT-Charakteristik-Werten und
     * leitet entsprechende Benachrichtigungen an die registrierten Charakteristiken weiter.
     */
    private class PropertiesChangedHandler extends org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler {

        @Override
        public void handle(PropertiesChanged signal) {
            String path = signal.getPath();
            String interfaceName = signal.getInterfaceName();
            Map<String, Variant<?>> changedProperties = signal.getPropertiesChanged();

            // Nur GATT-Charakteristik-Änderungen verarbeiten
            if (!"org.bluez.GattCharacteristic1".equals(interfaceName)) {
                return;
            }

            // Prüfen ob sich der Value geändert hat
            if (!changedProperties.containsKey("Value")) {
                return;
            }

            try {
                byte[] byteValue = extractByteValue(changedProperties.get("Value"));

                if (byteValue != null) {
                    notifyCharacteristicValueChanged(path, byteValue);
                }

            } catch (Exception e) {
                LOGGER.error("Fehler bei der Verarbeitung von Charakteristik-Wertänderung: {}", e.getMessage(), e);
            }
        }

        /**
         * Extrahiert den Byte-Array-Wert aus einer DBus-Variant.
         *
         * @param variant Die DBus-Variant mit dem Wert
         * @return Der extrahierte Byte-Array oder null bei Fehlern
         */
        private byte[] extractByteValue(Variant<?> variant) {
            Object value = variant.getValue();

            if (value instanceof byte[]) {
                return (byte[]) value;
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Byte> byteList = (List<Byte>) value;
                byte[] result = new byte[byteList.size()];
                for (int i = 0; i < byteList.size(); i++) {
                    result[i] = byteList.get(i);
                }
                return result;
            }

            return null;
        }

        /**
         * Benachrichtigt die entsprechende Charakteristik über eine Wertänderung.
         *
         * @param path Der DBus-Pfad der Charakteristik
         * @param value Der neue Wert
         */
        private void notifyCharacteristicValueChanged(String path, byte[] value) {
            BluetoothGattCharacteristic characteristic = characteristicsByPath.get(path);

            if (characteristic != null) {
                LOGGER.debug("Benachrichtige über Wertänderung: Pfad={}, Größe={} Bytes", path, value.length);
                characteristic.notifyValueChanged(value);
            } else {
                LOGGER.debug("Keine registrierte Charakteristik für Pfad: {}", path);
            }
        }
    }
}