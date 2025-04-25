package de.ostfalia.ble;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * 适配器类，将com.github.hypfvieh库中的BluetoothDevice适配到教授代码所期望的接口
 */
public class BluetoothDevice {
    private final com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice device; // 内部持有新库中的设备对象

    // 存储连接通知回调
    private Consumer<Boolean> connectedNotificationConsumer;

    public BluetoothDevice(com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice device) {
        this.device = device;
    }

    /**
     * 连接到设备
     * @return 是否连接成功
     */
    public boolean connect() {
        return device.connect();
    }

    /**
     * 获取当前连接状态
     * @return 是否已连接
     */
    public boolean getConnected() {
        return device.isConnected();
    }

    /**
     * 断开与设备的连接
     */
    public void disconnect() {
        device.disconnect();
    }

    /**
     * 获取设备MAC地址
     * @return MAC地址
     */
    public String getAddress() {
        return device.getAddress();
    }

    /**
     * 获取设备提供的UUID列表
     * @return UUID列表
     */
    public List<String> getUUIDs() {
        List<String> result = new ArrayList<>();
        String[] uuids = device.getUuids();
        if (uuids != null) {
            for (String uuid : uuids) {
                result.add(uuid.toLowerCase()); // 转为小写以匹配教授代码中的比较方式
            }
        }
        return result;
    }

    /**
     * 获取设备的服务列表
     * @return GATT服务列表
     */
    public List<BluetoothGattService> getServices() {
        List<BluetoothGattService> result = new ArrayList<>();
        List<com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService> services = device.getGattServices();

        if (services != null) {
            for (com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService service : services) {
                result.add(new BluetoothGattService(service));
            }
        }

        return result;
    }

    /**
     * 启用连接状态通知
     * @param callback 回调函数，当连接状态改变时触发
     */
    public void enableConnectedNotifications(Consumer<Boolean> callback) {
        this.connectedNotificationConsumer = callback;

        // 这里你需要设置设备连接状态监听
        // 在hypfvieh库中，这可能需要通过DBus信号监听来实现
        // 当状态改变时调用：callback.accept(isConnected);

        // 由于这部分实现可能比较复杂，你可能需要在BluetoothManager中
        // 添加信号处理代码来支持这个功能
    }

    /**
     * 停用连接状态通知
     */
    public void disableConnectedNotifications() {
        this.connectedNotificationConsumer = null;
        // 取消设备连接状态监听
    }

    /**
     * 手动触发连接状态通知
     * 这个方法可以由BluetoothManager在检测到连接状态变化时调用
     *
     * @param connected 当前连接状态
     */
    public void notifyConnectionState(boolean connected) {
        if (connectedNotificationConsumer != null) {
            connectedNotificationConsumer.accept(connected);
        }
    }

    /**
     * 获取底层的hypfvieh设备对象
     * @return hypfvieh的BluetoothDevice对象
     */
    public com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice getWrappedDevice() {
        return device;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BluetoothDevice) {
            return this.getAddress().equals(((BluetoothDevice) obj).getAddress());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getAddress().hashCode();
    }
}