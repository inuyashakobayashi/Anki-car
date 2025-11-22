package de.pdbm.anki.impl;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
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
 * Concrete implementation of the AnkiController interface.
 */
public class AnkiControllerImpl implements AnkiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiControllerImpl.class);
    private static final String ANKI_SERVICE_UUID_PATTERN = "beef";
    private static final int CONNECTION_STABILIZATION_DELAY = 1000;

    private BluetoothDevice connectedDevice;
    private Vehicle vehicle;

    // Track Mapping state
    private final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();
    private int currentLocation = -1;
    private RoadPiece currentRoadPiece = null;
    private boolean isTrackMapping = false;
    private TrackMappingListener mappingListener;

    public AnkiControllerImpl() {
        // 确保 DBus 连接已创建 (单例模式)
        try {
            if (DeviceManager.getInstance() == null) {
                DeviceManager.createInstance(false);
            }
        } catch (Exception e) {
            // 忽略，可能已经创建过了
        }
        LOGGER.debug("AnkiController implementation initialized");
    }

    @Override
    public List<String> scanDevices() {
        LOGGER.debug("Starting scan for Anki devices...");
        List<String> ankiDevices = new ArrayList<>();

        try {
            // 使用官方 DeviceManager
            DeviceManager manager = DeviceManager.getInstance();
            List<BluetoothDevice> devices = manager.scanForBluetoothDevices(5000);

            for (BluetoothDevice device : devices) {
                if (isAnkiDevice(device)) {
                    ankiDevices.add(device.getAddress());
                    LOGGER.debug("Anki device found: {}", device.getAddress());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Scan failed: {}", e.getMessage());
        }

        LOGGER.info("Scan completed. {} Anki devices found", ankiDevices.size());
        return ankiDevices;
    }

    @Override
    public boolean connect(String deviceAddress) {
        if (deviceAddress == null) throw new IllegalArgumentException("Address cannot be null");

        LOGGER.info("Attempting connection to: {}", deviceAddress);
        BluetoothDevice targetDevice = findDeviceByAddress(deviceAddress);

        if (targetDevice == null) {
            LOGGER.warn("Device {} not found", deviceAddress);
            return false;
        }

        if (!establishBluetoothConnection(targetDevice)) {
            LOGGER.error("Bluetooth connection failed");
            return false;
        }

        if (!initializeVehicle(targetDevice)) {
            LOGGER.error("Vehicle initialization failed");
            cleanup();
            return false;
        }

        LOGGER.info("Connected to: {}", deviceAddress);
        return true;
    }

    @Override
    public void disconnect() {
        LOGGER.info("Disconnecting...");
        if (vehicle != null) {
            try {
                vehicle.setSpeed(0);
            } catch (Exception e) {
                LOGGER.warn("Error stopping vehicle: {}", e.getMessage());
            }
        }
        cleanup();
    }

    @Override
    public boolean isConnected() {
        return vehicle != null && vehicle.isConnected();
    }

    @Override
    public void setSpeed(int speed) {
        if (validateVehicleConnection()) {
            vehicle.setSpeed(speed);
        }
    }

    @Override
    public void stop() {
        setSpeed(0);
    }

    @Override
    public void changeLane(float offset) {
        if (validateVehicleConnection()) {
            vehicle.changeLane(offset);
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
        if (!validateVehicleConnection()) throw new IllegalStateException("No vehicle connected");

        this.isTrackMapping = true;
        this.mappingListener = listener;

        LOGGER.info("Track mapping started at speed: {}", speed);
        vehicle.setSpeed(speed);
    }

    @Override
    public void stopTrackMapping() {
        if (isTrackMapping) {
            LOGGER.info("Track mapping stopped");
            this.isTrackMapping = false;
            this.mappingListener = null;
            if (vehicle != null) vehicle.setSpeed(0);
        }
    }

    @Override
    public Map<Integer, RoadPiece> getTrackMap() {
        return new HashMap<>(trackMap);
    }

    @Override
    public void clearTrackMap() {
        trackMap.clear();
        currentLocation = -1;
        currentRoadPiece = null;
    }

    @Override
    public Vehicle getVehicle() {
        return vehicle;
    }

    // === Private Helpers ===

    private boolean isAnkiDevice(BluetoothDevice device) {
        // 注意：getUuids() 返回 String[]
        String[] uuids = device.getUuids();
        if (uuids == null) return false;

        return Arrays.stream(uuids)
                .anyMatch(uuid -> uuid.toLowerCase().contains(ANKI_SERVICE_UUID_PATTERN));
    }

    private BluetoothDevice findDeviceByAddress(String address) {
        try {
            List<BluetoothDevice> devices = DeviceManager.getInstance().getDevices();
            return devices.stream()
                    .filter(d -> d.getAddress().equals(address))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean establishBluetoothConnection(BluetoothDevice device) {
        try {
            // 官方库 connect() 可能抛异常
            if (!device.isConnected()) {
                return device.connect();
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Connection error: {}", e.getMessage());
            return false;
        }
    }

    private boolean initializeVehicle(BluetoothDevice device) {
        try {
            this.connectedDevice = device;
            this.vehicle = new Vehicle(device);
            Thread.sleep(CONNECTION_STABILIZATION_DELAY);

            boolean initSuccess = vehicle.initializeCharacteristics();
            if (initSuccess) {
                setupEventListeners();
            }
            return initSuccess;
        } catch (Exception e) {
            return false;
        }
    }

    private void setupEventListeners() {
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                handlePositionUpdate(update);
            }
        });
    }

    private void handlePositionUpdate(PositionUpdate update) {
        currentLocation = update.getLocation();
        currentRoadPiece = update.getRoadPiece();

        if (isTrackMapping && mappingListener != null) {
            try {
                mappingListener.onLocationUpdate(currentLocation, update.isAscendingLocations());
                mappingListener.onTrackPieceDiscovered(currentLocation, update.getRoadPieceId(), currentRoadPiece);
            } catch (Exception e) {
                LOGGER.error("Listener error: {}", e.getMessage());
            }
        }

        if (!trackMap.containsKey(currentLocation)) {
            trackMap.put(currentLocation, currentRoadPiece);
        }
    }

    private boolean validateVehicleConnection() {
        return vehicle != null && vehicle.isConnected();
    }

    private void cleanup() {
        if (isTrackMapping) stopTrackMapping();

        if (connectedDevice != null) {
            try {
                connectedDevice.disconnect();
            } catch (Exception e) {
                // ignore
            }
            connectedDevice = null;
        }
        vehicle = null;
    }
}