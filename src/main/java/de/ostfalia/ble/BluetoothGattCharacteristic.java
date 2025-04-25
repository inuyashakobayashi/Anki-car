package de.ostfalia.ble;

import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;
import org.bluez.exceptions.*;

/**
 * 适配器类，将com.github.hypfvieh库中的BluetoothGattCharacteristic适配到教授代码所期望的接口
 */
public class BluetoothGattCharacteristic {
    private final com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic characteristic;

    // 存储值通知回调
    private Consumer<byte[]> valueNotificationConsumer;

    public BluetoothGattCharacteristic(com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic characteristic) {
        this.characteristic = characteristic;
    }

    /**
     * 获取特性的UUID
     * @return 特性UUID
     */
    public String getUUID() {
        return characteristic.getUuid().toUpperCase(); // 教授代码中使用大写UUID进行比较
    }

    /**
     * 向特性写入数据
     * @param bytes 要写入的数据
     * @return 是否写入成功
     */
    public boolean writeValue(byte[] bytes) {
        try {
            // 创建一个空的选项Map
            Map<String, Object> options = new HashMap<>();
            characteristic.writeValue(bytes, options);
            return true;
        } catch (BluezFailedException | BluezInProgressException | BluezNotPermittedException
                 | BluezNotAuthorizedException | BluezNotSupportedException
                 | BluezInvalidValueLengthException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 读取特性的值
     * @return 特性的值
     */
    public byte[] readValue() {
        try {
            // 创建一个空的选项Map
            Map<String, Object> options = new HashMap<>();
            return characteristic.readValue(options);
        } catch (BluezFailedException | BluezInProgressException | BluezNotPermittedException
                 | BluezNotAuthorizedException | BluezNotSupportedException
                 | BluezInvalidOffsetException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    /**
     * 启用值变化通知
     * @param callback 当值变化时的回调函数
     */
    public void enableValueNotifications(Consumer<byte[]> callback) {
        this.valueNotificationConsumer = callback;

        try {
            // 启用特性通知
            characteristic.startNotify();

            // 这里需要实现监听特性值变化的逻辑
            // 在hypfvieh库中，这可能需要通过DBus信号监听来实现
            // 当值变化时调用：callback.accept(newValue);

            // 由于这部分实现可能比较复杂，你可能需要在BluetoothManager中
            // 添加信号处理代码来支持这个功能
        } catch (BluezFailedException | BluezInProgressException | BluezNotSupportedException
                 | BluezNotPermittedException | BluezNotConnectedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停用值变化通知
     */
    public void disableValueNotifications() {
        this.valueNotificationConsumer = null;

        try {
            // 停用特性通知
            characteristic.stopNotify();
        } catch (BluezFailedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 手动触发值变化通知
     * 这个方法可以由BluetoothManager在检测到值变化时调用
     *
     * @param value 新的值
     */
    public void notifyValueChanged(byte[] value) {
        if (valueNotificationConsumer != null) {
            valueNotificationConsumer.accept(value);
        }
    }

    /**
     * 获取底层的hypfvieh特性对象
     * @return hypfvieh的BluetoothGattCharacteristic对象
     */
    public com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic getWrappedCharacteristic() {
        return characteristic;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BluetoothGattCharacteristic) {
            return this.getUUID().equals(((BluetoothGattCharacteristic) obj).getUUID());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getUUID().hashCode();
    }
}