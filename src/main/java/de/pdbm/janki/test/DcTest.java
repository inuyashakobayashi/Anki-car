package de.pdbm.janki.test;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothGattCharacteristic;
import de.ostfalia.ble.BluetoothGattService;
import de.ostfalia.ble.BluetoothManager;

import java.util.List;
import java.util.Scanner;

/**
 * 最小化测试程序 - 直接访问特性
 */
public class DcTest {

    private static final String ANKI_SERVICE_UUID = "BE15BEEF-6186-407E-8381-0BD89C4D8DF4";
    private static final String ANKI_WRITE_CHARACTERISTIC_UUID = "BE15BEE1-6186-407E-8381-0BD89C4D8DF4";

    private static void delay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // SDK模式消息，直接从Message类复制
    private static byte[] getSdkMode() {
        byte[] message = new byte[2];
        message[0] = (byte) 0x90;
        message[1] = 0x01;
        return message;
    }

    // 速度消息，直接从Message类复制
    private static byte[] speedMessage(short speed) {
        byte[] message = new byte[7];
        message[0] = (byte) 0xC1;
        message[1] = (byte) (speed & 0xFF);
        message[2] = (byte) ((speed >> 8) & 0xFF);
        message[3] = (byte) 0x00;
        message[4] = (byte) 0x00;
        message[5] = (byte) 0x00;
        message[6] = (byte) 0x00;
        return message;
    }

    public static void main(String[] args) {
        System.out.println("===== 直接特性测试 =====");
        System.out.println("正在初始化蓝牙...");

        // 获取蓝牙管理器
        BluetoothManager manager = BluetoothManager.getBluetoothManager();

        // 获取设备列表
        List<BluetoothDevice> devices = manager.getDevices();

        // 打印Anki设备
        System.out.println("找到的可能是Anki小车的设备:");
        int index = 1;
        for (BluetoothDevice device : devices) {
            List<String> uuids = device.getUUIDs();
            if (uuids != null) {
                for (String uuid : uuids) {
                    if (uuid.toLowerCase().contains("beef")) {
                        System.out.println(index + ": MAC地址: " + device.getAddress() +
                                " UUID: " + uuid + " [可能是Anki小车]");
                        index++;
                        break;
                    }
                }
            }
        }

        if (index == 1) {
            System.out.println("未找到可能的Anki小车设备。");
            return;
        }

        // 选择设备
        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入要连接的设备编号: ");
        int choice = scanner.nextInt();
        scanner.nextLine(); // 消耗换行符

        // 查找所选设备
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
            System.out.println("无效的选择。");
            return;
        }

        System.out.println("选择的设备: " + selectedDevice.getAddress());

        // 尝试连接
        System.out.println("尝试连接...");
        for (int attempt = 1; attempt <= 5; attempt++) {
            System.out.println("连接尝试 #" + attempt);
            boolean connected = selectedDevice.connect();
            System.out.println("连接状态: " + (connected ? "成功" : "失败"));
            if (connected) break;
            delay(1000);
        }

        // 直接查找Anki服务和写特性
        System.out.println("\n查找服务和特性...");
        BluetoothGattService ankiService = null;
        List<BluetoothGattService> services = selectedDevice.getServices();

        System.out.println("找到 " + services.size() + " 个服务:");
        for (BluetoothGattService service : services) {
            String serviceUUID = service.getUUID();
            System.out.println("- 服务: " + serviceUUID);

            if (ANKI_SERVICE_UUID.equalsIgnoreCase(serviceUUID)) {
                ankiService = service;
                System.out.println("  ** 找到Anki服务 **");
            }
        }

        if (ankiService == null) {
            System.out.println("未找到Anki服务，程序退出。");
            return;
        }

        // 查找写特性
        BluetoothGattCharacteristic writeCharacteristic = null;
        List<BluetoothGattCharacteristic> characteristics = ankiService.getCharacteristics();

        System.out.println("\nAnki服务包含 " + characteristics.size() + " 个特性:");
        for (BluetoothGattCharacteristic characteristic : characteristics) {
            String charUUID = characteristic.getUUID();
            System.out.println("- 特性: " + charUUID);

            if (ANKI_WRITE_CHARACTERISTIC_UUID.equalsIgnoreCase(charUUID)) {
                writeCharacteristic = characteristic;
                System.out.println("  ** 找到写特性 **");
            }
        }

        if (writeCharacteristic == null) {
            System.out.println("未找到写特性，程序退出。");
            return;
        }

        // 进入SDK模式
        System.out.println("\n尝试进入SDK模式...");
        boolean sdkModeSet = writeCharacteristic.writeValue(getSdkMode());
        System.out.println("SDK模式设置: " + (sdkModeSet ? "成功" : "失败"));

        // 简单的速度测试循环
        boolean exit = false;
        while (!exit) {
            System.out.println("\n==== 直接速度测试 ====");
            System.out.println("1: 设置速度");
            System.out.println("2: 停止");
            System.out.println("3: 重新连接");
            System.out.println("4: 退出");
            System.out.print("请选择: ");

            int cmd = scanner.nextInt();
            scanner.nextLine(); // 消耗换行符

            switch (cmd) {
                case 1:
                    // 设置速度
                    System.out.print("请输入速度 (0-1000): ");
                    int speed = scanner.nextInt();
                    scanner.nextLine(); // 消耗换行符

                    try {
                        boolean result = writeCharacteristic.writeValue(speedMessage((short)speed));
                        System.out.println("速度设置: " + (result ? "成功" : "失败") + " (" + speed + ")");
                    } catch (Exception e) {
                        System.out.println("设置速度时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;

                case 2:
                    // 停止
                    try {
                        boolean result = writeCharacteristic.writeValue(speedMessage((short)0));
                        System.out.println("停止: " + (result ? "成功" : "失败"));
                    } catch (Exception e) {
                        System.out.println("停止时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;

                case 3:
                    // 重新连接
                    try {
                        System.out.println("尝试重新连接...");
                        selectedDevice.disconnect();
                        delay(1000);
                        boolean connected = selectedDevice.connect();
                        System.out.println("重新连接: " + (connected ? "成功" : "失败"));
                    } catch (Exception e) {
                        System.out.println("重新连接时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;

                case 4:
                    // 退出
                    exit = true;
                    System.out.println("程序退出");
                    break;

                default:
                    System.out.println("无效的选择");
                    break;
            }
        }

        scanner.close();
    }
}