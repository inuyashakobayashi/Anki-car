package de.ostfalia.ble;

import java.util.List;
import java.util.ArrayList;

/**
 * Adapter-Klasse für GATT-Services der hypfvieh-Bibliothek.
 *
 * Ein GATT-Service gruppiert verwandte Funktionalitäten eines Bluetooth-Geräts.
 * Jeder Service enthält eine oder mehrere Charakteristiken, die spezifische
 * Datenoperationen (Lesen, Schreiben, Benachrichtigungen) bereitstellen.
 */
public class BluetoothGattService {

    /** Das gekapselte GATT-Service-Objekt aus der hypfvieh-Bibliothek */
    private final com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService service;

    /**
     * Konstruktor für BluetoothGattService.
     *
     * @param service Das zu kapselnde hypfvieh BluetoothGattService-Objekt
     */
    public BluetoothGattService(com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService service) {
        this.service = service;
    }

    /**
     * Gibt die eindeutige UUID des GATT-Services zurück.
     *
     * Die UUID identifiziert den Typ des Services (z.B. Anki-Fahrzeug-Service).
     *
     * @return Die Service-UUID in Großbuchstaben
     */
    public String getUUID() {
        return service.getUuid().toUpperCase();
    }

    /**
     * Gibt alle Charakteristiken zurück, die dieser Service bereitstellt.
     *
     * Charakteristiken sind die eigentlichen Datenpunkte eines Services,
     * über die Kommunikation mit dem Bluetooth-Gerät stattfindet.
     *
     * @return Liste aller verfügbaren GATT-Charakteristiken
     */
    public List<BluetoothGattCharacteristic> getCharacteristics() {
        List<BluetoothGattCharacteristic> result = new ArrayList<>();
        List<com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic> characteristics =
                service.getGattCharacteristics();

        if (characteristics != null) {
            for (com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic characteristic : characteristics) {
                result.add(new BluetoothGattCharacteristic(characteristic));
            }
        }

        return result;
    }

    /**
     * Sucht eine spezifische Charakteristik anhand ihrer UUID.
     *
     * Diese Methode ist nützlich, um direkt auf bekannte Charakteristiken
     * zuzugreifen, ohne alle durchlaufen zu müssen.
     *
     * @param uuid Die UUID der gesuchten Charakteristik
     * @return Die entsprechende Charakteristik oder null, falls nicht gefunden
     */
    public BluetoothGattCharacteristic find(String uuid) {
        com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic characteristic =
                service.getGattCharacteristicByUuid(uuid);

        if (characteristic != null) {
            return new BluetoothGattCharacteristic(characteristic);
        }

        return null;
    }

    /**
     * Gibt das gekapselte hypfvieh GATT-Service-Objekt zurück.
     *
     * Diese Methode sollte nur verwendet werden, wenn direkter Zugriff auf die
     * zugrundeliegende Implementierung erforderlich ist.
     *
     * @return Das hypfvieh BluetoothGattService-Objekt
     */
    public com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService getWrappedService() {
        return service;
    }

    /**
     * Vergleicht zwei BluetoothGattService-Objekte basierend auf ihrer UUID.
     *
     * @param obj Das zu vergleichende Objekt
     * @return true wenn die UUIDs identisch sind, false andernfalls
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BluetoothGattService) {
            return this.getUUID().equals(((BluetoothGattService) obj).getUUID());
        }
        return false;
    }

    /**
     * Generiert einen Hash-Code basierend auf der Service-UUID.
     *
     * @return Hash-Code der Service-UUID
     */
    @Override
    public int hashCode() {
        return getUUID().hashCode();
    }
}