package de.pdbm.anki.example;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothManager;
import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.notifications.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive Anki Overdrive controller example
 * Integrates device connection, track mapping, vehicle control and other complete functions
 * Modified version: Separate log output to file
 */
public class AnkiControlExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiControlExample.class);

    // === File Output ===
    private static PrintWriter logWriter;
    private static final String LOG_FILE = "anki_vehicle_log.txt";

    // === Vehicle and Connection ===
    private static Vehicle vehicle;

    // === Track Information ===
    private static final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();
    private static final List<PositionUpdate> positionUpdates = new ArrayList<>();
    private static final List<TransitionUpdate> transitionUpdates = new ArrayList<>();

    // === Current Position ===
    private static int currentLocation = -1;
    private static RoadPiece currentRoadPiece = null;
    private static boolean ascendingLocation = true;

    // === Status Tracking ===
    private static boolean positionListenerActive = false;
    private static boolean transitionListenerActive = false;
    private static int totalNotificationsReceived = 0;

    /**
     * Initialize log file
     */
    private static void initializeLogFile() {
        try {
            // Create log file with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String logFileName = "anki_log_" + timestamp + ".txt";

            logWriter = new PrintWriter(new FileWriter(logFileName, true));

            // Write file header
            logWriter.println("===== Anki Overdrive Vehicle Log =====");
            logWriter.println("Start time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            logWriter.println("==========================================");
            logWriter.flush();

            // Only display log file info on console
            System.out.println(" Log file created: " + logFileName);

        } catch (IOException e) {
            System.err.println("❌ Unable to create log file: " + e.getMessage());
        }
    }

    /**
     * Write to log file (not displayed on console)
     */
    private static void writeToLog(String message) {
        if (logWriter != null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            logWriter.println("[" + timestamp + "] " + message);
            logWriter.flush();
        }
    }

    /**
     * Write to log file and display on console (for important information)
     */
    private static void writeToLogAndConsole(String message) {
        writeToLog(message);
        System.out.println(message);
    }

    /**
     * Thread delay helper method
     */
    private static void delay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Thread interrupted");
        }
    }

    /**
     * Configure vehicle event listeners
     */
    private static void setupListeners() {
        System.out.println("Configuring event listeners...");

        // Position update listener - only write to log file
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                positionListenerActive = true;
                totalNotificationsReceived++;
                positionUpdates.add(update);

                // Update current position
                currentLocation = update.getLocation();
                currentRoadPiece = update.getRoadPiece();
                ascendingLocation = update.isAscendingLocations();

                // Add to track map
                trackMap.put(currentLocation, currentRoadPiece);

                // Only write to log file, don't display on console
                writeToLog("POSITION UPDATE #" + positionUpdates.size() + ":");
                writeToLog("  Location ID: " + currentLocation);
                writeToLog("  Road Type: " + currentRoadPiece);
                writeToLog("  Direction: " + (ascendingLocation ? "Forward" : "Reverse"));
                writeToLog(""); // Empty line separator
            }
        });

        // Transition listener - only write to log file
        vehicle.addNotificationListener(new TransitionUpdateListener() {
            @Override
            public void onTransitionUpdate(TransitionUpdate update) {
                transitionListenerActive = true;
                totalNotificationsReceived++;
                transitionUpdates.add(update);

                // Only show important transitions, and only write to log file
                if (isSignificantTransition(update)) {
                    writeToLog("TRACK TRANSITION #" + transitionUpdates.size() + ":");
                    writeToLog("  Location ID: " + update.getLocation());
                    writeToLog("  Road Type: " +
                            (update.getRoadPiece() != null ? update.getRoadPiece() : "Transitioning"));
                    writeToLog(""); // Empty line separator
                } else {
                    writeToLog("Transition (filtered): ID=" + update.getLocation());
                }
            }
        });

        // Charger info listener - write to log file and console
        vehicle.addNotificationListener(new ChargerInfoNotificationListener() {
            @Override
            public void onChargerInfoNotification(ChargerInfoNotification notification) {
                String message = " Charger Status: " + (notification.isOnCharger() ? "On charger" : "Not on charger");
                writeToLogAndConsole(message);
            }
        });

        System.out.println("✓ Event listeners configured");
        writeToLog("Event listeners configured successfully");
    }

    /**
     * Determine if transition is significant, filter redundant transitions
     */
    private static boolean isSignificantTransition(TransitionUpdate update) {
        return update.getRoadPiece() != null ||
                (update.getLocation() != 0 && update.getLocation() != currentLocation);
    }

    /**
     * Start track mapping mode
     */
    private static void startTrackMapping(Scanner scanner) {
        System.out.println("\n===== Track Mapping Mode =====");
        System.out.print("Enter mapping speed (recommended 300-500): ");
        int speed = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        // Clear previous data
        trackMap.clear();
        positionUpdates.clear();
        transitionUpdates.clear();
        currentLocation = -1;
        currentRoadPiece = null;

        writeToLog("===== TRACK MAPPING START =====");
        writeToLog("Mapping speed: " + speed);

        try {
            // Re-initialize connection
            System.out.println("Ensuring SDK mode and notification settings...");
            boolean reinitialized = vehicle.initializeCharacteristics();
            System.out.println("Initialization: " + (reinitialized ? "✓ Success" : "✗ Failed"));
            writeToLog("Re-initialization: " + (reinitialized ? "Success" : "Failed"));

            // Wait for notification system
            System.out.println("Waiting for notification system ready (5 seconds)...");
            for (int i = 0; i < 5; i++) {
                System.out.print(".");
                delay(1000);
            }
            System.out.println(" ✓ Ready");

            // Start track mapping
            System.out.println("\n Track mapping started, speed: " + speed);
            System.out.println(" Position updates are being logged to file...");
            System.out.println("Press Enter to stop...");
            writeToLog("Track mapping started, speed: " + speed);

            // Set speed
            vehicle.setSpeed(speed);

            // Lane calibration
            System.out.println("Performing lane calibration...");
            vehicle.changeLane(300f);
            delay(1000);
            vehicle.changeLane(-300f);
            delay(1000);

            // Wait for user input
            scanner.nextLine();

            // Stop
            vehicle.setSpeed(0);
            System.out.println(" Track mapping stopped");
            writeToLog("Track mapping stopped");

            // Display results
            displayMappingResults();

        } catch (Exception e) {
            String errorMsg = "✗ Track mapping error: " + e.getMessage();
            System.out.println(errorMsg);
            writeToLog("ERROR: " + errorMsg);
            LOGGER.error("Track mapping failed", e);
            vehicle.setSpeed(0); // Safe stop
        }
    }

    /**
     * Display mapping results
     */
    private static void displayMappingResults() {
        System.out.println("\n===== Mapping Results =====");
        System.out.println(" Collected track segments: " + trackMap.size());
        System.out.println(" Position updates: " + positionUpdates.size());
        System.out.println(" Track transitions: " + transitionUpdates.size());
        System.out.println(" Detailed information saved to log file");

        // Write summary information to log file
        writeToLog("===== MAPPING RESULTS SUMMARY =====");
        writeToLog("Collected track segments: " + trackMap.size());
        writeToLog("Total position updates: " + positionUpdates.size());
        writeToLog("Total track transitions: " + transitionUpdates.size());

        if (!trackMap.isEmpty()) {
            writeToLog("Track Map:");
            List<Integer> sortedLocations = new ArrayList<>(trackMap.keySet());
            Collections.sort(sortedLocations);

            for (Integer location : sortedLocations) {
                RoadPiece piece = trackMap.get(location);
                String icon = getAsciiIconForRoadPiece(piece);
                writeToLog("  " + icon + " ID: " + location + " -> " + piece);
            }
        } else {
            writeToLog("No track information collected");
        }
        writeToLog("====================================");
    }

    /**
     * Return appropriate icon for road type (console version)
     */
    private static String getIconForRoadPiece(RoadPiece piece) {
        if (piece == null) return "❓";

        return switch (piece) {
            case STRAIGHT -> "➡️";
            case CORNER -> "C";
            case START -> "S";
            case FINISH -> "F";
            case INTERSECTION -> "✖️";
            default -> "⭕";
        };
    }

    /**
     * Return ASCII icon for road type (log file version)
     */
    private static String getAsciiIconForRoadPiece(RoadPiece piece) {
        if (piece == null) return "[?]";

        return switch (piece) {
            case STRAIGHT -> "[->]";
            case CORNER -> "[C]";
            case START -> "[S]";
            case FINISH -> "[F]";
            case INTERSECTION -> "[X]";
            default -> "[O]";
        };
    }

    /**
     * Test notification system
     */
    private static void testNotificationSystem(Scanner scanner) {
        System.out.println("\n===== Notification System Test =====");
        System.out.println("This test checks if the notification system is working properly");
        System.out.println(" Test results will be logged to file");
        System.out.println("Press Enter to start...");
        scanner.nextLine();

        int startNotifications = totalNotificationsReceived;
        writeToLog("===== NOTIFICATION SYSTEM TEST START =====");

        System.out.println("Setting low speed and performing lane changes...");
        vehicle.setSpeed(200);
        writeToLog("Set speed: 200");

        System.out.println("Performing multiple lane changes...");
        for (int i = 0; i < 3; i++) {
            System.out.println("  Lane change " + (i+1) + "/3...");
            writeToLog("Lane change " + (i+1) + "/3");

            vehicle.changeLane(-300f);
            writeToLog("  → Left lane (-300mm)");
            delay(1000);

            vehicle.changeLane(300f);
            writeToLog("  → Right lane (300mm)");
            delay(1000);

            vehicle.changeLane(0.0f);
            writeToLog("  → Center (0.0)");
            delay(1000);
        }

        vehicle.setSpeed(0);
        writeToLog("Stop vehicle");

        int endNotifications = totalNotificationsReceived;
        int newNotifications = endNotifications - startNotifications;

        System.out.println("\nTest results:");
        System.out.println("Received: " + newNotifications + " new notifications");

        writeToLog("Test result: Received " + newNotifications + " new notifications");

        if (newNotifications > 0) {
            System.out.println("✓ Notification system working properly!");
            writeToLog("✓ Notification system working properly");
        } else {
            System.out.println("✗ No notifications received. Suggestions:");
            System.out.println("1. Check vehicle battery");
            System.out.println("2. Restart vehicle");
            System.out.println("3. Check vehicle placement on track");

            writeToLog("✗ No notifications received - Possible issues:");
            writeToLog("1. Vehicle battery low");
            writeToLog("2. Vehicle needs restart");
            writeToLog("3. Vehicle position incorrect");
        }

        writeToLog("===== NOTIFICATION SYSTEM TEST END =====");
    }

    /**
     * Perform special vehicle tests
     */
    private static void performSpecialTest(Scanner scanner) {
        System.out.println("\n===== Special Vehicle Tests =====");
        System.out.println("1: Start-Stop Test");
        System.out.println("2: Lane Change Test");
        System.out.println("3: Return");
        System.out.print("Select test: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        switch (choice) {
            case 1 -> emergencyStartStopTest(scanner);
            case 2 -> laneChangeTest(scanner);
            case 3 -> { /* Return */ }
            default -> System.out.println("Invalid selection");
        }
    }

    /**
     * Quick start-stop cycle test
     */
    private static void emergencyStartStopTest(Scanner scanner) {
        System.out.println("\n===== Start-Stop Test =====");
        System.out.println("Test quick start-stop cycles to get more position updates");
        System.out.println("📝 Test process will be logged to file");
        System.out.println("Press Enter to start...");
        scanner.nextLine();

        int beforeCount = positionUpdates.size();
        int cycles = 10;

        writeToLog("===== START-STOP TEST START =====");
        writeToLog("Test cycle count: " + cycles);

        try {
            System.out.println("Executing " + cycles + " start-stop cycles...");

            for (int i = 0; i < cycles; i++) {
                System.out.println("  Cycle " + (i+1) + "/" + cycles);
                writeToLog("Cycle " + (i+1) + "/" + cycles + ":");

                System.out.println("     Start (speed 500)");
                writeToLog("  Start - speed 500");
                vehicle.setSpeed(500);
                delay(1000);

                System.out.println("     Stop");
                writeToLog("  Stop");
                vehicle.setSpeed(0);
                delay(500);
            }

            int afterCount = positionUpdates.size();
            int updateCount = afterCount - beforeCount;

            System.out.println("\n📊 Test result: " + updateCount + " new position updates");
            writeToLog("Test completed - new position updates: " + updateCount);
            writeToLog("===== START-STOP TEST END =====");

        } catch (Exception e) {
            String errorMsg = "✗ Test failed: " + e.getMessage();
            System.out.println(errorMsg);
            writeToLog("ERROR: " + errorMsg);
            vehicle.setSpeed(0);
        }
    }

    /**
     * Lane change test
     */
    private static void laneChangeTest(Scanner scanner) {
        System.out.println("\n===== Lane Change Test =====");
        System.out.println("Test lane changes while driving");
        System.out.println(" Test process will be logged to file");
        System.out.println("Press Enter to start...");
        scanner.nextLine();

        int beforeCount = positionUpdates.size();
        writeToLog("===== LANE CHANGE TEST START =====");

        try {
            System.out.println(" Start driving (speed 300)");
            writeToLog("Start driving - speed 300");
            vehicle.setSpeed(300);
            delay(2000);

            System.out.println("⬅️ Change to left lane");
            writeToLog("Change to left lane (-0.5)");
            vehicle.changeLane(-0.5f);
            delay(3000);

            System.out.println("➡️ Change to right lane");
            writeToLog("Change to right lane (0.5)");
            vehicle.changeLane(0.5f);
            delay(3000);

            System.out.println("⬆️ Return to center");
            writeToLog("Return to center lane (0.0)");
            vehicle.changeLane(0.0f);
            delay(3000);

            vehicle.setSpeed(0);
            writeToLog("Stop vehicle");

            int afterCount = positionUpdates.size();
            int updateCount = afterCount - beforeCount;

            System.out.println("\n📊 Test result: " + updateCount + " new position updates");
            writeToLog("Test completed - new position updates: " + updateCount);
            writeToLog("===== LANE CHANGE TEST END =====");

        } catch (Exception e) {
            String errorMsg = "✗ Test failed: " + e.getMessage();
            System.out.println(errorMsg);
            writeToLog("ERROR: " + errorMsg);
            vehicle.setSpeed(0);
        }
    }

    /**
     * Generate detailed track report
     */
    private static void generateTrackReport() {
        System.out.println("\n===== Detailed Track Report =====");

        if (trackMap.isEmpty()) {
            System.out.println("⚠️ No track information available");
            return;
        }

        writeToLog("===== DETAILED TRACK REPORT =====");

        // Track type statistics
        Map<RoadPiece, Integer> pieceTypeCounts = new HashMap<>();
        for (RoadPiece piece : trackMap.values()) {
            pieceTypeCounts.put(piece, pieceTypeCounts.getOrDefault(piece, 0) + 1);
        }

        System.out.println("📊 Track Type Statistics:");
        writeToLog("Track Type Statistics:");
        for (Map.Entry<RoadPiece, Integer> entry : pieceTypeCounts.entrySet()) {
            String consoleIcon = getIconForRoadPiece(entry.getKey());
            String logIcon = getAsciiIconForRoadPiece(entry.getKey());
            String consoleLine = "  " + consoleIcon + " " + entry.getKey() + ": " + entry.getValue() + " segments";
            String logLine = "  " + logIcon + " " + entry.getKey() + ": " + entry.getValue() + " segments";
            System.out.println(consoleLine);
            writeToLog(logLine);
        }

        // Track sequence
        System.out.println("\n🗺️ Track Sequence (sorted by location):");
        System.out.println(" Detailed sequence information saved to log file");

        writeToLog("Track Sequence (sorted by location):");
        List<Integer> sortedLocations = new ArrayList<>(trackMap.keySet());
        Collections.sort(sortedLocations);

        for (int i = 0; i < sortedLocations.size(); i++) {
            Integer location = sortedLocations.get(i);
            RoadPiece piece = trackMap.get(location);
            String icon = getAsciiIconForRoadPiece(piece);
            writeToLog("  " + (i+1) + ". " + icon + " Location: " + location + " -> " + piece);
        }

        // Special track segments
        System.out.println("\n Special Track Segments:");
        writeToLog("Special Track Segments:");
        boolean foundSpecial = false;
        for (Map.Entry<Integer, RoadPiece> entry : trackMap.entrySet()) {
            String consoleSpecial = switch (entry.getValue()) {
                case START -> " Start Line";
                case FINISH -> " Finish Line";
                case INTERSECTION -> "✖️ Intersection";
                default -> null;
            };
            String logSpecial = switch (entry.getValue()) {
                case START -> "[S] Start Line";
                case FINISH -> "[F] Finish Line";
                case INTERSECTION -> "[X] Intersection";
                default -> null;
            };
            if (consoleSpecial != null && logSpecial != null) {
                String consoleLine = "  " + consoleSpecial + ": Location " + entry.getKey();
                String logLine = "  " + logSpecial + ": Location " + entry.getKey();
                System.out.println(consoleLine);
                writeToLog(logLine);
                foundSpecial = true;
            }
        }
        if (!foundSpecial) {
            System.out.println("  No special segments found");
            writeToLog("  No special segments found");
        }

        // System status
        System.out.println("\n System Status:");
        System.out.println("  Position Listener: " + (positionListenerActive ? "✓ Active" : "✗ Inactive"));
        System.out.println("  Transition Listener: " + (transitionListenerActive ? "✓ Active" : "✗ Inactive"));
        System.out.println("  Total Notifications: " + totalNotificationsReceived);
        System.out.println("  Position Updates: " + positionUpdates.size());
        System.out.println("  Track Transitions: " + transitionUpdates.size());

        writeToLog("System Status:");
        writeToLog("  Position Listener: " + (positionListenerActive ? "Active" : "Inactive"));
        writeToLog("  Transition Listener: " + (transitionListenerActive ? "Active" : "Inactive"));
        writeToLog("  Total Notifications: " + totalNotificationsReceived);
        writeToLog("  Position Updates: " + positionUpdates.size());
        writeToLog("  Track Transitions: " + transitionUpdates.size());
        writeToLog("===================================");
    }

    /**
     * Basic control demonstration
     */
    private static void demonstrateBasicControl(Scanner scanner) {
        System.out.println("\n=== Basic Control Demo ===");
        System.out.println(" Demo process will be logged to file");
        System.out.println("Press Enter to start demo...");
        scanner.nextLine();

        writeToLog("===== BASIC CONTROL DEMO START =====");

        // Set speed
        System.out.println("Setting speed to 300...");
        writeToLog("Set speed: 300");
        vehicle.setSpeed(300);
        delay(3000);

        // Change lane
        System.out.println("Changing to left lane...");
        writeToLog("Change to left lane (-300)");
        vehicle.changeLane(-300f);
        delay(2000);

        // Return to center
        System.out.println("Returning to center...");
        writeToLog("Return to center (300)");
        vehicle.changeLane(300f);
        delay(2000);

        // Stop
        System.out.println("Stopping...");
        writeToLog("Stop vehicle");
        vehicle.setSpeed(0);
        System.out.println("✓ Basic control demo completed");
        writeToLog("Basic control demo completed");
        writeToLog("===== BASIC CONTROL DEMO END =====");
    }

    /**
     * Close log file
     */
    private static void closeLogFile() {
        if (logWriter != null) {
            writeToLog("Program terminated");
            writeToLog("End time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writeToLog("==========================================");
            logWriter.close();
            System.out.println(" Log file saved");
        }
    }

    // === Main Method ===
    public static void main(String[] args) {
        System.out.println("===== Anki Overdrive Comprehensive Controller =====");

        // Initialize log file
        initializeLogFile();

        System.out.println("Initializing Bluetooth...");
        writeToLog("Program started - Initializing Bluetooth");

        BluetoothManager manager = BluetoothManager.getBluetoothManager();
        List<BluetoothDevice> devices = manager.getDevices();

        // Search for Anki devices
        System.out.println("Searching for Anki vehicles:");
        List<BluetoothDevice> ankiDevices = new ArrayList<>();
        for (BluetoothDevice device : devices) {
            List<String> uuids = device.getUUIDs();
            if (uuids != null) {
                for (String uuid : uuids) {
                    if (uuid.toLowerCase().contains("beef")) {
                        ankiDevices.add(device);
                        System.out.println((ankiDevices.size()) + ": MAC: " + device.getAddress() +
                                " [Anki Vehicle]");
                        writeToLog("Found Anki vehicle: " + device.getAddress());
                        break;
                    }
                }
            }
        }

        if (ankiDevices.isEmpty()) {
            System.out.println("❌ No Anki vehicles found");
            writeToLog("No Anki vehicles found");
            closeLogFile();
            return;
        }

        // Select device
        Scanner scanner = new Scanner(System.in);
        System.out.print("Select vehicle (1-" + ankiDevices.size() + "): ");
        int choice = scanner.nextInt();
        scanner.nextLine();

        if (choice < 1 || choice > ankiDevices.size()) {
            System.out.println("❌ Invalid selection");
            writeToLog("Invalid vehicle selection: " + choice);
            closeLogFile();
            return;
        }

        BluetoothDevice selectedDevice = ankiDevices.get(choice - 1);
        System.out.println(" Selected vehicle: " + selectedDevice.getAddress());
        writeToLog("Selected vehicle: " + selectedDevice.getAddress());

        // Establish connection
        System.out.println("Connecting...");
        boolean connected = selectedDevice.connect();
        System.out.println("Connection: " + (connected ? "✓ Success" : "❌ Failed"));
        writeToLog("Connection status: " + (connected ? "Success" : "Failed"));

        if (!connected) {
            closeLogFile();
            scanner.close();
            return;
        }

        delay(1000);

        // Create Vehicle object
        System.out.println("Creating Vehicle object...");
        vehicle = new Vehicle(selectedDevice);

        // Initialize
        System.out.println("Waiting for initialization...");
        for (int i = 0; i < 10; i++) {
            System.out.print(".");
            delay(500);
        }
        System.out.println();

        System.out.println("Initializing vehicle characteristics...");
        boolean initialized = vehicle.initializeCharacteristics();
        System.out.println("Initialization: " + (initialized ? "✓ Success" : "❌ Failed"));
        writeToLog("Vehicle initialization: " + (initialized ? "Success" : "Failed"));

        if (!initialized) {
            System.out.println("❌ Unable to initialize vehicle");
            writeToLog("Unable to initialize vehicle - Program exit");
            closeLogFile();
            scanner.close();
            return;
        }

        // Configure event listeners
        setupListeners();

        // Main menu
        boolean exit = false;
        while (!exit) {
            System.out.println("\n=====  Anki Vehicle Controller =====");
            System.out.println("1:  Check Status");
            System.out.println("2:  Set Speed");
            System.out.println("3:  Change Lane");
            System.out.println("4:  Track Mapping");
            System.out.println("5:  Basic Control Demo");
            System.out.println("6:  Special Tests");
            System.out.println("7:  Track Report");
            System.out.println("8:  Notification Test");
            System.out.println("9:  Exit");
            System.out.println(" Note: Vehicle activity is being logged to file");

            System.out.print("Choice: ");

            int cmd = scanner.nextInt();
            scanner.nextLine();

            writeToLog("User selected menu item: " + cmd);

            switch (cmd) {
                case 1 -> {
                    // Check status
                    System.out.println("\n Vehicle Status:");
                    System.out.println("   Connection: " + (vehicle.isConnected() ? "✓ Connected" : "❌ Disconnected"));
                    System.out.println("   Ready: " + (vehicle.isReadyToStart() ? "✓ Yes" : "❌ No"));
                    System.out.println("   Charger: " + (vehicle.isOnCharger() ? "✓ Yes" : "❌ No"));
                    System.out.println("   Speed: " + vehicle.getSpeed());
                    System.out.println("   Location: " + (currentLocation == -1 ? "Unknown" : currentLocation));
                    System.out.println("  ️ Road Type: " + (currentRoadPiece == null ? "Unknown" : currentRoadPiece));
                    System.out.println("  ️ Mapped Segments: " + trackMap.size());
                    System.out.println("   Notifications: " + totalNotificationsReceived);

                    // Log status query
                    writeToLog("===== STATUS QUERY =====");
                    writeToLog("Connection status: " + (vehicle.isConnected() ? "Connected" : "Disconnected"));
                    writeToLog("Ready status: " + (vehicle.isReadyToStart() ? "Yes" : "No"));
                    writeToLog("Charger status: " + (vehicle.isOnCharger() ? "On charger" : "Not on charger"));
                    writeToLog("Current speed: " + vehicle.getSpeed());
                    writeToLog("Current location: " + (currentLocation == -1 ? "Unknown" : String.valueOf(currentLocation)));
                    writeToLog("Current road type: " + (currentRoadPiece == null ? "Unknown" : currentRoadPiece.toString()));
                    writeToLog("Mapped track segments: " + trackMap.size());
                    writeToLog("Total notifications: " + totalNotificationsReceived);
                    writeToLog("========================");
                }
                case 2 -> {
                    // Set speed
                    System.out.print("Speed (0-1000): ");
                    int speed = scanner.nextInt();
                    scanner.nextLine();

                    try {
                        vehicle.setSpeed(speed);

                        writeToLog("Set speed: " + speed);
                        // Wait for user input
                        System.out.println("✓ Speed set: " + speed);
                        scanner.nextLine();
                        vehicle.setSpeed(0);
                    } catch (Exception e) {
                        String errorMsg = "❌ Speed setting error: " + e.getMessage();
                        System.out.println(errorMsg);
                        writeToLog("ERROR - " + errorMsg);
                    }
                }
                case 3 -> {
                    // Lane change
                    System.out.print("Lane offset (-400mm to 400mm): ");
                    float offset = scanner.nextFloat();
                    scanner.nextLine();

                    try {
                        vehicle.changeLane(offset);
                        System.out.println("✓ Lane change completed: " + offset);
                        writeToLog("Lane change: " + offset);
                    } catch (Exception e) {
                        String errorMsg = "❌ Lane change error: " + e.getMessage();
                        System.out.println(errorMsg);
                        writeToLog("ERROR - " + errorMsg);
                    }
                }
                case 4 -> startTrackMapping(scanner);
                case 5 -> demonstrateBasicControl(scanner);
                case 6 -> performSpecialTest(scanner);
                case 7 -> generateTrackReport();
                case 8 -> testNotificationSystem(scanner);
                case 9 -> {
                    exit = true;
                    System.out.println(" Program exit");
                    vehicle.setSpeed(0);
                    writeToLog("Program normal exit");
                }
                default -> {
                    System.out.println(" Invalid selection");
                    writeToLog("Invalid menu selection: " + cmd);
                }
            }
        }

        // Clean up resources
        closeLogFile();
        scanner.close();
    }
}