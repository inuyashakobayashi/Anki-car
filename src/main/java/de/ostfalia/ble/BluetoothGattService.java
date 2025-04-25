package de.ostfalia.ble;

import java.util.List;
import java.util.ArrayList;

/**
 * 适配器类，将com.github.hypfvieh库中的BluetoothGattService适配到教授代码所期望的接口
 */
public class BluetoothGattService {
    private final com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService service;

    public BluetoothGattService(com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService service) {
        this.service = service;
    }

    /**
     * 获取服务的UUID
     * @return 服务UUID
     */
    public String getUUID() {
        return service.getUuid().toUpperCase(); // 教授代码中使用大写UUID进行比较
    }

    /**
     * 获取服务包含的所有特性
     * @return 特性列表
     */
    public List<BluetoothGattCharacteristic> getCharacteristics() {
        List<BluetoothGattCharacteristic> result = new ArrayList<>();
        List<com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic> characteristics =
                service.getGattCharacteristics();

        if (characteristics != null) {
            for (com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic characteristic : characteristics) {
                result.add(new BluetoothGattCharacteristic(characteristic));
            }
        }

        return result;
    }

    /**
     * 根据UUID查找特性
     * @param uuid 特性UUID
     * @return 对应的特性对象，如果未找到则返回null
     */
    public BluetoothGattCharacteristic find(String uuid) {
        com.github.hypfvieh.bluetooth.wrapper.BluetoothGattCharacteristic characteristic =
                service.getGattCharacteristicByUuid(uuid);

        if (characteristic != null) {
            return new BluetoothGattCharacteristic(characteristic);
        }

        return null;
    }

    /**
     * 获取底层的hypfvieh服务对象
     * @return hypfvieh的BluetoothGattService对象
     */
    public com.github.hypfvieh.bluetooth.wrapper.BluetoothGattService getWrappedService() {
        return service;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BluetoothGattService) {
            return this.getUUID().equals(((BluetoothGattService) obj).getUUID());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getUUID().hashCode();
    }
}