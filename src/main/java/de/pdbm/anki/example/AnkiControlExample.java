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
 * Comprehensive demonstration application for Anki Overdrive vehicle control.
 *
 * <p>This application serves as a complete example of how to use the Anki Overdrive
 * API for vehicle control, track mapping, and data collection. It provides an
 * interactive console interface with comprehensive logging capabilities.
 *
 * <p>Key features include:
 * <ul>
 *   <li>Interactive menu-driven vehicle control interface</li>
 *   <li>Automatic device discovery and connection management</li>
 *   <li>Real-time position tracking and track mapping</li>
 *   <li>Comprehensive logging system with file output</li>
 *   <li>Various test modes for system validation</li>
 *   <li>Detailed track analysis and reporting</li>
 * </ul>
 *
 * <p>The application implements a dual-output logging system:
 * <ul>
 *   <li><strong>Console output:</strong> User interface and important status messages</li>
 *   <li><strong>File output:</strong> Detailed vehicle activities with timestamps</li>
 * </ul>
 *
 * <p>This separation allows for clean user interaction while maintaining comprehensive
 * data collection for analysis and debugging purposes.
 *
 * <h3>Usage:</h3>
 * <p>Run the application from the command line. The program will:
 * <ol>
 *   <li>Initialize Bluetooth and scan for Anki vehicles</li>
 *   <li>Present a selection menu for available vehicles</li>
 *   <li>Establish connection and initialize vehicle control</li>
 *   <li>Provide an interactive menu for various operations</li>
 * </ol>
 *
 * <h3>Menu Options:</h3>
 * <ul>
 *   <li><strong>Check Status:</strong> Display current vehicle status and connection info</li>
 *   <li><strong>Set Speed:</strong> Control vehicle speed with immediate feedback</li>
 *   <li><strong>Change Lane:</strong> Adjust vehicle lane position</li>
 *   <li><strong>Track Mapping:</strong> Automated track discovery and mapping</li>
 *   <li><strong>Basic Control Demo:</strong> Predefined control sequence demonstration</li>
 *   <li><strong>Special Tests:</strong> Advanced testing procedures</li>
 *   <li><strong>Track Report:</strong> Detailed analysis of discovered track layout</li>
 *   <li><strong>Notification Test:</strong> System validation for event handling</li>
 * </ul>
 *
 * <h3>Thread Safety:</h3>
 * <p>The application uses {@link ConcurrentHashMap} for thread-safe track data storage
 * and proper synchronization for multi-threaded event handling.
 *
 * <h3>Error Handling:</h3>
 * <p>All operations include comprehensive error handling with graceful degradation.
 * Errors are logged to both console and file with appropriate user feedback.
 *
 * <h3>System Requirements:</h3>
 * <ul>
 *   <li>Java 22 or higher</li>
 *   <li>Linux system with BlueZ Bluetooth stack</li>
 *   <li>Anki Overdrive vehicle and track</li>
 *   <li>Bluetooth Low Energy adapter</li>
 * </ul>
 *
 * @author Zijian Ying
 * @version 1.0
 * @since 2025-07-17
 * @see Vehicle
 * @see BluetoothManager
 * @see RoadPiece
 */
public class AnkiControlExample {

    /** Logger instance for application-wide logging. */
    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiControlExample.class);

    // === File Output Configuration ===

    /**
     * Print writer for log file output.
     * Used for writing detailed vehicle activity logs with timestamps.
     */
    private static PrintWriter logWriter;

    /**
     * Base filename for log files.
     * Actual filename includes timestamp for uniqueness.
     */
    private static final String LOG_FILE = "anki_vehicle_log.txt";

    // === Vehicle and Connection Management ===

    /**
     * Currently connected vehicle instance.
     * Null if no vehicle is connected.
     */
    private static Vehicle vehicle;

    // === Track Information Storage ===

    /**
     * Thread-safe map of discovered track pieces indexed by location ID.
     * Uses ConcurrentHashMap for safe concurrent access during event handling.
     */
    private static final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();

    /**
     * List of all position updates received from the vehicle.
     * Used for detailed analysis and statistics.
     */
    private static final List<PositionUpdate> positionUpdates = new ArrayList<>();

    /**
     * List of all track transition events.
     * Includes both significant and filtered transitions.
     */
    private static final List<TransitionUpdate> transitionUpdates = new ArrayList<>();

    // === Current Position Tracking ===

    /**
     * Current location ID of the vehicle.
     * Value of -1 indicates unknown or uninitialized position.
     */
    private static int currentLocation = -1;

    /**
     * Current road piece type at vehicle position.
     * Null if position is unknown or transitioning.
     */
    private static RoadPiece currentRoadPiece = null;

    /**
     * Direction flag indicating track traversal direction.
     * True for ascending location IDs, false for descending.
     */
    private static boolean ascendingLocation = true;

    // === Status Tracking ===

    /**
     * Flag indicating if position update listener is receiving events.
     * Used for system health monitoring.
     */
    private static boolean positionListenerActive = false;

    /**
     * Flag indicating if transition listener is receiving events.
     * Used for system health monitoring.
     */
    private static boolean transitionListenerActive = false;

    /**
     * Counter for total notifications received from vehicle.
     * Used for system performance analysis.
     */
    private static int totalNotificationsReceived = 0;

    /**
     * Initializes the log file with timestamp-based filename.
     *
     * <p>Creates a new log file with the current timestamp in the filename
     * to ensure unique log files for each session. The file header includes
     * session start time and application identification.
     *
     * <p>Log files are created in the current working directory with the
     * format: {@code anki_log_YYYYMMDD_HHMMSS.txt}
     *
     * <p>If file creation fails, an error message is displayed but the
     * application continues to run without file logging.
     *
     * @throws IOException if log file cannot be created (handled internally)
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
     * Writes a message to the log file with timestamp.
     *
     * <p>This method writes messages exclusively to the log file without
     * displaying them on the console. Each message is prefixed with a
     * precise timestamp for chronological analysis.
     *
     * <p>The timestamp format is {@code HH:mm:ss.SSS} for millisecond precision.
     * Messages are immediately flushed to ensure data persistence.
     *
     * @param message the message to write to the log file
     */
    private static void writeToLog(String message) {
        if (logWriter != null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            logWriter.println("[" + timestamp + "] " + message);
            logWriter.flush();
        }
    }

    /**
     * Writes a message to both log file and console.
     *
     * <p>This method is used for important messages that should be visible
     * to the user while also being recorded in the log file. Typically used
     * for status updates and critical information.
     *
     * @param message the message to write to both outputs
     */
    private static void writeToLogAndConsole(String message) {
        writeToLog(message);
        System.out.println(message);
    }

    /**
     * Utility method for thread delays with interruption handling.
     *
     * <p>Provides a safe way to pause execution while properly handling
     * thread interruptions. If interrupted, the thread's interrupt status
     * is restored and a warning is logged.
     *
     * @param milliseconds the delay duration in milliseconds
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
     * Configures event listeners for vehicle notifications.
     *
     * <p>Sets up listeners for different types of vehicle events:
     * <ul>
     *   <li><strong>Position updates:</strong> Tracked and logged for mapping</li>
     *   <li><strong>Transition events:</strong> Filtered and recorded</li>
     *   <li><strong>Charger status:</strong> Displayed and logged</li>
     * </ul>
     *
     * <p>Position updates are used to build the track map and maintain
     * current location information. Transition events are filtered to
     * reduce noise while preserving significant changes.
     *
     * <p>All events are logged to the file with precise timestamps for
     * detailed analysis and debugging.
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
     * Determines if a transition update is significant enough to log.
     *
     * <p>Filters transition events to reduce log noise while preserving
     * important track changes. A transition is considered significant if:
     * <ul>
     *   <li>It includes valid road piece information</li>
     *   <li>The location ID is non-zero and different from current location</li>
     * </ul>
     *
     * @param update the transition update to evaluate
     * @return true if the transition should be logged, false otherwise
     */
    private static boolean isSignificantTransition(TransitionUpdate update) {
        return update.getRoadPiece() != null ||
                (update.getLocation() != 0 && update.getLocation() != currentLocation);
    }

    /**
     * Initiates automatic track mapping at user-specified speed.
     *
     * <p>This method implements automated track discovery by driving the vehicle
     * at a controlled speed while recording position updates. The mapping process
     * includes:
     * <ol>
     *   <li>Data structure initialization and clearing</li>
     *   <li>Vehicle re-initialization for reliable communication</li>
     *   <li>Notification system preparation</li>
     *   <li>Lane calibration for optimal positioning</li>
     *   <li>Continuous mapping until user interruption</li>
     * </ol>
     *
     * <p>The mapping speed should be chosen carefully:
     * <ul>
     *   <li><strong>300-400:</strong> Recommended for accuracy</li>
     *   <li><strong>500+:</strong> Faster but may miss some positions</li>
     *   <li><strong>200-:</strong> Very thorough but slow</li>
     * </ul>
     *
     * <p>All mapping activities are logged to the file with detailed timestamps
     * for analysis and verification.
     *
     * @param scanner input scanner for user interaction
     * @throws RuntimeException if vehicle operations fail during mapping
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
            System.out.println("🛑 Track mapping stopped");
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
     * Displays comprehensive results of the track mapping session.
     *
     * <p>Provides both console summary and detailed file logging of mapping
     * results. The summary includes:
     * <ul>
     *   <li>Number of unique track segments discovered</li>
     *   <li>Total position updates received</li>
     *   <li>Number of track transitions recorded</li>
     *   <li>Complete track map with location IDs</li>
     * </ul>
     *
     * <p>The detailed track map is written to the log file with ASCII
     * representations for better readability in text format.
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
     * Returns appropriate emoji icon for road piece type (console display).
     *
     * <p>Provides visual representation of track pieces for console output.
     * Uses Unicode emoji characters for better visual appeal in the terminal.
     *
     * @param piece the road piece type
     * @return Unicode emoji representing the road piece type
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
     * Returns ASCII icon for road piece type (log file format).
     *
     * <p>Provides text-based representation of track pieces for log files.
     * Uses ASCII characters for better compatibility with text editors
     * and analysis tools.
     *
     * @param piece the road piece type
     * @return ASCII representation of the road piece type
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
     * Performs comprehensive notification system validation.
     *
     * <p>This test verifies that the vehicle's notification system is working
     * correctly by executing a series of controlled movements and monitoring
     * the response. The test includes:
     * <ul>
     *   <li>Baseline notification count recording</li>
     *   <li>Controlled speed and lane change sequence</li>
     *   <li>Notification count analysis</li>
     *   <li>System health assessment</li>
     * </ul>
     *
     * <p>The test is designed to trigger position updates and verify that
     * the event handling system is functioning properly. Results are logged
     * for detailed analysis.
     *
     * @param scanner input scanner for user interaction
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
     * Provides access to specialized vehicle testing procedures.
     *
     * <p>Presents a submenu for advanced testing functions including:
     * <ul>
     *   <li><strong>Start-Stop Test:</strong> Rapid acceleration/deceleration cycles</li>
     *   <li><strong>Lane Change Test:</strong> Continuous lane change operations</li>
     * </ul>
     *
     * <p>These tests are designed for system validation and performance
     * analysis under specific conditions.
     *
     * @param scanner input scanner for user interaction
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
     * Executes rapid start-stop cycles for position update testing.
     *
     * <p>This test performs multiple rapid acceleration and deceleration
     * cycles to maximize position update generation. The test helps verify:
     * <ul>
     *   <li>Position update reliability under changing conditions</li>
     *   <li>System response to rapid speed changes</li>
     *   <li>Event handling performance</li>
     * </ul>
     *
     * <p>The test uses high speed (500) for short durations to trigger
     * maximum position updates while maintaining control.
     *
     * @param scanner input scanner for user interaction
     */
    private static void emergencyStartStopTest(Scanner scanner) {
        System.out.println("\n===== Start-Stop Test =====");
        System.out.println("Test quick start-stop cycles to get more position updates");
        System.out.println(" Test process will be logged to file");
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

                System.out.println("    🚀 Start (speed 500)");
                writeToLog("  Start - speed 500");
                vehicle.setSpeed(500);
                delay(1000);

                System.out.println("    🛑 Stop");
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
     * Executes continuous lane change operations while driving.
     *
     * <p>This test performs lane changes at moderate speed to verify:
     * <ul>
     *   <li>Lane change responsiveness and accuracy</li>
     *   <li>Position tracking during lateral movement</li>
     *   <li>System stability during combined speed and lane operations</li>
     * </ul>
     *
     * <p>The test sequence includes left lane, right lane, and center
     * positioning with appropriate delays for completion.
     *
     * @param scanner input scanner for user interaction
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
            System.out.println("🚗 Start driving (speed 300)");
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
     * Generates comprehensive analysis of discovered track layout.
     *
     * <p>Provides detailed statistical analysis of the track mapping data including:
     * <ul>
     *   <li><strong>Track Type Statistics:</strong> Count of each road piece type</li>
     *   <li><strong>Track Sequence:</strong> Ordered list of track segments by location</li>
     *   <li><strong>Special Segments:</strong> Identification of start, finish, and intersection pieces</li>
     *   <li><strong>System Status:</strong> Health metrics for listeners and notifications</li>
     * </ul>
     *
     * <p>The report uses dual output format with summary information displayed
     * on console and detailed data written to the log file for analysis.
     *
     * <p>If no track data is available, the method provides appropriate feedback
     * and suggestions for data collection.
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
        System.out.println("\n Track Sequence (sorted by location):");
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
     * Demonstrates basic vehicle control operations in sequence.
     *
     * <p>This method provides a predefined demonstration of fundamental
     * vehicle control operations including:
     * <ol>
     *   <li>Speed control with moderate acceleration</li>
     *   <li>Lane change operations (left and return to center)</li>
     *   <li>Controlled deceleration and stop</li>
     * </ol>
     *
     * <p>The demonstration uses safe parameters and includes appropriate
     * delays to show each operation clearly. All actions are logged for
     * verification and analysis.
     *
     * <p>This method is useful for:
     * <ul>
     *   <li>System validation after connection</li>
     *   <li>Demonstration of API capabilities</li>
     *   <li>Basic functionality testing</li>
     * </ul>
     *
     * @param scanner input scanner for user interaction
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
     * Safely closes the log file and writes session termination information.
     *
     * <p>This method properly terminates the logging session by:
     * <ul>
     *   <li>Writing session end timestamp</li>
     *   <li>Adding session termination marker</li>
     *   <li>Flushing and closing the file writer</li>
     *   <li>Providing user feedback about log file completion</li>
     * </ul>
     *
     * <p>The method is safe to call multiple times and handles null
     * writer gracefully.
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

    /**
     * Main application entry point.
     *
     * <p>This method orchestrates the complete application workflow including:
     * <ol>
     *   <li><strong>Initialization:</strong> Bluetooth setup and logging configuration</li>
     *   <li><strong>Device Discovery:</strong> Scanning for available Anki vehicles</li>
     *   <li><strong>Device Selection:</strong> User-guided vehicle selection</li>
     *   <li><strong>Connection:</strong> Bluetooth connection establishment</li>
     *   <li><strong>Vehicle Setup:</strong> Vehicle initialization and event listener configuration</li>
     *   <li><strong>Interactive Mode:</strong> Menu-driven user interface</li>
     *   <li><strong>Cleanup:</strong> Resource cleanup and log file closure</li>
     * </ol>
     *
     * <p>The application provides comprehensive error handling at each stage
     * with appropriate user feedback and graceful degradation.
     *
     * <p>The interactive menu system offers the following operations:
     * <ul>
     *   <li><strong>Status Check:</strong> Current vehicle state and statistics</li>
     *   <li><strong>Speed Control:</strong> Direct speed setting with immediate feedback</li>
     *   <li><strong>Lane Control:</strong> Precise lane positioning</li>
     *   <li><strong>Track Mapping:</strong> Automated track discovery</li>
     *   <li><strong>Control Demo:</strong> Predefined control sequence</li>
     *   <li><strong>Special Tests:</strong> Advanced testing procedures</li>
     *   <li><strong>Track Report:</strong> Detailed track analysis</li>
     *   <li><strong>System Test:</strong> Notification system validation</li>
     * </ul>
     *
     * <p>All user interactions and vehicle activities are logged to a timestamped
     * file for detailed analysis and debugging.
     *
     * <h3>System Requirements:</h3>
     * <ul>
     *   <li>Java 22 or higher</li>
     *   <li>Linux operating system with BlueZ</li>
     *   <li>Bluetooth Low Energy adapter</li>
     *   <li>Anki Overdrive vehicle and track</li>
     * </ul>
     *
     * <h3>Usage:</h3>
     * <pre>java de.pdbm.anki.example.AnkiControlExample</pre>
     *
     * @param args command line arguments (not used)
     * @throws RuntimeException if critical initialization fails
     */
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
            System.out.println("\n===== 🚗 Anki Vehicle Controller =====");
            System.out.println("1:  Check Status");
            System.out.println("2:  Set Speed");
            System.out.println("3:  Change Lane");
            System.out.println("4:  Track Mapping");
            System.out.println("5:  Basic Control Demo");
            System.out.println("6:  Special Tests");
            System.out.println("7:  Track Report");
            System.out.println("8:  Notification Test");
            System.out.println("9: ❌ Exit");
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
                    System.out.println("   Road Type: " + (currentRoadPiece == null ? "Unknown" : currentRoadPiece));
                    System.out.println("   Mapped Segments: " + trackMap.size());
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
                    System.out.println("❌ Invalid selection");
                    writeToLog("Invalid menu selection: " + cmd);
                }
            }
        }

        // Clean up resources
        closeLogFile();
        scanner.close();
    }
}