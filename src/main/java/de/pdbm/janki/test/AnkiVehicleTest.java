package de.pdbm.janki.test;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothManager;
import de.pdbm.janki.Vehicle;

import java.util.List;
import java.util.Scanner;

/**
 * 极简测试程序 - 只测试连接和速度控制
 */
public class AnkiVehicleTest {

    private static void delay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("===== 简单Anki测试程序 =====");
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
        boolean connected = selectedDevice.connect();
        System.out.println("连接状态: " + (connected ? "成功" : "失败"));

        // 等待1秒
        delay(1000);

        // 创建Vehicle对象
        System.out.println("创建Vehicle对象...");
        Vehicle vehicle = new Vehicle(selectedDevice);

        // 再等待一会，让特性初始化
        System.out.println("等待初始化...");
        for (int i = 0; i < 10; i++) {
            System.out.print(".");
            delay(500);
        }
        System.out.println();

        // 在等待初始化后，主动初始化特性
        System.out.println("初始化特性...");
        boolean initialized = vehicle.initializeCharacteristics();
        System.out.println("特性初始化状态: " + (initialized ? "成功" : "失败"));

        if (!initialized) {
            System.out.println("无法初始化车辆特性，程序退出");
            return;
        }

        // 简单的速度测试循环
        boolean exit = false;
        while (!exit) {
            System.out.println("\n==== 速度测试 ====");
            System.out.println("1: 检查状态");
            System.out.println("2: 设置速度");
            System.out.println("3: 停止");
            System.out.println("4: 退出");
            System.out.print("请选择: ");

            int cmd = scanner.nextInt();
            scanner.nextLine(); // 消耗换行符

            switch (cmd) {
                case 1:
                    // 检查状态
                    System.out.println("车辆状态:");
                    System.out.println("  连接状态: " + (vehicle.isConnected() ? "已连接" : "未连接"));
                    System.out.println("  准备状态: " + (vehicle.isReadyToStart() ? "已准备" : "未准备"));
                    System.out.println("  充电器状态: " + (vehicle.isOnCharger() ? "在充电器上" : "不在充电器上"));
                    System.out.println("  当前速度: " + vehicle.getSpeed());

                    // 检查设备连接状态
                    System.out.println("  设备连接状态: " + (selectedDevice.getConnected() ? "已连接" : "未连接"));
                    break;

                case 2:
                    // 设置速度
                    System.out.print("请输入速度 (0-1000): ");
                    int speed = scanner.nextInt();
                    scanner.nextLine(); // 消耗换行符

                    try {
                        vehicle.setSpeed(speed);
                        System.out.println("已设置速度: " + speed);
                    } catch (Exception e) {
                        System.out.println("设置速度时出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;

                case 3:
                    // 停止
                    try {
                        vehicle.setSpeed(0);
                        System.out.println("已停止车辆");
                    } catch (Exception e) {
                        System.out.println("停止车辆时出错: " + e.getMessage());
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