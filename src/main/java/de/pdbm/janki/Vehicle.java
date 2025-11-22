package de.pdbm.janki;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService;
import de.pdbm.janki.notifications.*;
import org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.Variant;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Class Vehicle represent a Anki Overdrive vehicle.
 * * @author bernd
 */
public class Vehicle {

    public static final Map<Vehicle, Long> vehicles = new ConcurrentHashMap<>();

    private final BluetoothDevice bluetoothDevice; // device representing this vehicle

    private BluetoothGattCharacteristic readCharacteristic;

    private BluetoothGattCharacteristic writeCharacteristic;

    private final Collection<NotificationListener> listeners;

    private int speed;

    private boolean connected;

    private boolean onCharger;

    public Vehicle(BluetoothDevice bluetoothDevice) {
        this.listeners = new ConcurrentLinkedDeque<>();
        this.bluetoothDevice = bluetoothDevice;
        this.addNotificationListener(new DefaultConnectedNotificationListener());
        this.addNotificationListener(new DefaultChargerInfoNotificationListener());
    }

    // 在 Vehicle 类中添加一个方法来强制初始化特性
    public boolean initializeCharacteristics() {
        boolean success = false;
        try {
            // 修复 1: getConnected() -> isConnected()
            if (!Boolean.TRUE.equals(this.bluetoothDevice.isConnected())) {
                this.bluetoothDevice.connect();
                Thread.sleep(1000); // 给连接一些时间
            }

            // 2. 刷新服务列表 (官方库推荐做法)
            this.bluetoothDevice.refreshGattServices();

            // 3. 查找读写特征
            this.writeCharacteristic = AnkiBle.findCharacteristic(this.bluetoothDevice, AnkiBle.ANKI_WRITE_CHARACTERISTIC_UUID);
            this.readCharacteristic = AnkiBle.findCharacteristic(this.bluetoothDevice, AnkiBle.ANKI_READ_CHARACTERISTIC_UUID);

            // 4. 初始化写特征
            if (this.writeCharacteristic != null) {
                // 修复 2: 添加 try-catch 处理 writeValue 的异常
                try {
                    this.writeCharacteristic.writeValue(Message.getSdkMode(), null);
                    Thread.sleep(500);
                } catch (Exception e) {
                    System.err.println("设置 SDK 模式失败: " + e.getMessage());
                }
            }

            // 5. 初始化读特征
            if (this.readCharacteristic != null) {
                this.readCharacteristic.startNotify();
                setupNotificationListener();
            } else {
                System.out.println("读特性初始化失败！");
            }

            success = (this.writeCharacteristic != null && this.readCharacteristic != null);
            System.out.println("特性初始化状态: " + (success ? "成功" : "失败"));

            if (success) {
                try {
                    this.writeCharacteristic.writeValue(Message.getSdkMode(), null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            System.out.println("初始化特性出错: " + e.getMessage());
            e.printStackTrace();
        }
        return success;
    }

    /**
     * Returns a list of all known vehicles.
     */
    public static List<Vehicle> getAllVehicles() {
        return new ArrayList<>(vehicles.keySet());
    }

    /**
     * Returns the vehicle for this MAC address.
     */
    public static Vehicle get(String mac) {
        Set<Vehicle> tmp = vehicles.keySet();
        Optional<Vehicle> any = tmp.stream().filter(v -> v.getMacAddress().equals(mac)).findAny();
        return any.orElse(null);
    }

    /**
     * Sets the speed of this vehicle.
     */
    public void setSpeed(int speed) {
        // 修复 1: getConnected() -> isConnected()
        if (Boolean.TRUE.equals(bluetoothDevice.isConnected())) {
            // 修复 2: 添加 try-catch
            try {
                writeCharacteristic.writeValue(Message.speedMessage((short) speed), null);
                this.speed = speed;
            } catch (Exception e) {
                System.err.println("设置速度失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("not connected");
        }
    }

    public int getSpeed() {
        return speed;
    }

    public void changeLane(float offset) {
        // 修复 1: getConnected() -> isConnected()
        if (Boolean.TRUE.equals(bluetoothDevice.isConnected())) {
            // 修复 2: 添加 try-catch
            try {
                writeCharacteristic.writeValue(Message.setOffsetFromRoadCenter(), null);
                writeCharacteristic.writeValue(Message.changeLaneMessage((short) 1000, (short) 1000, offset), null);
            } catch (Exception e) {
                System.err.println("变道失败: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("not connected");
        }
    }

    /**
     * Returns true, if and only if, the vehicle is connected.
     */
    public boolean isConnected() {
        // 修复 1: getConnected() -> isConnected()
        boolean bluetoothConnected = Boolean.TRUE.equals(bluetoothDevice.isConnected());
        boolean characteristicsReady = writeCharacteristic != null && readCharacteristic != null;
        return bluetoothConnected && characteristicsReady;
    }

    public boolean isOnCharger() {
        return onCharger;
    }

    /**
     * 核心方法：设置 DBus 信号监听器
     */
    private void setupNotificationListener() {
        if (readCharacteristic == null) return;

        AbstractPropertiesChangedHandler handler = new AbstractPropertiesChangedHandler() {
            @Override
            public void handle(PropertiesChanged signal) {
                if (!signal.getPath().equals(readCharacteristic.getDbusPath())) {
                    return;
                }
                Map<String, Variant<?>> properties = signal.getPropertiesChanged();
                if (properties.containsKey("Value")) {
                    try {
                        byte[] data = convertToBytes(properties.get("Value").getValue());
                        onValueNotification(data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        try {
            bluetoothDevice.getDbusConnection().addSigHandler(PropertiesChanged.class, handler);
        } catch (Exception e) {
            System.err.println("注册通知监听器失败: " + e.getMessage());
        }
    }

    private byte[] convertToBytes(Object value) {
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            byte[] bytes = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof Number) {
                    bytes[i] = ((Number) list.get(i)).byteValue();
                }
            }
            return bytes;
        }
        return new byte[0];
    }

    public boolean isReadyToStart() {
        return connected && !onCharger && writeCharacteristic != null && readCharacteristic != null;
    }

    /**
     * Disconnect the vehicle.
     */
    public void disconnect() {
        // 修复 1: getConnected() -> isConnected()
        if (Boolean.TRUE.equals(bluetoothDevice.isConnected())) {
            // 修复 2: 添加 try-catch
            try {
                writeCharacteristic.writeValue(Message.disconnectMessage(), null);
                bluetoothDevice.disconnect();
            } catch (Exception e) {
                System.err.println("断开连接时出错: " + e.getMessage());
            }
        }
    }

    public void addNotificationListener(NotificationListener listener) {
        listeners.add(listener);
    }

    public void removeNotificationListener(NotificationListener listener) {
        listeners.remove(listener);
    }

    public String getMacAddress() {
        return bluetoothDevice.getAddress();
    }

    @Override
    public int hashCode() {
        return bluetoothDevice.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Vehicle && ((Vehicle) obj).bluetoothDevice.equals(this.bluetoothDevice);
    }

    public void onValueNotification(byte[] bytes) {
        try {
            Notification notification = NotificationParser.parse(this, bytes);
            // Java 14+ switch expression, 如果你的 Java 版本较低，可能需要改回传统 switch
            switch (notification) {
                case PositionUpdate pu -> {
                    for (NotificationListener notificationListener : listeners) {
                        if (notificationListener instanceof PositionUpdateListener pul) {
                            pul.onPositionUpdate(pu);
                        }
                    }
                }
                case TransitionUpdate tu -> {
                    for (NotificationListener notificationListener : listeners) {
                        if (notificationListener instanceof TransitionUpdateListener tul) {
                            tul.onTransitionUpdate(tu);
                        }
                    }
                }
                case IntersectionUpdate iu -> {
                    for (NotificationListener notificationListener : listeners) {
                        if (notificationListener instanceof IntersectionUpdateListener iul) {
                            iul.onIntersectionUpdate(iu);
                        }
                    }
                }
                case ChargerInfoNotification cin -> {
                    for (NotificationListener notificationListener : listeners) {
                        if (notificationListener instanceof ChargerInfoNotificationListener cnl) {
                            cnl.onChargerInfoNotification(cin);
                        }
                    }
                }
                default -> {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onConnectedNotification(boolean flag) {
        try {
            ConnectedNotification cn = new ConnectedNotification(this, flag);
            for (NotificationListener notificationListener : listeners) {
                if (notificationListener instanceof ConnectedNotificationListener) {
                    ((ConnectedNotificationListener) notificationListener).onConnectedNotification(cn);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String upperCaseChars(String str) {
        StringBuilder sb = new StringBuilder();
        str.codePoints().filter(Character::isUpperCase).forEach(sb::appendCodePoint);
        return sb.toString();
    }

    private static class AnkiBle {

        private static final String ANKI_SERVICE_UUID = "BE15BEEF-6186-407E-8381-0BD89C4D8DF4";
        private static final String ANKI_READ_CHARACTERISTIC_UUID = "BE15BEE0-6186-407E-8381-0BD89C4D8DF4";
        private static final String ANKI_WRITE_CHARACTERISTIC_UUID = "BE15BEE1-6186-407E-8381-0BD89C4D8DF4";

        private static void init() {
            System.out.println("Initializing JAnki...");
            Runtime.getRuntime().addShutdownHook(new Thread(AnkiBle::disconnectAll));
            AnkiBle.discoverDevices();
            AnkiBle.initializeDevices();
        }

        /**
         * 查找指定的特征值 (带详细调试日志)
         */
        static BluetoothGattCharacteristic findCharacteristic(BluetoothDevice device, String uuid) {
            System.out.println("正在查找服务: " + ANKI_SERVICE_UUID);

            // 1. 尝试刷新并获取服务
            device.refreshGattServices();
            List<BluetoothGattService> services = device.getGattServices();

            if (services.isEmpty()) {
                System.out.println("⚠️ 警告: 未找到任何 GATT 服务! (服务列表为空)");
                // 再试一次刷新
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
                device.refreshGattServices();
                services = device.getGattServices();
            }

            // 打印所有找到的服务，方便调试
            System.out.println("找到 " + services.size() + " 个服务:");
            BluetoothGattService targetService = null;

            for (BluetoothGattService s : services) {
                String sUuid = s.getUuid();
                System.out.println(" - 服务 UUID: " + sUuid);
                if (sUuid.equalsIgnoreCase(ANKI_SERVICE_UUID)) {
                    targetService = s;
                }
            }

            if (targetService == null) {
                System.out.println("❌ 未找到 Anki 主服务!");
                return null;
            }

            System.out.println("✓ 找到 Anki 服务，正在查找特征值: " + uuid);

            // 2. 在服务中找特征值
            targetService.refreshGattCharacteristics();
            List<BluetoothGattCharacteristic> chars = targetService.getGattCharacteristics();
            System.out.println("  服务内有 " + chars.size() + " 个特征值:");

            for (BluetoothGattCharacteristic c : chars) {
                System.out.println("  - 特征值 UUID: " + c.getUuid());
                if (c.getUuid().equalsIgnoreCase(uuid)) {
                    return c;
                }
            }

            System.out.println("❌ 未找到目标特征值!");
            return null;
        }

        static void disconnectAll() {
            System.out.println("Disconnecting all devices...");
            Vehicle.vehicles.entrySet().parallelStream().forEach(entry -> entry.getKey().disconnect());
        }

        public static void discoverDevices() {
            try {
                DeviceManager manager = DeviceManager.getInstance();
                List<BluetoothDevice> list = manager.scanForBluetoothDevices(5000);

                for (BluetoothDevice device : list) {
                    String[] uuids = device.getUuids();
                    if (uuids == null) continue;

                    boolean isAnki = Arrays.asList(uuids).contains(ANKI_SERVICE_UUID.toLowerCase());

                    if (isAnki) {
                        String addr = device.getAddress();
                        boolean exists = Vehicle.getAllVehicles().stream()
                                .anyMatch(v -> v.getMacAddress().equals(addr));

                        if (exists) {
                            Vehicle.vehicles.replace(Vehicle.get(addr), System.nanoTime());
                        } else {
                            Vehicle vehicle = new Vehicle(device);
                            Vehicle.vehicles.put(vehicle, System.nanoTime());
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void initializeDevices() {
            try {
                for (Vehicle vehicle : Vehicle.vehicles.keySet()) {
                    if (!vehicle.connected) {
                        vehicle.initializeCharacteristics();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class DefaultConnectedNotificationListener implements ConnectedNotificationListener {
        @Override
        public void onConnectedNotification(ConnectedNotification connectedNotification) {
            Vehicle.this.connected = connectedNotification.isConnected();
        }
    }

    private class DefaultChargerInfoNotificationListener implements ChargerInfoNotificationListener {
        @Override
        public void onChargerInfoNotification(ChargerInfoNotification chargerInfoNotification) {
            Vehicle.this.onCharger = chargerInfoNotification.isOnCharger();
        }
    }
}