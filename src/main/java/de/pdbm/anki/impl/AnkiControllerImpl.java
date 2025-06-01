package de.pdbm.anki.impl;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothManager;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.notifications.*;
import de.pdbm.anki.api.AnkiController;
import de.pdbm.anki.api.TrackMappingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Konkrete Implementierung des AnkiController-Interfaces.
 *
 * Diese Klasse stellt die vollständige Funktionalität für die Steuerung von
 * Anki Overdrive Fahrzeugen bereit, einschließlich Verbindungsmanagement,
 * Fahrzeugsteuerung und Streckenkartierung.
 */
public class AnkiControllerImpl implements AnkiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiControllerImpl.class);

    /** Anki-Service-UUID für die Geräteerkennung */
    private static final String ANKI_SERVICE_UUID_PATTERN = "beef";

    /** Wartezeit für Verbindungsstabilisierung in Millisekunden */
    private static final int CONNECTION_STABILIZATION_DELAY = 1000;

    // === Bluetooth-Verwaltung ===
    private final BluetoothManager bluetoothManager;
    private BluetoothDevice connectedDevice;
    private Vehicle vehicle;

    // === Streckenkartierung ===
    private final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();
    private int currentLocation = -1;
    private RoadPiece currentRoadPiece = null;
    private boolean isTrackMapping = false;
    private TrackMappingListener mappingListener;

    /**
     * Konstruktor für AnkiControllerImpl.
     * Initialisiert den Bluetooth-Manager für die Gerätekommunikation.
     */
    public AnkiControllerImpl() {
        this.bluetoothManager = BluetoothManager.getBluetoothManager();
        LOGGER.debug("AnkiController-Implementierung initialisiert");
    }

    @Override
    public List<String> scanDevices() {
        LOGGER.debug("Starte Scan nach Anki-Geräten...");

        List<String> ankiDevices = new ArrayList<>();
        List<BluetoothDevice> devices = bluetoothManager.getDevices();

        for (BluetoothDevice device : devices) {
            if (isAnkiDevice(device)) {
                ankiDevices.add(device.getAddress());
                LOGGER.debug("Anki-Gerät gefunden: {}", device.getAddress());
            }
        }

        LOGGER.info("Scan abgeschlossen. {} Anki-Geräte gefunden", ankiDevices.size());
        return ankiDevices;
    }

    @Override
    public boolean connect(String deviceAddress) {
        LOGGER.info("Versuche Verbindung zu Gerät: {}", deviceAddress);

        BluetoothDevice targetDevice = findDeviceByAddress(deviceAddress);
        if (targetDevice == null) {
            LOGGER.warn("Gerät mit Adresse {} nicht gefunden", deviceAddress);
            return false;
        }

        if (!establishBluetoothConnection(targetDevice)) {
            LOGGER.error("Bluetooth-Verbindung zu {} fehlgeschlagen", deviceAddress);
            return false;
        }

        if (!initializeVehicle(targetDevice)) {
            LOGGER.error("Fahrzeug-Initialisierung für {} fehlgeschlagen", deviceAddress);
            cleanup();
            return false;
        }

        LOGGER.info("Erfolgreich verbunden mit Fahrzeug: {}", deviceAddress);
        return true;
    }

    @Override
    public void disconnect() {
        LOGGER.info("Trenne Verbindung zum Fahrzeug...");

        // Fahrzeug stoppen
        if (vehicle != null) {
            try {
                vehicle.setSpeed(0);
                LOGGER.debug("Fahrzeug gestoppt");
            } catch (Exception e) {
                LOGGER.warn("Fehler beim Stoppen des Fahrzeugs: {}", e.getMessage());
            }
        }

        cleanup();
        LOGGER.info("Verbindung getrennt");
    }

    @Override
    public boolean isConnected() {
        return vehicle != null && vehicle.isConnected();
    }

    @Override
    public void setSpeed(int speed) {
        if (!validateVehicleConnection()) {
            return;
        }

        try {
            vehicle.setSpeed(speed);
            LOGGER.debug("Geschwindigkeit gesetzt: {}", speed);
        } catch (Exception e) {
            LOGGER.error("Fehler beim Setzen der Geschwindigkeit: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        LOGGER.debug("Stoppe Fahrzeug");
        setSpeed(0);
    }

    @Override
    public void changeLane(float offset) {
        if (!validateVehicleConnection()) {
            return;
        }

        try {
            vehicle.changeLane(offset);
            LOGGER.debug("Spurwechsel durchgeführt: Offset={}", offset);
        } catch (Exception e) {
            LOGGER.error("Fehler beim Spurwechsel: {}", e.getMessage());
        }
    }

    @Override
    public int getSpeed() {
        return vehicle != null ? vehicle.getSpeed() : 0;
    }

    @Override
    public int getCurrentLocation() {
        return currentLocation;
    }

    @Override
    public RoadPiece getCurrentRoadPiece() {
        return currentRoadPiece;
    }

    @Override
    public boolean isOnCharger() {
        return vehicle != null && vehicle.isOnCharger();
    }

    @Override
    public void startTrackMapping(int speed, TrackMappingListener listener) {
        if (!validateVehicleConnection()) {
            LOGGER.warn("Streckenkartierung kann nicht gestartet werden: Kein verbundenes Fahrzeug");
            return;
        }

        if (listener == null) {
            LOGGER.warn("TrackMappingListener ist null - keine Benachrichtigungen möglich");
        }

        this.isTrackMapping = true;
        this.mappingListener = listener;

        LOGGER.info("Streckenkartierung gestartet mit Geschwindigkeit: {}", speed);
        vehicle.setSpeed(speed);
    }

    @Override
    public void stopTrackMapping() {
        if (isTrackMapping) {
            LOGGER.info("Streckenkartierung gestoppt");
            this.isTrackMapping = false;
            this.mappingListener = null;

            if (vehicle != null) {
                vehicle.setSpeed(0);
            }
        }
    }

    @Override
    public Map<Integer, RoadPiece> getTrackMap() {
        // Defensive Kopie zurückgeben, um interne Datenstruktur zu schützen
        return new HashMap<>(trackMap);
    }

    @Override
    public void clearTrackMap() {
        LOGGER.debug("Streckenkarte wird geleert");
        trackMap.clear();
        currentLocation = -1;
        currentRoadPiece = null;
    }

    // === Private Hilfsmethoden ===

    /**
     * Überprüft, ob ein Bluetooth-Gerät ein Anki-Gerät ist.
     *
     * @param device Das zu überprüfende Gerät
     * @return true wenn es sich um ein Anki-Gerät handelt
     */
    private boolean isAnkiDevice(BluetoothDevice device) {
        List<String> uuids = device.getUUIDs();
        if (uuids == null) {
            return false;
        }

        return uuids.stream()
                .anyMatch(uuid -> uuid.toLowerCase().contains(ANKI_SERVICE_UUID_PATTERN));
    }

    /**
     * Sucht ein Bluetooth-Gerät anhand seiner MAC-Adresse.
     *
     * @param deviceAddress Die gesuchte MAC-Adresse
     * @return Das gefundene Gerät oder null
     */
    private BluetoothDevice findDeviceByAddress(String deviceAddress) {
        List<BluetoothDevice> devices = bluetoothManager.getDevices();

        return devices.stream()
                .filter(device -> device.getAddress().equals(deviceAddress))
                .findFirst()
                .orElse(null);
    }

    /**
     * Stellt eine Bluetooth-Verbindung zum Zielgerät her.
     *
     * @param targetDevice Das Zielgerät
     * @return true wenn die Verbindung erfolgreich war
     */
    private boolean establishBluetoothConnection(BluetoothDevice targetDevice) {
        try {
            boolean connected = targetDevice.connect();
            if (connected) {
                this.connectedDevice = targetDevice;
                LOGGER.debug("Bluetooth-Verbindung hergestellt");
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("Fehler bei Bluetooth-Verbindung: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initialisiert das Fahrzeug-Objekt und richtet Event-Listener ein.
     *
     * @param targetDevice Das verbundene Bluetooth-Gerät
     * @return true wenn die Initialisierung erfolgreich war
     */
    private boolean initializeVehicle(BluetoothDevice targetDevice) {
        try {
            this.vehicle = new Vehicle(targetDevice);

            // Warten auf Verbindungsstabilisierung
            Thread.sleep(CONNECTION_STABILIZATION_DELAY);

            boolean initialized = vehicle.initializeCharacteristics();
            if (initialized) {
                setupEventListeners();
                LOGGER.debug("Fahrzeug erfolgreich initialisiert");
                return true;
            }

            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Thread wurde während Fahrzeug-Initialisierung unterbrochen");
            return false;
        } catch (Exception e) {
            LOGGER.error("Fehler bei Fahrzeug-Initialisierung: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Richtet Event-Listener für Fahrzeugbenachrichtigungen ein.
     */
    private void setupEventListeners() {
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                handlePositionUpdate(update);
            }
        });

        LOGGER.debug("Event-Listener konfiguriert");
    }

    /**
     * Verarbeitet Positionsupdates vom Fahrzeug.
     *
     * @param update Das Positionsupdate
     */
    private void handlePositionUpdate(PositionUpdate update) {
        currentLocation = update.getLocation();
        currentRoadPiece = update.getRoadPiece();

        // Zur Streckenkarte hinzufügen
        trackMap.put(currentLocation, currentRoadPiece);

        LOGGER.debug("Positionsupdate: Location={}, RoadPiece={}", currentLocation, currentRoadPiece);

        // Bei aktiver Streckenkartierung Listener benachrichtigen
        if (isTrackMapping && mappingListener != null) {
            try {
                mappingListener.onTrackPieceDiscovered(currentLocation, currentRoadPiece);
                mappingListener.onLocationUpdate(currentLocation, update.isAscendingLocations());
            } catch (Exception e) {
                LOGGER.error("Fehler beim Benachrichtigen des TrackMappingListeners: {}", e.getMessage());
            }
        }
    }

    /**
     * Validiert, ob eine Fahrzeugverbindung besteht.
     *
     * @return true wenn ein Fahrzeug verbunden ist
     */
    private boolean validateVehicleConnection() {
        if (vehicle == null || !vehicle.isConnected()) {
            LOGGER.warn("Keine aktive Fahrzeugverbindung");
            return false;
        }
        return true;
    }

    /**
     * Räumt Ressourcen auf und setzt interne Zustandsvariablen zurück.
     */
    private void cleanup() {
        // Streckenkartierung stoppen
        if (isTrackMapping) {
            stopTrackMapping();
        }

        // Bluetooth-Verbindung trennen
        if (connectedDevice != null) {
            try {
                connectedDevice.disconnect();
            } catch (Exception e) {
                LOGGER.warn("Fehler beim Trennen der Bluetooth-Verbindung: {}", e.getMessage());
            }
            connectedDevice = null;
        }

        // Fahrzeug-Referenz entfernen
        vehicle = null;

        LOGGER.debug("Cleanup abgeschlossen");
    }
}