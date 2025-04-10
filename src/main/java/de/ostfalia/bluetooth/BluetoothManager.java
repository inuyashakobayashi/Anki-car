package de.ostfalia.bluetooth;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * 蓝牙管理器类，用于搜索和连接蓝牙设备
 */
public class BluetoothManager {
    // 默认扫描时间（秒）
    private static final int DEFAULT_SCAN_DURATION = 5;

    private DeviceManager deviceManager;
    private BluetoothAdapter adapter;

    /**
     * 初始化蓝牙管理器
     * @return 是否成功初始化
     */
    public boolean initialize() {
        try {
            System.out.println("Bluetooth-Manager wird initialisiert...");

            // 首先创建DeviceManager实例（使用系统模式）
            try {
                deviceManager = DeviceManager.createInstance(false);
            } catch (DBusException e) {
                System.err.println("Fehler beim Erstellen der DeviceManager-Instanz: " + e.getMessage());
                return false;
            }

            // 获取所有蓝牙适配器
            List<BluetoothAdapter> adapters = deviceManager.getAdapters();

            if (adapters.isEmpty()) {
                System.err.println("Kein Bluetooth-Adapter gefunden");
                return false;
            }

            // 使用第一个适配器
            adapter = adapters.get(0);
            System.out.println("Verwende Bluetooth-Adapter: " + adapter.getName() + " [" + adapter.getAddress() + "]");

            // 确保蓝牙适配器已开启
            if (!adapter.isPowered()) {
                System.out.println("Bluetooth-Adapter wird eingeschaltet...");
                adapter.setPowered(true);
                TimeUnit.SECONDS.sleep(2); // 等待适配器启动
            }

            return true;
        } catch (Exception e) {
            System.err.println("Fehler bei der Initialisierung des Bluetooth-Managers: " + e.getMessage());
            return false;
        }
    }

    /**
     * 使用默认时间搜索蓝牙设备
     * @return 搜索到的设备列表
     */
    public List<BluetoothDevice> searchDevices() {
        return searchDevices(DEFAULT_SCAN_DURATION);
    }

    /**
     * 搜索蓝牙设备
     * @param durationSeconds 搜索持续时间（秒）
     * @return 搜索到的设备列表
     */
    public List<BluetoothDevice> searchDevices(int durationSeconds) {
        try {
            System.out.println("\nSuche nach Bluetooth-Geräten...");
            System.out.println("Scanning läuft für " + durationSeconds + " Sekunden...");

            // 开始扫描前先停止任何正在进行的扫描
            if (adapter.isDiscovering()) {
                adapter.stopDiscovery();
                TimeUnit.SECONDS.sleep(1);
            }

            // 设置适配器为可发现模式
            adapter.setDiscoverable(true);

            // 使用DeviceManager的scanForBluetoothDevices方法扫描设备
            List<BluetoothDevice> devices = deviceManager.scanForBluetoothDevices(adapter.getAddress(), durationSeconds * 1000);

            System.out.println("\n" + devices.size() + " Geräte gefunden:");
            System.out.println("---------------------------------------------");
            System.out.printf("%-4s %-25s %-17s\n", "Nr.", "Gerätename", "MAC-Adresse");
            System.out.println("---------------------------------------------");

            for (int i = 0; i < devices.size(); i++) {
                BluetoothDevice device = devices.get(i);
                String name = device.getName();
                if (name == null || name.trim().isEmpty()) {
                    name = "(Unbekanntes Gerät)";
                }
                System.out.printf("%-4d %-25s %-17s\n", (i+1), name, device.getAddress());
            }
            System.out.println("---------------------------------------------");

            return devices;
        } catch (Exception e) {
            System.err.println("Fehler beim Suchen von Geräten: " + e.getMessage());
            return new ArrayList<>(); // 返回空列表而不是null
        }
    }

    /**
     * 连接到指定的蓝牙设备
     * @param device 要连接的设备
     * @return 是否成功连接
     */
    public boolean connectToDevice(BluetoothDevice device) {
        try {
            System.out.println("\nVerbindung mit Gerät wird hergestellt: " + device.getName() + " [" + device.getAddress() + "]");

            // 如果设备已经连接，直接返回成功
            if (device.isConnected()) {
                System.out.println("Gerät ist bereits verbunden");
                return true;
            }

            // 尝试配对（如果尚未配对）
            if (!device.isPaired()) {
                System.out.println("Pairing mit Gerät...");
                device.pair();
                TimeUnit.SECONDS.sleep(2); // 等待配对完成
            }

            // 连接到设备
            boolean connected = device.connect();

            if (connected) {
                System.out.println("\nVerbindung erfolgreich hergestellt");
                printDeviceServices(device);
            } else {
                System.out.println("\nVerbindung konnte nicht hergestellt werden");
            }

            return connected;
        } catch (Exception e) {
            System.err.println("Fehler beim Verbinden mit dem Gerät: " + e.getMessage());
            return false;
        }
    }

    /**
     * 打印设备的服务和特征
     * @param device 蓝牙设备
     */
    private void printDeviceServices(BluetoothDevice device) {
        try {
            List<BluetoothGattService> services = device.getGattServices();

            if (services == null || services.isEmpty()) {
                System.out.println("Gerät bietet keine GATT-Dienste an");
                return;
            }

            System.out.println("\nVerfügbare Dienste des Geräts:");
            System.out.println("---------------------------------------------");

            for (BluetoothGattService service : services) {
                System.out.println("Dienst: " + service.getUuid());

                List<BluetoothGattCharacteristic> characteristics = service.getGattCharacteristics();
                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    System.out.println("  Merkmal: " + characteristic.getUuid());
                }
                System.out.println("---------------------------------------------");
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Abrufen der Gerätedienste: " + e.getMessage());
        }
    }

    /**
     * 断开与设备的连接
     * @param device 要断开连接的设备
     */
    public void disconnectDevice(BluetoothDevice device) {
        try {
            if (device.isConnected()) {
                System.out.println("\nVerbindung mit Gerät wird getrennt: " + device.getName());
                device.disconnect();
                System.out.println("Verbindung getrennt");
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Trennen der Verbindung: " + e.getMessage());
        }
    }

    /**
     * 关闭蓝牙管理器，释放资源
     */
    public void close() {
        try {
            if (deviceManager != null) {
                deviceManager.closeConnection();
                System.out.println("Bluetooth-Verbindung geschlossen");
            }
        } catch (Exception e) {
            System.err.println("Fehler beim Schließen des Bluetooth-Managers: " + e.getMessage());
        }
    }

    /**
     * 主程序示例
     */
    public static void main(String[] args) {
        BluetoothManager manager = new BluetoothManager();
        Scanner scanner = new Scanner(System.in);

        try {
            // 初始化
            if (!manager.initialize()) {
                System.err.println("Initialisierung des Bluetooth-Managers fehlgeschlagen");
                return;
            }

            boolean continueScanning = true;
            List<BluetoothDevice> devices = new ArrayList<>();

            while (continueScanning) {
                // 提示用户输入扫描时间或使用默认值
                System.out.print("\nBitte geben Sie die Scandauer in Sekunden ein, drücken Sie Enter für Standardwert (" + DEFAULT_SCAN_DURATION + " Sekunden): ");
                String input = scanner.nextLine().trim();

                int scanDuration = DEFAULT_SCAN_DURATION;
                if (!input.isEmpty()) {
                    try {
                        scanDuration = Integer.parseInt(input);
                    } catch (NumberFormatException e) {
                        System.out.println("Ungültige Eingabe, verwende Standardwert: " + DEFAULT_SCAN_DURATION + " Sekunden");
                    }
                }

                // 搜索设备
                devices = manager.searchDevices(scanDuration);

                if (devices.isEmpty()) {
                    System.out.println("\nKeine Geräte gefunden. Möchten Sie erneut scannen? (j/n): ");
                    String answer = scanner.nextLine().trim().toLowerCase();
                    continueScanning = answer.startsWith("j");

                    if (!continueScanning) {
                        System.out.println("Scan beendet, Programm wird beendet");
                        return;
                    }
                } else {
                    continueScanning = false; // 找到设备后停止循环
                }
            }

            boolean validSelection = false;
            BluetoothDevice selectedDevice = null;

            while (!validSelection) {
                // 选择设备
                System.out.print("\nBitte wählen Sie die Nummer des Geräts zum Verbinden (1-" + devices.size() + "): ");
                String deviceInput = scanner.nextLine().trim();

                try {
                    int deviceNum = Integer.parseInt(deviceInput);

                    if (deviceNum >= 1 && deviceNum <= devices.size()) {
                        selectedDevice = devices.get(deviceNum - 1);
                        validSelection = true;
                    } else {
                        System.err.println("Ungültige Gerätenummer, bitte erneut eingeben");
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Bitte geben Sie eine gültige Nummer ein");
                }
            }

            // 连接到设备
            boolean connected = manager.connectToDevice(selectedDevice);

            if (connected) {
                System.out.println("\nVerbindung erfolgreich. Drücken Sie Enter, um die Verbindung zu trennen...");
                scanner.nextLine(); // 等待用户按回车键

                // 断开连接
                manager.disconnectDevice(selectedDevice);
            } else {
                System.out.println("\nMöchten Sie erneut versuchen, eine Verbindung herzustellen? (j/n): ");
                String retry = scanner.nextLine().trim().toLowerCase();

                if (retry.startsWith("j")) {
                    System.out.println("Versuche erneut, eine Verbindung herzustellen...");
                    connected = manager.connectToDevice(selectedDevice);

                    if (connected) {
                        System.out.println("\nVerbindung erfolgreich. Drücken Sie Enter, um die Verbindung zu trennen...");
                        scanner.nextLine(); // 等待用户按回车键

                        // 断开连接
                        manager.disconnectDevice(selectedDevice);
                    } else {
                        System.out.println("Verbindung fehlgeschlagen, Programm wird beendet");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler bei der Programmausführung: " + e.getMessage());
            e.printStackTrace();
        } finally {
            manager.close();
            scanner.close();
        }
    }
}