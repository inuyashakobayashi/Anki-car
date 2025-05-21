package de.ostfalia.ble;

import com.github.hypfvieh.bluetooth.DeviceManager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.interfaces.Properties.PropertiesChanged;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 适配器类，将com.github.hypfvieh库中的蓝牙管理功能适配到教授代码所期望的接口
 */
public class BluetoothManager {

    private static BluetoothManager instance;
    private DeviceManager deviceManager;
    private final Map<String, BluetoothDevice> deviceCache = new ConcurrentHashMap<>();

    // 添加一个Map来跟踪特性路径和特性对象之间的关系
    private final Map<String, BluetoothGattCharacteristic> characteristicsByPath = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(BluetoothManager.class);

    private BluetoothManager() {
        try {
            deviceManager = DeviceManager.createInstance(false);

            // 设置DBus信号处理
            setupSignalHandlers();

            LOGGER.info("蓝牙管理器初始化完成");
        } catch (Exception e) {
            LOGGER.error("初始化蓝牙管理器失败: {}", e.getMessage(), e);
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
        try {
            // 获取DBus连接
            DBusConnection connection = deviceManager.getDbusConnection();

            // 注册PropertiesChanged信号处理器
            LOGGER.info("正在注册属性变更处理器...");

            // 创建属性变更处理器
            PropertiesChangedHandler propHandler = new PropertiesChangedHandler();

            // 注册处理器
            deviceManager.registerPropertyHandler(propHandler);

            LOGGER.info("属性变更处理器注册完成");
        } catch (Exception e) {
            LOGGER.error("设置DBus信号处理器失败: {}", e.getMessage(), e);
        }
    }
    /**
     * 注册特性以便在值变化时收到通知
     */
    public void registerCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (characteristic != null) {
            String path = characteristic.getWrappedCharacteristic().getDbusPath();
            LOGGER.debug("注册特性路径: {}, UUID: {}", path, characteristic.getUUID());
            characteristicsByPath.put(path, characteristic);
        }
    }

    /**
     * Properties变更处理器内部类
     */
    private class PropertiesChangedHandler extends org.freedesktop.dbus.handlers.AbstractPropertiesChangedHandler {

        @Override
        public void handle(PropertiesChanged signal) {
            String path = signal.getPath();
            String interfaceName = signal.getInterfaceName();
            Map<String, Variant<?>> changedProperties = signal.getPropertiesChanged();

            LOGGER.debug("收到属性变更信号: path={}, interface={}", path, interfaceName);

            // 检查是否是GATT特性接口
            if ("org.bluez.GattCharacteristic1".equals(interfaceName)) {
                // 检查是否有Value属性变化
                if (changedProperties.containsKey("Value")) {
                    try {
                        // 获取Value属性的新值
                        Variant<?> variant = changedProperties.get("Value");
                        Object value = variant.getValue();

                        // 检查值类型并转换为字节数组
                        byte[] byteValue = null;
                        if (value instanceof byte[]) {
                            byteValue = (byte[]) value;
                        } else if (value instanceof List) {
                            // 有时值可能是字节列表
                            @SuppressWarnings("unchecked")
                            List<Byte> byteList = (List<Byte>) value;
                            byteValue = new byte[byteList.size()];
                            for (int i = 0; i < byteList.size(); i++) {
                                byteValue[i] = byteList.get(i);
                            }
                        }

                        if (byteValue != null) {
                            // 查找对应的特性对象
                            BluetoothGattCharacteristic characteristic = characteristicsByPath.get(path);

                            if (characteristic != null) {
                                LOGGER.debug("转发特性值变化通知: path={}, valueLength={}", path, byteValue.length);
                                // 通知值变化
                                characteristic.notifyValueChanged(byteValue);
                            } else {
                                LOGGER.warn("未找到特性路径对应的对象: {}", path);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("处理特性值变化时出错: {}", e.getMessage(), e);
                    }
                }
            }
        }

//        @Override
//        public Class<PropertiesChanged> getImplementationClass() {
//            return PropertiesChanged.class;
//        }
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