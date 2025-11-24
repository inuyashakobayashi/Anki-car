package de.pdbm.anki.example;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
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

public class AnkiControlExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiControlExample.class);
    private static PrintWriter logWriter;
    private static Vehicle vehicle;
    private static final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();
    private static final List<PositionUpdate> positionUpdates = new ArrayList<>();
    private static final List<TransitionUpdate> transitionUpdates = new ArrayList<>();
    private static int currentLocation = -1;
    private static RoadPiece currentRoadPiece = null;
    private static boolean ascendingLocation = true;
    private static boolean positionListenerActive = false;
    private static boolean transitionListenerActive = false;
    private static int totalNotificationsReceived = 0;

    public static void main(String[] args) {
        System.out.println("===== Anki Overdrive Controller (Hypfvieh Edition) =====");
        initializeLogFile();

        try {
            // 1. 初始化 DBus 连接
            System.out.println("Initializing Bluetooth DBus connection...");
            DeviceManager.createInstance(false); // 使用 System Bus
            DeviceManager manager = DeviceManager.getInstance();

            // 2. 扫描设备
            System.out.println("Scanning for devices (5s)...");
            List<BluetoothDevice> devices = manager.scanForBluetoothDevices(5000);

            // 3. 筛选 Anki 设备
            List<BluetoothDevice> ankiDevices = new ArrayList<>();
            for (BluetoothDevice device : devices) {
                String[] uuids = device.getUuids();
                if (uuids != null) {
                    for (String uuid : uuids) {
                        if (uuid.toLowerCase().contains("beef")) {
                            ankiDevices.add(device);
                            break;
                        }
                    }
                }
            }

            if (ankiDevices.isEmpty()) {
                System.out.println("❌ No Anki vehicles found. Please check if bluetooth is on and vehicle is charged.");
                closeLogFile();
                return;
            }

            // 4. 用户选择
            for (int i = 0; i < ankiDevices.size(); i++) {
                BluetoothDevice d = ankiDevices.get(i);
                System.out.printf("[%d] MAC: %s Name: %s%n", i + 1, d.getAddress(), d.getName());
            }

            Scanner scanner = new Scanner(System.in);
            System.out.print("Select vehicle (number): ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice < 1 || choice > ankiDevices.size()) {
                System.out.println("Invalid selection.");
                return;
            }

            BluetoothDevice selectedDevice = ankiDevices.get(choice - 1);
            System.out.println("Selected: " + selectedDevice.getAddress());

            // 5. 连接并初始化
            System.out.println("Connecting...");
            boolean connected = selectedDevice.connect();
            if (!connected) {
                System.out.println("❌ Connection failed.");
                return;
            }

            // 创建 Vehicle 并初始化
            vehicle = new Vehicle(selectedDevice);
            System.out.println("Initializing vehicle...");
            boolean initSuccess = vehicle.initializeCharacteristics();

            if (!initSuccess) {
                System.out.println("❌ Initialization failed. Retrying...");
                initSuccess = vehicle.initializeCharacteristics();
                if (!initSuccess) {
                    System.out.println("❌ Fatal error: Could not initialize characteristics.");
                    return;
                }
            }

            System.out.println("✓ Ready to race!");
            setupListeners();

            // 6. 进入主菜单
            runMenu(scanner);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeLogFile();
        }
    }

    private static void runMenu(Scanner scanner) {
        boolean exit = false;
        while (!exit) {
            System.out.println("\n===== MENU =====");
            System.out.println("1: Check Status");
            System.out.println("2: Set Speed (300)");
            System.out.println("3: Stop (0)");
            System.out.println("4: Change Lane");
            System.out.println("5: unturn car");
            System.out.println("8: battery level check");
            System.out.println("9: Exit");

            System.out.print("Choice: ");

            int cmd = scanner.nextInt();
            scanner.nextLine();

            switch (cmd) {
                case 1 -> {
                    System.out.println("Connected: " + vehicle.isConnected());
                    System.out.println("On Charger: " + vehicle.isOnCharger());
                    System.out.println("Location: " + currentLocation);
                }
                case 2 -> vehicle.setSpeed(300);
                case 3 -> vehicle.setSpeed(0);
                case 4 -> {
                    System.out.print("Offset (-68 to 68): ");
                    float offset = scanner.nextFloat();
                    vehicle.changeLane(offset);
                }
                case 5 -> {
                    // 建议先给点速度，掉头效果更好
                    if (vehicle.getSpeed() == 0) {
                        System.out.println("先加速到 300...");
                        vehicle.setSpeed(300);
                        try { Thread.sleep(1500); } catch (Exception e) {}
                    }
                    vehicle.uTurn();
                }
                case 9 -> {
                    vehicle.setSpeed(0);
                    vehicle.disconnect();
                    exit = true;
                }
                case 8 -> {
                    vehicle.queryBatteryLevel();
                }
                default -> System.out.println("Invalid command");
            }
        }
    }

    private static void setupListeners() {
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                currentLocation = update.getLocation();
                writeToLog("Pos: " + update.getLocation() + " Piece: " + update.getRoadPiece());
            }
        });

        vehicle.addNotificationListener(new ChargerInfoNotificationListener() {
            @Override
            public void onChargerInfoNotification(ChargerInfoNotification notification) {
                if (notification.isOnCharger()) {
                    System.out.println("⚠️ Vehicle is on charger!");
                }
            }
        });
    }

    private static void initializeLogFile() {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            logWriter = new PrintWriter(new FileWriter("anki_log_" + timestamp + ".txt", true));
        } catch (IOException e) {
            System.err.println("Could not create log file");
        }
    }

    private static void writeToLog(String msg) {
        if (logWriter != null) {
            logWriter.println(LocalDateTime.now() + " " + msg);
            logWriter.flush();
        }
    }

    private static void closeLogFile() {
        if (logWriter != null) logWriter.close();
    }
}