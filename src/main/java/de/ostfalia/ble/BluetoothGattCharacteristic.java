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
            // 获取BluetoothManager实例
            BluetoothManager manager = BluetoothManager.getBluetoothManager();

            // 注册此特性以接收通知
            manager.registerCharacteristic(this);

            // 启用特性通知
            System.out.println("正在启用特性通知，路径: " + characteristic.getDbusPath());
            characteristic.startNotify();
            System.out.println("特性通知已启用");
        } catch (Exception e) {
            System.err.println("启用通知失败: " + e.getMessage());
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
     * 这个方法由BluetoothManager在检测到值变化时调用
     *
     * @param value 新的值
     */
    public void notifyValueChanged(byte[] value) {
        if (valueNotificationConsumer != null) {
            System.out.println("接收到特性值变化通知，UUID: " + getUUID() + ", 数据长度: " + value.length);

            // 打印前几个字节用于调试
            if (value.length > 0) {
                System.out.print("数据内容前10字节: [");
                for (int i = 0; i < Math.min(value.length, 10); i++) {
                    System.out.print(String.format("%02X ", value[i]));
                }
                System.out.println("]");
            }

            // 调用回调函数处理通知
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