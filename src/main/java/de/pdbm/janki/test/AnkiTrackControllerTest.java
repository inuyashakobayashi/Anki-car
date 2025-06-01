package de.pdbm.janki.test;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothManager;
import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.notifications.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Erweiterte Testklasse für Anki Overdrive Streckenerfassung und Fahrzeugsteuerung.
 *
 * Diese Klasse bietet umfassende Testfunktionen für:
 * - Bluetooth-Verbindung zu Anki-Fahrzeugen
 * - Streckenerfassung und -kartierung
 * - Benachrichtigungssystem-Tests
 * - Fahrzeugsteuerung (Geschwindigkeit, Spurwechsel)
 */
public class AnkiTrackControllerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiTrackControllerTest.class);

    // === Fahrzeug und Verbindung ===
    private static Vehicle vehicle;

    // === Streckeninformationen ===
    private static final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();
    private static final List<PositionUpdate> positionUpdates = new ArrayList<>();
    private static final List<TransitionUpdate> transitionUpdates = new ArrayList<>();

    // === Aktuelle Position ===
    private static int currentLocation = -1;
    private static RoadPiece currentRoadPiece = null;
    private static boolean ascendingLocation = true;

    // === Status-Tracking ===
    private static boolean positionListenerActive = false;
    private static boolean transitionListenerActive = false;
    private static int totalNotificationsReceived = 0;

    /**
     * Hilfsmethode für Thread-Pausen.
     */
    private static void delay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Thread wurde unterbrochen");
        }
    }

    /**
     * Testet das Benachrichtigungssystem durch Fahrzeugbewegungen.
     */
    private static void testNotificationSystem(Scanner scanner) {
        System.out.println("\n===== Benachrichtigungssystem-Test =====");
        System.out.println("Dieser Test überprüft, ob das Benachrichtigungssystem funktioniert");
        System.out.println("Drücken Sie Enter zum Starten...");
        scanner.nextLine();

        int startNotifications = totalNotificationsReceived;

        System.out.println("Setze niedrige Geschwindigkeit und führe Spurwechsel durch...");
        vehicle.setSpeed(200);

        System.out.println("Führe mehrere Spurwechsel durch...");
        for (int i = 0; i < 3; i++) {
            System.out.println("  Spurwechsel " + (i+1) + "/3...");
            vehicle.changeLane(-0.3f);
            delay(1000);
            vehicle.changeLane(0.3f);
            delay(1000);
            vehicle.changeLane(0.0f);
            delay(1000);
        }

        vehicle.setSpeed(0);

        int endNotifications = totalNotificationsReceived;
        int newNotifications = endNotifications - startNotifications;

        System.out.println("\nTestergebnis:");
        System.out.println("Empfangen: " + newNotifications + " neue Benachrichtigungen");

        if (newNotifications > 0) {
            System.out.println("✓ Benachrichtigungssystem funktioniert!");
        } else {
            System.out.println("✗ Keine Benachrichtigungen empfangen. Empfehlungen:");
            System.out.println("1. Fahrzeugbatterie prüfen");
            System.out.println("2. Fahrzeug neu starten");
            System.out.println("3. Korrekte Platzierung auf der Strecke prüfen");
        }
    }

    /**
     * Konfiguriert Event-Listener für Fahrzeugbenachrichtigungen.
     */
    private static void setupListeners() {
        System.out.println("Konfiguriere Event-Listener...");

        // Positionsupdate-Listener
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                positionListenerActive = true;
                totalNotificationsReceived++;
                positionUpdates.add(update);

                // Aktuelle Position aktualisieren
                currentLocation = update.getLocation();
                currentRoadPiece = update.getRoadPiece();
                ascendingLocation = update.isAscendingLocations();

                // Zur Streckenkarte hinzufügen
                trackMap.put(currentLocation, currentRoadPiece);

                System.out.println("\n📍 Positionsupdate #" + positionUpdates.size() + ":");
                System.out.println("  Positions-ID: " + currentLocation);
                System.out.println("  Streckentyp: " + currentRoadPiece);
                System.out.println("  Richtung: " + (ascendingLocation ? "vorwärts" : "rückwärts"));
            }
        });

        // Übergangs-Listener (vereinfacht, da oft redundant)
        vehicle.addNotificationListener(new TransitionUpdateListener() {
            @Override
            public void onTransitionUpdate(TransitionUpdate update) {
                transitionListenerActive = true;
                totalNotificationsReceived++;
                transitionUpdates.add(update);

                // Nur wichtige Übergänge anzeigen (nicht die redundanten)
                if (isSignificantTransition(update)) {
                    System.out.println("\n🔄 Streckenübergang #" + transitionUpdates.size() + ":");
                    System.out.println("  Positions-ID: " + update.getLocation());
                    System.out.println("  Streckentyp: " +
                            (update.getRoadPiece() != null ? update.getRoadPiece() : "Übergang"));
                } else {
                    // Nur in Debug-Modus ausgeben
                    LOGGER.debug("Übergang (gefiltert): ID={}", update.getLocation());
                }
            }
        });

        // Ladegerät-Info-Listener
        vehicle.addNotificationListener(new ChargerInfoNotificationListener() {
            @Override
            public void onChargerInfoNotification(ChargerInfoNotification notification) {
                System.out.println("\n🔋 Ladegerät-Status:");
                System.out.println("  Auf Ladegerät: " + (notification.isOnCharger() ? "Ja" : "Nein"));
            }
        });

        System.out.println("✓ Event-Listener konfiguriert");
    }

    /**
     * Bestimmt, ob ein Übergang signifikant genug ist, um angezeigt zu werden.
     * Filtert redundante/wiederholte Übergänge heraus.
     */
    private static boolean isSignificantTransition(TransitionUpdate update) {
        // Filtern: Nur Übergänge mit tatsächlichen Streckeninformationen oder neue Positionen
        return update.getRoadPiece() != null ||
                (update.getLocation() != 0 && update.getLocation() != currentLocation);
    }

    /**
     * Startet den automatischen Streckenkartierungs-Modus.
     */
    private static void startTrackMapping(Scanner scanner) {
        System.out.println("\n===== Streckenkartierungs-Modus =====");
        System.out.print("Geschwindigkeit für Kartierung eingeben (empfohlen 300-500): ");
        int speed = scanner.nextInt();
        scanner.nextLine(); // Newline konsumieren

        // Vorherige Daten löschen
        trackMap.clear();
        positionUpdates.clear();
        transitionUpdates.clear();
        currentLocation = -1;
        currentRoadPiece = null;

        try {
            // Verbindung neu initialisieren
            System.out.println("Stelle SDK-Modus und Benachrichtigungen sicher...");
            boolean reinitialized = vehicle.initializeCharacteristics();
            System.out.println("Initialisierung: " + (reinitialized ? "✓ Erfolgreich" : "✗ Fehlgeschlagen"));

            // Warten auf Benachrichtigungssystem
            System.out.println("Warte auf Benachrichtigungssystem (5 Sekunden)...");
            for (int i = 0; i < 5; i++) {
                System.out.print(".");
                delay(1000);
            }
            System.out.println(" ✓ Bereit");

            // Streckenkartierung starten
            System.out.println("\n🚗 Streckenkartierung gestartet, Geschwindigkeit: " + speed);
            System.out.println("Drücken Sie Enter zum Stoppen...");

            // Geschwindigkeit setzen
            vehicle.setSpeed(speed);

            // Spurkalibrierung
            System.out.println("Führe Spurkalibrierung durch...");
            vehicle.changeLane(0.0f);
            delay(1000);

            // Warten auf Benutzereingabe
            scanner.nextLine();

            // Stoppen
            vehicle.setSpeed(0);
            System.out.println("🛑 Streckenkartierung gestoppt");

            // Ergebnisse anzeigen
            displayMappingResults();

        } catch (Exception e) {
            System.out.println("✗ Fehler bei Streckenkartierung: " + e.getMessage());
            LOGGER.error("Streckenkartierung fehlgeschlagen", e);
            vehicle.setSpeed(0); // Sicherheitsstop
        }
    }

    /**
     * Zeigt die Ergebnisse der Streckenkartierung an.
     */
    private static void displayMappingResults() {
        System.out.println("\n===== Kartierungsergebnisse =====");
        System.out.println("📊 Gesammelte Streckensegmente: " + trackMap.size());
        System.out.println("📍 Positionsupdates: " + positionUpdates.size());
        System.out.println("🔄 Streckenübergänge: " + transitionUpdates.size());

        if (!trackMap.isEmpty()) {
            System.out.println("\n🗺️ Streckenkarte:");
            List<Integer> sortedLocations = new ArrayList<>(trackMap.keySet());
            Collections.sort(sortedLocations);

            for (Integer location : sortedLocations) {
                RoadPiece piece = trackMap.get(location);
                String icon = getIconForRoadPiece(piece);
                System.out.println("  " + icon + " ID: " + location + " → " + piece);
            }
        } else {
            System.out.println("⚠️ Keine Streckeninformationen gesammelt");
        }
    }

    /**
     * Gibt ein passendes Icon für einen Streckentyp zurück.
     */
    private static String getIconForRoadPiece(RoadPiece piece) {
        if (piece == null) return "❓";

        return switch (piece) {
            case STRAIGHT -> "➡️";
            case CORNER -> "🔄";
            case START -> "🏁";
            case FINISH -> "🏁";
            case INTERSECTION -> "✖️";
            default -> "⭕";
        };
    }

    /**
     * Führt verschiedene Fahrzeugtests durch.
     */
    private static void performSpecialTest(Scanner scanner) {
        System.out.println("\n===== Spezielle Fahrzeugtests =====");
        System.out.println("1: Start-Stopp-Test");
        System.out.println("2: Spurwechsel-Test");
        System.out.println("3: Zurück");
        System.out.print("Test auswählen: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // Newline konsumieren

        switch (choice) {
            case 1 -> emergencyStartStopTest(scanner);
            case 2 -> laneChangeTest(scanner);
            case 3 -> { /* Zurück */ }
            default -> System.out.println("Ungültige Auswahl");
        }
    }

    /**
     * Test für schnelle Start-Stopp-Zyklen.
     */
    private static void emergencyStartStopTest(Scanner scanner) {
        System.out.println("\n===== Start-Stopp-Test =====");
        System.out.println("Testet schnelle Start-Stopp-Zyklen für mehr Positionsupdates");
        System.out.println("Enter drücken zum Starten...");
        scanner.nextLine();

        int beforeCount = positionUpdates.size();
        int cycles = 10;

        try {
            System.out.println("Führe " + cycles + " Start-Stopp-Zyklen durch...");

            for (int i = 0; i < cycles; i++) {
                System.out.println("  Zyklus " + (i+1) + ":");
                System.out.println("    🚀 Start (Geschwindigkeit 500)");
                vehicle.setSpeed(500);
                delay(1000);

                System.out.println("    🛑 Stopp");
                vehicle.setSpeed(0);
                delay(500);
            }

            int afterCount = positionUpdates.size();
            int updateCount = afterCount - beforeCount;

            System.out.println("\n📊 Testergebnis: " + updateCount + " neue Positionsupdates");

        } catch (Exception e) {
            System.out.println("✗ Test fehlgeschlagen: " + e.getMessage());
            vehicle.setSpeed(0);
        }
    }

    /**
     * Test für Spurwechsel-Manöver.
     */
    private static void laneChangeTest(Scanner scanner) {
        System.out.println("\n===== Spurwechsel-Test =====");
        System.out.println("Testet Spurwechsel während der Fahrt");
        System.out.println("Enter drücken zum Starten...");
        scanner.nextLine();

        int beforeCount = positionUpdates.size();

        try {
            System.out.println("🚗 Fahrt starten (Geschwindigkeit 300)");
            vehicle.setSpeed(300);
            delay(2000);

            System.out.println("⬅️ Wechsel zur linken Spur");
            vehicle.changeLane(-0.5f);
            delay(3000);

            System.out.println("➡️ Wechsel zur rechten Spur");
            vehicle.changeLane(0.5f);
            delay(3000);

            System.out.println("⬆️ Zurück zur Mitte");
            vehicle.changeLane(0.0f);
            delay(3000);

            vehicle.setSpeed(0);

            int afterCount = positionUpdates.size();
            int updateCount = afterCount - beforeCount;

            System.out.println("\n📊 Testergebnis: " + updateCount + " neue Positionsupdates");

        } catch (Exception e) {
            System.out.println("✗ Test fehlgeschlagen: " + e.getMessage());
            vehicle.setSpeed(0);
        }
    }

    /**
     * Generiert einen detaillierten Streckenbericht.
     */
    private static void generateTrackReport() {
        System.out.println("\n===== Detaillierter Streckenbericht =====");

        if (trackMap.isEmpty()) {
            System.out.println("⚠️ Keine Streckeninformationen verfügbar");
            return;
        }

        // Streckentyp-Statistiken
        Map<RoadPiece, Integer> pieceTypeCounts = new HashMap<>();
        for (RoadPiece piece : trackMap.values()) {
            pieceTypeCounts.put(piece, pieceTypeCounts.getOrDefault(piece, 0) + 1);
        }

        System.out.println("📊 Streckentyp-Statistiken:");
        for (Map.Entry<RoadPiece, Integer> entry : pieceTypeCounts.entrySet()) {
            String icon = getIconForRoadPiece(entry.getKey());
            System.out.println("  " + icon + " " + entry.getKey() + ": " + entry.getValue() + " Segmente");
        }

        // Streckensequenz
        System.out.println("\n🗺️ Streckensequenz (nach Position sortiert):");
        List<Integer> sortedLocations = new ArrayList<>(trackMap.keySet());
        Collections.sort(sortedLocations);

        for (int i = 0; i < sortedLocations.size(); i++) {
            Integer location = sortedLocations.get(i);
            RoadPiece piece = trackMap.get(location);
            String icon = getIconForRoadPiece(piece);
            System.out.println("  " + (i+1) + ". " + icon + " Position: " + location + " → " + piece);
        }

        // Besondere Streckensegmente
        System.out.println("\n🎯 Besondere Streckensegmente:");
        boolean foundSpecial = false;
        for (Map.Entry<Integer, RoadPiece> entry : trackMap.entrySet()) {
            String special = switch (entry.getValue()) {
                case START -> "🏁 Startlinie";
                case FINISH -> "🏁 Ziellinie";
                case INTERSECTION -> "✖️ Kreuzung";
                default -> null;
            };
            if (special != null) {
                System.out.println("  " + special + ": Position " + entry.getKey());
                foundSpecial = true;
            }
        }
        if (!foundSpecial) {
            System.out.println("  Keine besonderen Segmente gefunden");
        }

        // System-Status
        System.out.println("\n🔧 System-Status:");
        System.out.println("  Positionslistener: " + (positionListenerActive ? "✓ Aktiv" : "✗ Inaktiv"));
        System.out.println("  Übergangslistener: " + (transitionListenerActive ? "✓ Aktiv" : "✗ Inaktiv"));
        System.out.println("  Gesamt-Benachrichtigungen: " + totalNotificationsReceived);
        System.out.println("  Positionsupdates: " + positionUpdates.size());
        System.out.println("  Streckenübergänge: " + transitionUpdates.size());
    }

    // === MAIN-METHODE ===

    public static void main(String[] args) {
        System.out.println("===== Anki Overdrive Streckenerfassungs-Test =====");
        System.out.println("Initialisiere Bluetooth...");

        BluetoothManager manager = BluetoothManager.getBluetoothManager();
        List<BluetoothDevice> devices = manager.getDevices();

        // Anki-Geräte finden
        System.out.println("Suche nach Anki-Fahrzeugen:");
        int index = 1;
        for (BluetoothDevice device : devices) {
            List<String> uuids = device.getUUIDs();
            if (uuids != null) {
                for (String uuid : uuids) {
                    if (uuid.toLowerCase().contains("beef")) {
                        System.out.println(index + ": MAC: " + device.getAddress() +
                                " [Anki-Fahrzeug]");
                        index++;
                        break;
                    }
                }
            }
        }

        if (index == 1) {
            System.out.println("❌ Keine Anki-Fahrzeuge gefunden");
            return;
        }

        // Gerät auswählen
        Scanner scanner = new Scanner(System.in);
        System.out.print("Fahrzeug auswählen (1-" + (index-1) + "): ");
        int choice = scanner.nextInt();
        scanner.nextLine();

        // Gewähltes Gerät finden
        BluetoothDevice selectedDevice = null;
        index = 1;
        for (BluetoothDevice device : devices) {
            List<String> uuids = device.getUUIDs();
            if (uuids != null) {
                for (String uuid : uuids) {
                    if (uuid.toLowerCase().contains("beef")) {
                        if (index == choice) {
                            selectedDevice = device;
                        }
                        index++;
                        break;
                    }
                }
            }
        }

        if (selectedDevice == null) {
            System.out.println("❌ Ungültige Auswahl");
            return;
        }

        System.out.println("🚗 Gewähltes Fahrzeug: " + selectedDevice.getAddress());

        // Verbindung herstellen
        System.out.println("Verbinde...");
        boolean connected = selectedDevice.connect();
        System.out.println("Verbindung: " + (connected ? "✓ Erfolgreich" : "❌ Fehlgeschlagen"));

        if (!connected) {
            return;
        }

        delay(1000);

        // Vehicle-Objekt erstellen
        System.out.println("Erstelle Vehicle-Objekt...");
        vehicle = new Vehicle(selectedDevice);

        // Initialisierung
        System.out.println("Warte auf Initialisierung...");
        for (int i = 0; i < 10; i++) {
            System.out.print(".");
            delay(500);
        }
        System.out.println();

        System.out.println("Initialisiere Fahrzeug-Charakteristiken...");
        boolean initialized = vehicle.initializeCharacteristics();
        System.out.println("Initialisierung: " + (initialized ? "✓ Erfolgreich" : "❌ Fehlgeschlagen"));

        if (!initialized) {
            System.out.println("❌ Kann Fahrzeug nicht initialisieren");
            return;
        }

        // Event-Listener konfigurieren
        setupListeners();

        // Hauptmenü
        boolean exit = false;
        while (!exit) {
            System.out.println("\n===== 🚗 Anki Fahrzeug-Steuerung =====");
            System.out.println("1: 📊 Status prüfen");
            System.out.println("2: 🏃 Geschwindigkeit setzen");
            System.out.println("3: ↔️ Spurwechsel");
            System.out.println("4: 🗺️ Streckenkartierung");
            System.out.println("5: 🧪 Spezielle Tests");
            System.out.println("6: 📋 Streckenbericht");
            System.out.println("7: 🔔 Benachrichtigungstest");
            System.out.println("8: ❌ Beenden");

            System.out.print("Auswahl: ");

            int cmd = scanner.nextInt();
            scanner.nextLine();

            switch (cmd) {
                case 1 -> {
                    // Status prüfen
                    System.out.println("\n📊 Fahrzeug-Status:");
                    System.out.println("  🔗 Verbindung: " + (vehicle.isConnected() ? "✓ Verbunden" : "❌ Getrennt"));
                    System.out.println("  ⚡ Bereit: " + (vehicle.isReadyToStart() ? "✓ Ja" : "❌ Nein"));
                    System.out.println("  🔋 Ladegerät: " + (vehicle.isOnCharger() ? "✓ Ja" : "❌ Nein"));
                    System.out.println("  🏃 Geschwindigkeit: " + vehicle.getSpeed());
                    System.out.println("  📍 Position: " + (currentLocation == -1 ? "Unbekannt" : currentLocation));
                    System.out.println("  🛣️ Streckentyp: " + (currentRoadPiece == null ? "Unbekannt" : currentRoadPiece));
                    System.out.println("  🗺️ Kartierte Segmente: " + trackMap.size());
                    System.out.println("  📊 Benachrichtigungen: " + totalNotificationsReceived);
                }
                case 2 -> {
                    // Geschwindigkeit setzen
                    System.out.print("Geschwindigkeit (0-1000): ");
                    int speed = scanner.nextInt();
                    scanner.nextLine();

                    try {
                        vehicle.setSpeed(speed);
                        System.out.println("✓ Geschwindigkeit gesetzt: " + speed);
                    } catch (Exception e) {
                        System.out.println("❌ Fehler: " + e.getMessage());
                    }
                }
                case 3 -> {
                    // Spurwechsel
                    System.out.print("Spurversatz (-1.0 bis 1.0): ");
                    float offset = scanner.nextFloat();
                    scanner.nextLine();

                    try {
                        vehicle.changeLane(offset);
                        System.out.println("✓ Spurwechsel durchgeführt: " + offset);
                    } catch (Exception e) {
                        System.out.println("❌ Fehler: " + e.getMessage());
                    }
                }
                case 4 -> startTrackMapping(scanner);
                case 5 -> performSpecialTest(scanner);
                case 6 -> generateTrackReport();
                case 7 -> testNotificationSystem(scanner);
                case 8 -> {
                    exit = true;
                    System.out.println("🛑 Programm beendet");
                    vehicle.setSpeed(0);
                }
                default -> System.out.println("❌ Ungültige Auswahl");
            }
        }

        scanner.close();
    }
}