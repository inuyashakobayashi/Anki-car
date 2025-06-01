package de.ostfalia.ble;

import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;
import org.bluez.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapter-Klasse für GATT-Charakteristiken der hypfvieh-Bibliothek.
 *
 * Eine GATT-Charakteristik repräsentiert einen spezifischen Datenpunkt eines
 * Bluetooth-Geräts, über den Daten gelesen, geschrieben oder Benachrichtigungen
 * empfangen werden können.
 *
 * Für Anki-Fahrzeuge gibt es typischerweise:
 * - Write-Charakteristik: Für Befehle (Geschwindigkeit, Spurwechsel)
 * - Read-Charakteristik: Für Benachrichtigungen (Position, Batteriestand)
 */
public class BluetoothGattCharacteristic {

    private static final Logger LOGGER = LoggerFactory.getLogger(BluetoothGattCharacteristic.class);

    /** Das gekapselte GATT-Charakteristik-Objekt aus der hypfvieh-Bibliothek */
    private final com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic characteristic;

    /** Callback-Funktion für Wertänderungs-Benachrichtigungen */
    private Consumer<byte[]> valueNotificationConsumer;

    /**
     * Konstruktor für BluetoothGattCharacteristic.
     *
     * @param characteristic Das zu kapselnde hypfvieh BluetoothGattCharacteristic-Objekt
     */
    public BluetoothGattCharacteristic(com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic characteristic) {
        this.characteristic = characteristic;
    }

    /**
     * Gibt die eindeutige UUID der GATT-Charakteristik zurück.
     *
     * Für Anki-Fahrzeuge sind die wichtigsten UUIDs:
     * - BE15BEE1-...: Write-Charakteristik (Befehle senden)
     * - BE15BEE0-...: Read-Charakteristik (Daten empfangen)
     *
     * @return Die Charakteristik-UUID in Großbuchstaben
     */
    public String getUUID() {
        return characteristic.getUuid().toUpperCase();
    }

    /**
     * Schreibt Daten in die Charakteristik.
     *
     * Diese Methode wird verwendet, um Befehle an das Anki-Fahrzeug zu senden,
     * wie z.B. Geschwindigkeitsänderungen oder Spurwechsel-Befehle.
     *
     * @param bytes Die zu schreibenden Daten (typischerweise Anki-Befehlsnachrichten)
     * @return true wenn das Schreiben erfolgreich war, false bei Fehlern
     */
    public boolean writeValue(byte[] bytes) {
        try {
            Map<String, Object> options = new HashMap<>();
            characteristic.writeValue(bytes, options);

            LOGGER.debug("Daten erfolgreich geschrieben: UUID={}, Länge={} Bytes",
                    getUUID(), bytes.length);
            return true;

        } catch (BluezFailedException | BluezInProgressException | BluezNotPermittedException
                 | BluezNotAuthorizedException | BluezNotSupportedException
                 | BluezInvalidValueLengthException e) {

            LOGGER.error("Fehler beim Schreiben in Charakteristik {}: {}", getUUID(), e.getMessage());
            return false;
        }
    }

    /**
     * Liest den aktuellen Wert der Charakteristik.
     *
     * @return Der aktuelle Wert als Byte-Array, oder leeres Array bei Fehlern
     */
    public byte[] readValue() {
        try {
            Map<String, Object> options = new HashMap<>();
            byte[] value = characteristic.readValue(options);

            LOGGER.debug("Wert gelesen: UUID={}, Länge={} Bytes", getUUID(), value.length);
            return value;

        } catch (BluezFailedException | BluezInProgressException | BluezNotPermittedException
                 | BluezNotAuthorizedException | BluezNotSupportedException
                 | BluezInvalidOffsetException e) {

            LOGGER.error("Fehler beim Lesen der Charakteristik {}: {}", getUUID(), e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Aktiviert Benachrichtigungen für Wertänderungen dieser Charakteristik.
     *
     * Diese Methode ist essentiell für das Empfangen von Fahrzeugdaten wie
     * Positionsupdates, Batteriestand oder anderen Sensordaten vom Anki-Fahrzeug.
     *
     * @param callback Callback-Funktion, die bei Wertänderungen aufgerufen wird
     */
    public void enableValueNotifications(Consumer<byte[]> callback) {
        this.valueNotificationConsumer = callback;

        try {
            // Bei BluetoothManager für Benachrichtigungen registrieren
            BluetoothManager manager = BluetoothManager.getBluetoothManager();
            manager.registerCharacteristic(this);

            // Benachrichtigungen auf Bluetooth-Ebene aktivieren
            characteristic.startNotify();

            LOGGER.debug("Benachrichtigungen aktiviert für Charakteristik: UUID={}, Pfad={}",
                    getUUID(), characteristic.getDbusPath());

        } catch (Exception e) {
            LOGGER.error("Fehler beim Aktivieren der Benachrichtigungen für {}: {}",
                    getUUID(), e.getMessage(), e);
        }
    }

    /**
     * Deaktiviert Benachrichtigungen für Wertänderungen.
     */
    public void disableValueNotifications() {
        this.valueNotificationConsumer = null;

        try {
            characteristic.stopNotify();
            LOGGER.debug("Benachrichtigungen deaktiviert für Charakteristik: {}", getUUID());
        } catch (BluezFailedException e) {
            LOGGER.error("Fehler beim Deaktivieren der Benachrichtigungen für {}: {}",
                    getUUID(), e.getMessage());
        }
    }

    /**
     * Verarbeitet eine Wertänderungs-Benachrichtigung.
     *
     * Diese Methode wird vom BluetoothManager aufgerufen, wenn sich der Wert
     * der Charakteristik ändert. Sie leitet die Benachrichtigung an den
     * registrierten Callback weiter.
     *
     * @param value Der neue Wert der Charakteristik
     */
    public void notifyValueChanged(byte[] value) {
        if (valueNotificationConsumer != null) {
            LOGGER.debug("Wertänderungs-Benachrichtigung empfangen: UUID={}, Größe={} Bytes",
                    getUUID(), value.length);

            // In Debug-Modus die ersten Bytes ausgeben
            if (LOGGER.isDebugEnabled() && value.length > 0) {
                StringBuilder hexString = new StringBuilder();
                for (int i = 0; i < Math.min(value.length, 10); i++) {
                    hexString.append(String.format("%02X ", value[i]));
                }
                LOGGER.debug("Dateninhalt (erste {} Bytes): [{}]",
                        Math.min(value.length, 10), hexString.toString().trim());
            }

            // Callback aufrufen
            valueNotificationConsumer.accept(value);
        }
    }

    /**
     * Gibt das gekapselte hypfvieh GATT-Charakteristik-Objekt zurück.
     *
     * Diese Methode wird hauptsächlich von anderen Adapter-Klassen verwendet,
     * um auf die zugrundeliegende Implementierung zuzugreifen.
     *
     * @return Das hypfvieh BluetoothGattCharacteristic-Objekt
     */
    public com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic getWrappedCharacteristic() {
        return characteristic;
    }

    /**
     * Vergleicht zwei BluetoothGattCharacteristic-Objekte basierend auf ihrer UUID.
     *
     * @param obj Das zu vergleichende Objekt
     * @return true wenn die UUIDs identisch sind, false andernfalls
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BluetoothGattCharacteristic) {
            return this.getUUID().equals(((BluetoothGattCharacteristic) obj).getUUID());
        }
        return false;
    }

    /**
     * Generiert einen Hash-Code basierend auf der Charakteristik-UUID.
     *
     * @return Hash-Code der Charakteristik-UUID
     */
    @Override
    public int hashCode() {
        return getUUID().hashCode();
    }
}