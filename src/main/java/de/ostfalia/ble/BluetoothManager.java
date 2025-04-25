package de.ostfalia.ble;

import com.github.hypfvieh.bluetooth.DeviceManager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 适配器类，将com.github.hypfvieh库中的蓝牙管理功能适配到教授代码所期望的接口
 */
public class BluetoothManager {

    private static BluetoothManager instance;
    private DeviceManager deviceManager;

    // 缓存已创建的适配器对象，避免重复创建
    private final Map<String, BluetoothDevice> deviceCache = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，初始化蓝牙管理器
     */
    private BluetoothManager() {
        try {
            deviceManager = DeviceManager.createInstance(false);

            // 设置DBus信号处理，用于通知和事件处理
            setupSignalHandlers();
        } catch (DBusException e) {
            e.printStackTrace();
            System.err.println("初始化蓝牙管理器失败: " + e.getMessage());
        }
    }

    /**
     * 获取BluetoothManager单例
     * @return BluetoothManager实例
     */
    public static BluetoothManager getBluetoothManager() {
        if (instance == null) {
            instance = new BluetoothManager();
        }
        return instance;
    }

    /**
     * 设置D-Bus信号处理器，用于处理蓝牙事件
     */
    private void setupSignalHandlers() {
        // 这里需要实现DBus信号处理，用于处理设备连接状态变化和特性值变化等事件
        // 由于具体实现比较复杂，这里只提供一个框架

        // 1. 设备连接状态变化处理
        // 当设备连接状态变化时，通过适配器对象的notifyConnectionState方法通知Vehicle类

        // 2. 特性值变化处理
        // 当特性值变化时，通过适配器对象的notifyValueChanged方法通知Vehicle类
    }

    /**
     * 获取已发现的蓝牙设备列表
     * @return 蓝牙设备列表
     */
    public List<BluetoothDevice> getDevices() {
        List<BluetoothDevice> result = new ArrayList<>();

        try {
            // 确保蓝牙适配器已开启
            List<com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter> adapters = deviceManager.getAdapters();
            if (!adapters.isEmpty()) {
                com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter adapter = adapters.get(0);

                // 如果适配器未开启，则尝试开启
                if (!adapter.isPowered()) {
                    adapter.setPowered(true);
                    // 等待适配器开启
                    Thread.sleep(2000);
                }

                // 尝试获取已发现的设备
                List<com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice> devices = new ArrayList<>();

                // 如果设备列表为空，尝试启动设备发现
                if (devices.isEmpty()) {
                    // 启动发现过程
                    adapter.startDiscovery();

                    // 等待一段时间，让设备被发现
                    Thread.sleep(5000);

                    // 停止发现
                    adapter.stopDiscovery();

                    // 再次尝试扫描设备
                    devices = deviceManager.scanForBluetoothDevices(adapter.getAddress(), 5000);
                }
                // 将扫描到的设备转换为适配器对象
                for (com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice device : devices) {
                    BluetoothDevice adaptedDevice = getOrCreateDevice(device);
                    result.add(adaptedDevice);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("获取设备列表失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取或创建设备适配器对象
     * @param device hypfvieh库中的设备对象
     * @return 适配后的设备对象
     */
    private BluetoothDevice getOrCreateDevice(com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice device) {
        // 如果缓存中已存在该设备，则直接返回
        if (deviceCache.containsKey(device.getAddress())) {
            return deviceCache.get(device.getAddress());
        }

        // 否则创建新的适配器对象并缓存
        BluetoothDevice adaptedDevice = new BluetoothDevice(device);
        deviceCache.put(device.getAddress(), adaptedDevice);
        return adaptedDevice;
    }

    /**
     * 关闭蓝牙管理器
     */
    public void close() {
        if (deviceManager != null) {
            DBusConnection connection = deviceManager.getDbusConnection();
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}