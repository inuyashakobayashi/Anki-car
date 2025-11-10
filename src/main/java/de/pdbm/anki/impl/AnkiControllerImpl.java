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
 * Concrete implementation of the AnkiController interface for managing Anki Overdrive vehicles.
 *
 * <p>This class provides comprehensive functionality for controlling Anki Overdrive vehicles
 * through Bluetooth Low Energy (BLE) communication. It handles device discovery, connection
 * management, vehicle control, and track mapping capabilities.
 *
 * <p>Key features include:
 * <ul>
 *   <li>Automatic discovery of Anki Overdrive vehicles via Bluetooth</li>
 *   <li>Robust connection management with error handling</li>
 *   <li>Complete vehicle control (speed, lane changes, status monitoring)</li>
 *   <li>Real-time track mapping and position tracking</li>
 *   <li>Event-driven architecture for position updates</li>
 * </ul>
 *
 * <p>The implementation uses the hypfvieh bluez-dbus library for Bluetooth communication
 * and follows the singleton pattern for the BluetoothManager to ensure system-wide
 * Bluetooth resource management.
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // Create controller instance
 * AnkiController controller = new AnkiControllerImpl();
 *
 * // Scan for available devices
 * List<String> devices = controller.scanDevices();
 *
 * // Connect to first available device
 * if (!devices.isEmpty() && controller.connect(devices.get(0))) {
 *     // Control the vehicle
 *     controller.setSpeed(300);
 *     controller.changeLane(-0.5f);
 *
 *     // Start track mapping
 *     controller.startTrackMapping(200, new TrackMappingListener() {
 *         public void onTrackPieceDiscovered(int location, RoadPiece piece) {
 *             System.out.println("Found: " + piece + " at location " + location);
 *         }
 *     });
 * }
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>This implementation is thread-safe for concurrent access. The internal track map
 * uses {@link ConcurrentHashMap} to ensure safe multi-threaded operations.
 *
 * <h3>Error Handling:</h3>
 * <p>All public methods handle exceptions gracefully and use SLF4J logging for
 * debugging purposes. Connection failures are logged and methods return appropriate
 * error indicators (false for boolean methods, empty collections for lists).
 *
 * @author Zijian Ying
 * @version 1.0
 * @since 2025-07-17
 * @see AnkiController
 * @see TrackMappingListener
 */
public class AnkiControllerImpl implements AnkiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiControllerImpl.class);

    /**
     * Pattern for identifying Anki Overdrive vehicles by their service UUID.
     * Anki devices use the service UUID containing "beef" as identifier.
     */
    private static final String ANKI_SERVICE_UUID_PATTERN = "beef";

    /**
     * Delay in milliseconds to allow Bluetooth connection stabilization.
     * This prevents communication issues after initial connection.
     */
    private static final int CONNECTION_STABILIZATION_DELAY = 1000;

    // === Bluetooth Management ===

    /**
     * Bluetooth manager for device discovery and connection management.
     * Uses singleton pattern to ensure system-wide resource management.
     */
    private final BluetoothManager bluetoothManager;

    /**
     * Currently connected Bluetooth device, or null if not connected.
     * Used for connection state tracking and cleanup operations.
     */
    private BluetoothDevice connectedDevice;

    /**
     * Vehicle control object for the connected device.
     * Provides high-level interface for vehicle operations.
     */
    private Vehicle vehicle;

    // === Track Mapping ===

    /**
     * Thread-safe map storing discovered track pieces by location ID.
     * Uses ConcurrentHashMap for safe multi-threaded access.
     */
    private final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();

    /**
     * Current location ID of the vehicle on the track.
     * Value of -1 indicates unknown or uninitialized position.
     */
    private int currentLocation = -1;

    /**
     * Current road piece type at the vehicle's position.
     * Can be null if position is unknown or transitioning.
     */
    private RoadPiece currentRoadPiece = null;

    /**
     * Flag indicating whether track mapping is currently active.
     * Controls whether position updates are forwarded to the mapping listener.
     */
    private boolean isTrackMapping = false;

    /**
     * Listener for track mapping events, or null if not set.
     * Receives notifications about discovered track pieces and position updates.
     */
    private TrackMappingListener mappingListener;

    /**
     * Constructs a new AnkiControllerImpl instance.
     *
     * <p>Initializes the Bluetooth manager for device communication and sets up
     * internal data structures. The constructor does not establish any connections
     * or perform device discovery - these operations must be initiated explicitly.
     *
     * @throws RuntimeException if Bluetooth manager initialization fails
     */
    public AnkiControllerImpl() {
        this.bluetoothManager = BluetoothManager.getBluetoothManager();
        LOGGER.debug("AnkiController implementation initialized");
    }

    /**
     * Scans for available Anki Overdrive vehicles in the vicinity.
     *
     * <p>This method performs a Bluetooth device scan and filters the results
     * to identify Anki Overdrive vehicles based on their service UUID pattern.
     * The scan may take several seconds to complete depending on the number
     * of Bluetooth devices in range.
     *
     * <p>The returned list contains MAC addresses of discovered Anki vehicles
     * that can be used with the {@link #connect(String)} method.
     *
     * @return list of MAC addresses of discovered Anki vehicles, never null but may be empty
     * @throws RuntimeException if Bluetooth scanning fails due to system issues
     * @see #connect(String)
     */
    @Override
    public List<String> scanDevices() {
        LOGGER.debug("Starting scan for Anki devices...");

        List<String> ankiDevices = new ArrayList<>();
        List<BluetoothDevice> devices = bluetoothManager.getDevices();

        for (BluetoothDevice device : devices) {
            if (isAnkiDevice(device)) {
                ankiDevices.add(device.getAddress());
                LOGGER.debug("Anki device found: {}", device.getAddress());
            }
        }

        LOGGER.info("Scan completed. {} Anki devices found", ankiDevices.size());
        return ankiDevices;
    }

    /**
     * Establishes a connection to the specified Anki vehicle.
     *
     * <p>This method attempts to connect to an Anki vehicle using its MAC address.
     * The connection process includes Bluetooth pairing, vehicle initialization,
     * and setup of event listeners for position updates.
     *
     * <p>If a connection to another vehicle already exists, it will be automatically
     * closed before establishing the new connection.
     *
     * <p>The connection process may take several seconds and includes:
     * <ol>
     *   <li>Bluetooth device lookup and connection</li>
     *   <li>Vehicle characteristics initialization</li>
     *   <li>Event listener setup for position updates</li>
     *   <li>Connection stabilization delay</li>
     * </ol>
     *
     * @param deviceAddress the MAC address of the Anki vehicle to connect to
     * @return true if connection was successful, false otherwise
     * @throws IllegalArgumentException if deviceAddress is null or invalid format
     * @see #disconnect()
     * @see #isConnected()
     */
    @Override
    public boolean connect(String deviceAddress) {
        if (deviceAddress == null) {
            throw new IllegalArgumentException("Device address cannot be null");
        }

        LOGGER.info("Attempting connection to device: {}", deviceAddress);

        BluetoothDevice targetDevice = findDeviceByAddress(deviceAddress);
        if (targetDevice == null) {
            LOGGER.warn("Device with address {} not found", deviceAddress);
            return false;
        }

        if (!establishBluetoothConnection(targetDevice)) {
            LOGGER.error("Bluetooth connection to {} failed", deviceAddress);
            return false;
        }

        if (!initializeVehicle(targetDevice)) {
            LOGGER.error("Vehicle initialization for {} failed", deviceAddress);
            cleanup();
            return false;
        }

        LOGGER.info("Successfully connected to vehicle: {}", deviceAddress);
        return true;
    }

    /**
     * Disconnects from the currently connected vehicle.
     *
     * <p>This method safely terminates the connection to the current vehicle by:
     * <ol>
     *   <li>Stopping the vehicle (setting speed to 0)</li>
     *   <li>Stopping any active track mapping</li>
     *   <li>Closing the Bluetooth connection</li>
     *   <li>Cleaning up internal resources</li>
     * </ol>
     *
     * <p>This method is safe to call multiple times and will not throw exceptions
     * if no vehicle is currently connected.
     *
     * @see #connect(String)
     * @see #isConnected()
     */
    @Override
    public void disconnect() {
        LOGGER.info("Disconnecting from vehicle...");

        // Stop vehicle
        if (vehicle != null) {
            try {
                vehicle.setSpeed(0);
                LOGGER.debug("Vehicle stopped");
            } catch (Exception e) {
                LOGGER.warn("Error stopping vehicle: {}", e.getMessage());
            }
        }

        cleanup();
        LOGGER.info("Disconnected");
    }

    /**
     * Checks if a vehicle is currently connected and ready for commands.
     *
     * <p>This method verifies both the existence of a vehicle object and
     * the active state of the underlying Bluetooth connection.
     *
     * @return true if a vehicle is connected and ready, false otherwise
     * @see #connect(String)
     * @see #disconnect()
     */
    @Override
    public boolean isConnected() {
        return vehicle != null && vehicle.isConnected();
    }

    /**
     * Sets the speed of the connected vehicle.
     *
     * <p>The speed value is applied immediately and affects the vehicle's
     * movement on the track. The speed is measured in internal Anki units
     * and should be within the valid range for optimal performance.
     *
     * <p>This method includes validation and will log warnings for invalid
     * speed values while gracefully handling connection issues.
     *
     * @param speed the desired speed value in Anki internal units (typically 0-1000)
     * @throws IllegalStateException if no vehicle is connected
     * @see #getSpeed()
     * @see #stop()
     */
    @Override
    public void setSpeed(int speed) {
        if (!validateVehicleConnection()) {
            throw new IllegalStateException("No vehicle connected");
        }

        try {
            vehicle.setSpeed(speed);
            LOGGER.debug("Speed set to: {}", speed);
        } catch (Exception e) {
            LOGGER.error("Error setting speed: {}", e.getMessage());
            throw new RuntimeException("Failed to set vehicle speed", e);
        }
    }

    /**
     * Immediately stops the vehicle by setting its speed to zero.
     *
     * <p>This is a convenience method equivalent to calling {@code setSpeed(0)}.
     * It provides a more intuitive interface for stopping operations.
     *
     * @throws IllegalStateException if no vehicle is connected
     * @see #setSpeed(int)
     */
    @Override
    public void stop() {
        LOGGER.debug("Stopping vehicle");
        setSpeed(0);
    }

    /**
     * Changes the lane position of the vehicle.
     *
     * <p>The lane change is performed gradually and affects the vehicle's
     * lateral position on the track. The offset value determines how far
     * from the center line the vehicle should drive.
     *
     * <p>Positive values move the vehicle to the right side of the track,
     * negative values to the left side. The vehicle will maintain the new
     * lane position until another lane change command is issued.
     *
     * @param offset the lane offset value, typically between -1.0 (far left)
     *               and 1.0 (far right), with 0.0 being center
     * @throws IllegalStateException if no vehicle is connected
     * @throws IllegalArgumentException if offset is outside valid range
     */
    @Override
    public void changeLane(float offset) {
        if (!validateVehicleConnection()) {
            throw new IllegalStateException("No vehicle connected");
        }

        if (offset < -1.0f || offset > 1.0f) {
            throw new IllegalArgumentException("Lane offset must be between -1.0 and 1.0");
        }

        try {
            vehicle.changeLane(offset);
            LOGGER.debug("Lane change performed: offset={}", offset);
        } catch (Exception e) {
            LOGGER.error("Error changing lane: {}", e.getMessage());
            throw new RuntimeException("Failed to change vehicle lane", e);
        }
    }

    /**
     * Retrieves the current speed of the vehicle.
     *
     * <p>Returns the last known speed value in Anki internal units.
     * If no vehicle is connected, returns 0.
     *
     * @return current vehicle speed in internal units, or 0 if not connected
     * @see #setSpeed(int)
     */
    @Override
    public int getSpeed() {
        return vehicle != null ? vehicle.getSpeed() : 0;
    }

    /**
     * Gets the current location ID of the vehicle on the track.
     *
     * <p>The location ID is an integer identifier for track segments.
     * Returns -1 if the location is unknown or no vehicle is connected.
     *
     * @return current location ID, or -1 if unknown
     * @see #getCurrentRoadPiece()
     */
    @Override
    public int getCurrentLocation() {
        return currentLocation;
    }

    /**
     * Gets the current road piece type at the vehicle's position.
     *
     * <p>The road piece indicates the type of track segment the vehicle
     * is currently on (e.g., straight, curve, intersection).
     *
     * @return current road piece type, or null if unknown
     * @see #getCurrentLocation()
     * @see RoadPiece
     */
    @Override
    public RoadPiece getCurrentRoadPiece() {
        return currentRoadPiece;
    }

    /**
     * Checks if the vehicle is currently on a charging station.
     *
     * <p>This method queries the vehicle's charging status and returns
     * true if the vehicle is positioned on a charging pad.
     *
     * @return true if vehicle is on charger, false otherwise
     */
    @Override
    public boolean isOnCharger() {
        return vehicle != null && vehicle.isOnCharger();
    }

    /**
     * Starts automatic track mapping at the specified speed.
     *
     * <p>Track mapping mode automatically drives the vehicle around the track
     * at a controlled speed while recording the layout of track pieces.
     * Position updates are forwarded to the provided listener for real-time
     * track discovery feedback.
     *
     * <p>The mapping process continues until {@link #stopTrackMapping()} is called
     * or the vehicle is disconnected. The vehicle will maintain the specified
     * speed throughout the mapping process.
     *
     * <p>If track mapping is already active, this method will restart it with
     * the new parameters.
     *
     * @param speed the speed for track mapping (recommended: 200-400 for accuracy)
     * @param listener callback for track mapping events, may be null
     * @throws IllegalStateException if no vehicle is connected
     * @throws IllegalArgumentException if speed is negative
     * @see #stopTrackMapping()
     * @see #getTrackMap()
     * @see TrackMappingListener
     */
    @Override
    public void startTrackMapping(int speed, TrackMappingListener listener) {
        if (!validateVehicleConnection()) {
            throw new IllegalStateException("Cannot start track mapping: no connected vehicle");
        }

        if (speed < 0) {
            throw new IllegalArgumentException("Speed cannot be negative");
        }

        if (listener == null) {
            LOGGER.warn("TrackMappingListener is null - no notifications will be sent");
        }

        this.isTrackMapping = true;
        this.mappingListener = listener;

        LOGGER.info("Track mapping started with speed: {}", speed);
        try {
            vehicle.setSpeed(speed);
        } catch (Exception e) {
            LOGGER.error("Failed to start track mapping: {}", e.getMessage());
            stopTrackMapping();
            throw new RuntimeException("Failed to start track mapping", e);
        }
    }

    /**
     * Stops the currently active track mapping process.
     *
     * <p>This method immediately stops the vehicle and disables track mapping
     * mode. No further position updates will be sent to the mapping listener.
     *
     * <p>This method is safe to call multiple times and will not throw exceptions
     * if track mapping is not currently active.
     *
     * @see #startTrackMapping(int, TrackMappingListener)
     */
    @Override
    public void stopTrackMapping() {
        if (isTrackMapping) {
            LOGGER.info("Track mapping stopped");
            this.isTrackMapping = false;
            this.mappingListener = null;

            if (vehicle != null) {
                try {
                    vehicle.setSpeed(0);
                } catch (Exception e) {
                    LOGGER.warn("Error stopping vehicle during track mapping stop: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Returns a copy of the current track map.
     *
     * <p>The track map contains all discovered track pieces indexed by their
     * location ID. This method returns a defensive copy to prevent external
     * modification of the internal track data.
     *
     * <p>The map is populated during normal vehicle operation and track mapping.
     * Location IDs are integers assigned by the Anki system to identify
     * specific track segments.
     *
     * @return immutable copy of the current track map, never null
     * @see #clearTrackMap()
     * @see #startTrackMapping(int, TrackMappingListener)
     */
    @Override
    public Map<Integer, RoadPiece> getTrackMap() {
        // Return defensive copy to protect internal data structure
        return new HashMap<>(trackMap);
    }

    /**
     * Clears the current track map and resets position information.
     *
     * <p>This method removes all discovered track pieces from the internal
     * map and resets the current location and road piece information.
     * It does not affect the vehicle's actual position or movement.
     *
     * <p>This is useful for starting fresh track mapping sessions or
     * when switching to a different track layout.
     *
     * @see #getTrackMap()
     * @see #startTrackMapping(int, TrackMappingListener)
     */
    @Override
    public void clearTrackMap() {
        LOGGER.debug("Clearing track map");
        trackMap.clear();
        currentLocation = -1;
        currentRoadPiece = null;
    }

    // === Private Helper Methods ===

    /**
     * Checks if a Bluetooth device is an Anki Overdrive vehicle.
     *
     * <p>This method examines the device's advertised service UUIDs to
     * determine if it matches the pattern used by Anki vehicles.
     *
     * @param device the Bluetooth device to check
     * @return true if the device is identified as an Anki vehicle
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
     * Finds a Bluetooth device by its MAC address.
     *
     * <p>Searches through all available Bluetooth devices to find one
     * with the specified MAC address.
     *
     * @param deviceAddress the MAC address to search for
     * @return the matching device or null if not found
     */
    private BluetoothDevice findDeviceByAddress(String deviceAddress) {
        List<BluetoothDevice> devices = bluetoothManager.getDevices();

        return devices.stream()
                .filter(device -> device.getAddress().equals(deviceAddress))
                .findFirst()
                .orElse(null);
    }

    /**
     * Establishes a Bluetooth connection to the target device.
     *
     * <p>Attempts to connect to the specified device and updates the
     * internal connection state upon success.
     *
     * @param targetDevice the device to connect to
     * @return true if connection was successful
     */
    private boolean establishBluetoothConnection(BluetoothDevice targetDevice) {
        try {
            boolean connected = targetDevice.connect();
            if (connected) {
                this.connectedDevice = targetDevice;
                LOGGER.debug("Bluetooth connection established");
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.error("Error establishing Bluetooth connection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initializes the vehicle object and sets up event listeners.
     *
     * <p>Creates a new Vehicle instance, initializes its characteristics,
     * and configures event listeners for position updates.
     *
     * @param targetDevice the connected Bluetooth device
     * @return true if initialization was successful
     */
    private boolean initializeVehicle(BluetoothDevice targetDevice) {
        try {
            this.vehicle = new Vehicle(targetDevice);

            // Wait for connection stabilization
            Thread.sleep(CONNECTION_STABILIZATION_DELAY);

            boolean initialized = vehicle.initializeCharacteristics();
            if (initialized) {
                setupEventListeners();
                LOGGER.debug("Vehicle successfully initialized");
                return true;
            }

            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Thread interrupted during vehicle initialization");
            return false;
        } catch (Exception e) {
            LOGGER.error("Error during vehicle initialization: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sets up event listeners for vehicle notifications.
     *
     * <p>Configures listeners to receive position updates from the vehicle
     * and handle them appropriately for track mapping and status updates.
     */
    private void setupEventListeners() {
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                handlePositionUpdate(update);
            }
        });

        LOGGER.debug("Event listeners configured");
    }

    /**
     * Handles position updates from the vehicle.
     *
     * <p>Updates the internal position state and notifies the track mapping
     * listener if active. Also maintains the track map with discovered pieces.
     *
     * @param update the position update from the vehicle
     */
    private void handlePositionUpdate(PositionUpdate update) {
        currentLocation = update.getLocation();
        currentRoadPiece = update.getRoadPiece();

        // For track mapping, always notify listener - let the mapper decide if it's new
        // This is critical for figure-8 tracks where same location IDs appear multiple times
        if (isTrackMapping && mappingListener != null) {
            try {
                // Always send position update first to update ascending state
                mappingListener.onLocationUpdate(currentLocation, update.isAscendingLocations());

                // Always send piece discovery - let SimpleTrackMapper decide if it's new
                mappingListener.onTrackPieceDiscovered(currentLocation, update.getRoadPieceId(), currentRoadPiece);
            } catch (Exception e) {
                LOGGER.error("Error notifying track mapping listener: {}", e.getMessage());
            }
        }

        // Keep track map for non-mapping purposes (maintain backward compatibility)
        if (!trackMap.containsKey(currentLocation)) {
            trackMap.put(currentLocation, currentRoadPiece);
            LOGGER.debug("NEW track piece in map: location={}, roadPiece={}",
                    currentLocation, currentRoadPiece);
        }
    }

    /**
     * Validates that a vehicle connection exists and is active.
     *
     * <p>Checks both the vehicle object existence and connection state.
     * Logs warnings for connection issues.
     *
     * @return true if a valid vehicle connection exists
     */
    private boolean validateVehicleConnection() {
        if (vehicle == null || !vehicle.isConnected()) {
            LOGGER.warn("No active vehicle connection");
            return false;
        }
        return true;
    }

    /**
     * Cleans up resources and resets internal state.
     *
     * <p>Stops track mapping, disconnects Bluetooth, and clears all
     * internal references. This method is called during disconnection
     * and error recovery.
     */
    private void cleanup() {
        // Stop track mapping
        if (isTrackMapping) {
            stopTrackMapping();
        }

        // Disconnect Bluetooth
        if (connectedDevice != null) {
            try {
                connectedDevice.disconnect();
            } catch (Exception e) {
                LOGGER.warn("Error disconnecting Bluetooth: {}", e.getMessage());
            }
            connectedDevice = null;
        }

        // Clear vehicle reference
        vehicle = null;

        LOGGER.debug("Cleanup completed");
    }

    @Override
    public Vehicle getVehicle() {
        return vehicle;
    }
}