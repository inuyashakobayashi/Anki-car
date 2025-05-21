package de.pdbm.janki.test;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothManager;
import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.notifications.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anki轨道信息收集增强测试
 */
public class AnkiTrackControllerTest {

    private static Vehicle vehicle;

    // 轨道信息收集
    private static final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();
    private static final List<PositionUpdate> positionUpdates = new ArrayList<>();
    private static final List<TransitionUpdate> transitionUpdates = new ArrayList<>();

    // 当前位置信息
    private static int currentLocation = -1;
    private static RoadPiece currentRoadPiece = null;
    private static boolean ascendingLocation = true;

    // 监听器激活标志
    private static boolean positionListenerActive = false;
    private static boolean transitionListenerActive = false;

    // 状态计数器
    private static int totalNotificationsReceived = 0;

    private static void delay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void testNotificationSystem(Scanner scanner) {
        System.out.println("\n===== 通知系统测试 =====");
        System.out.println("此测试将验证通知系统是否正常工作");
        System.out.println("按回车键开始测试...");
        scanner.nextLine();

        int startNotifications = totalNotificationsReceived;

        System.out.println("设置低速并执行车道变换以触发通知...");
        vehicle.setSpeed(200);

        System.out.println("执行多次车道变换...");
        for (int i = 0; i < 3; i++) {
            System.out.println("  变换 " + (i+1) + "/3...");
            vehicle.changeLane(-0.3f);
            delay(1000);
            vehicle.changeLane(0.3f);
            delay(1000);
            vehicle.changeLane(0.0f);
            delay(1000);
        }

        vehicle.setSpeed(0);

        int endNotifications = totalNotificationsReceived;
        int newNotifications = endNotifications - startNotifications;

        System.out.println("\n测试结果:");
        System.out.println("收到 " + newNotifications + " 个新通知");

        if (newNotifications > 0) {
            System.out.println("通知系统工作正常!");
        } else {
            System.out.println("未收到任何通知。建议:");
            System.out.println("1. 检查小车电池");
            System.out.println("2. 重启小车");
            System.out.println("3. 确认小车已正确放置在轨道上");
        }
    }

    /**
     * 设置监听器
     */
    private static void setupListeners() {
        System.out.println("设置事件监听器...");

        // 位置更新监听器
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                positionListenerActive = true;
                totalNotificationsReceived++;
                positionUpdates.add(update);

                // 更新当前位置
                currentLocation = update.getLocation();
                currentRoadPiece = update.getRoadPiece();
                ascendingLocation = update.isAscendingLocations();

                // 添加到轨道地图
                trackMap.put(currentLocation, currentRoadPiece);

                System.out.println("\n位置更新 #" + positionUpdates.size() + ":");
                System.out.println("  位置ID: " + currentLocation);
                System.out.println("  轨道类型: " + currentRoadPiece);
                System.out.println("  方向: " + (ascendingLocation ? "正向" : "反向"));
            }
        });

        // 轨道过渡监听器 - 虽然不包含轨道信息，但可以帮助调试
        vehicle.addNotificationListener(new TransitionUpdateListener() {
            @Override
            public void onTransitionUpdate(TransitionUpdate update) {
                transitionListenerActive = true;
                totalNotificationsReceived++;
                transitionUpdates.add(update);

                System.out.println("\n轨道过渡 #" + transitionUpdates.size() + ":");
                System.out.println("  位置ID: " + update.getLocation());
                // 注意：roadPiece可能为null
                System.out.println("  轨道类型: " + (update.getRoadPiece() == null ? "未知" : update.getRoadPiece()));
            }
        });

        // 添加充电器信息监听器
        vehicle.addNotificationListener(new ChargerInfoNotificationListener() {
            @Override
            public void onChargerInfoNotification(ChargerInfoNotification notification) {
                System.out.println("\n充电器信息更新:");
                System.out.println("  在充电器上: " + notification.isOnCharger());
            }
        });

        // 不再添加通用通知监听器，因为它可能与接口定义不符

        System.out.println("事件监听器设置完成");
    }

    /**
     * 开始轨道映射
     */
    private static void startTrackMapping(Scanner scanner) {
        System.out.println("\n===== 轨道映射模式 =====");
        System.out.print("请输入映射速度 (建议 300-500): ");
        int speed = scanner.nextInt();
        scanner.nextLine(); // 消耗换行符

        // 清空之前的收集数据
        trackMap.clear();
        positionUpdates.clear();
        transitionUpdates.clear();
        currentLocation = -1;
        currentRoadPiece = null;

        try {
            // 关键步骤：重新初始化通信
            System.out.println("重新确保SDK模式和通知功能...");
            boolean reinitialized = vehicle.initializeCharacteristics();
            System.out.println("初始化状态: " + (reinitialized ? "成功" : "失败"));

            // 重要：给通知系统一些时间启动
            System.out.println("等待通知系统准备 (5秒)...");
            for (int i = 0; i < 5; i++) {
                System.out.print(".");
                delay(1000);
            }
            System.out.println(" 完成");

            // 开始轨道映射
            System.out.println("\n开始轨道映射，速度: " + speed);
            System.out.println("按回车键停止映射...");

            // 设置车速
            vehicle.setSpeed(speed);

            // 执行一次车道中心校准，这有时能触发传感器
            System.out.println("执行车道中心校准...");
            vehicle.changeLane(0.0f);
            delay(1000);

            // 等待用户按回车键
            scanner.nextLine();

            // 停车
            vehicle.setSpeed(0);
            System.out.println("轨道映射已停止");
            // 显示映射结果
            System.out.println("\n轨道映射结果:");
            System.out.println("收集到的轨道片段数: " + trackMap.size());
            System.out.println("位置更新数: " + positionUpdates.size());
            System.out.println("轨道过渡数: " + transitionUpdates.size());

            if (!trackMap.isEmpty()) {
                System.out.println("\n轨道地图:");
                List<Integer> sortedLocations = new ArrayList<>(trackMap.keySet());
                Collections.sort(sortedLocations);

                for (Integer location : sortedLocations) {
                    System.out.println("  位置ID: " + location + ", 类型: " + trackMap.get(location));
                }
            } else {
                System.out.println("未收集到轨道信息。");
            }

        } catch (Exception e) {
            System.out.println("轨道映射出错: " + e.getMessage());
            e.printStackTrace();
            vehicle.setSpeed(0); // 确保停车
        }
    }

    /**
     * 执行特殊测试
     */
    private static void performSpecialTest(Scanner scanner) {
        System.out.println("\n===== 特殊测试模式 =====");
        System.out.println("1: 速度变化测试");
        System.out.println("2: 紧急启停测试");
        System.out.println("3: 车道切换测试");
        System.out.println("4: 返回");
        System.out.print("请选择测试: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // 消耗换行符

        switch (choice) {

            case 2:
                emergencyStartStopTest(scanner);
                break;
            case 3:
                laneChangeTest(scanner);
                break;
            case 4:
            default:
                return;
        }
    }



    /**
     * 紧急启停测试
     */
    private static void emergencyStartStopTest(Scanner scanner) {
        System.out.println("\n===== 紧急启停测试 =====");
        System.out.println("此测试将反复快速启动和停止车辆，观察是否能触发更多位置更新");
        System.out.println("按回车键开始测试...");
        scanner.nextLine();

        int beforeCount = positionUpdates.size();
        int cycles = 10;

        try {
            System.out.println("执行 " + cycles + " 次启停循环...");

            for (int i = 0; i < cycles; i++) {
                System.out.println("循环 " + (i+1) + ":");

                // 快速启动
                System.out.println("  启动 (速度 500)");
                vehicle.setSpeed(500);
                delay(1000);

                // 紧急停止
                System.out.println("  停止");
                vehicle.setSpeed(0);
                delay(500);
            }

            // 记录结果
            int afterCount = positionUpdates.size();
            int updateCount = afterCount - beforeCount;

            System.out.println("\n测试结果: 触发了 " + updateCount + " 个位置更新");

        } catch (Exception e) {
            System.out.println("测试出错: " + e.getMessage());
            vehicle.setSpeed(0);
        }
    }

    /**
     * 车道切换测试
     */
    private static void laneChangeTest(Scanner scanner) {
        System.out.println("\n===== 车道切换测试 =====");
        System.out.println("此测试将在行驶过程中切换车道，观察是否能触发更多位置更新");
        System.out.println("按回车键开始测试...");
        scanner.nextLine();

        int beforeCount = positionUpdates.size();

        try {
            // 开始行驶
            System.out.println("开始行驶 (速度 300)");
            vehicle.setSpeed(300);
            delay(2000);

            // 左车道
            System.out.println("切换到左车道");
            vehicle.changeLane(-0.5f);
            delay(3000);

            // 右车道
            System.out.println("切换到右车道");
            vehicle.changeLane(0.5f);
            delay(3000);

            // 中间车道
            System.out.println("切换到中间车道");
            vehicle.changeLane(0.0f);
            delay(3000);

            // 停车
            vehicle.setSpeed(0);

            // 记录结果
            int afterCount = positionUpdates.size();
            int updateCount = afterCount - beforeCount;

            System.out.println("\n测试结果: 触发了 " + updateCount + " 个位置更新");

        } catch (Exception e) {
            System.out.println("测试出错: " + e.getMessage());
            vehicle.setSpeed(0);
        }
    }

    /**
     * 生成轨道报告
     */
    private static void generateTrackReport() {
        System.out.println("\n===== 轨道信息报告 =====");

        if (trackMap.isEmpty()) {
            System.out.println("尚未收集到轨道信息，无法生成报告。");
            return;
        }

        // 轨道类型统计
        Map<RoadPiece, Integer> pieceTypeCounts = new HashMap<>();
        for (RoadPiece piece : trackMap.values()) {
            pieceTypeCounts.put(piece, pieceTypeCounts.getOrDefault(piece, 0) + 1);
        }

        System.out.println("轨道类型统计:");
        for (Map.Entry<RoadPiece, Integer> entry : pieceTypeCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " 片段");
        }

        // 轨道序列
        System.out.println("\n轨道序列 (按位置ID排序):");
        List<Integer> sortedLocations = new ArrayList<>(trackMap.keySet());
        Collections.sort(sortedLocations);

        for (int i = 0; i < sortedLocations.size(); i++) {
            Integer location = sortedLocations.get(i);
            RoadPiece piece = trackMap.get(location);
            System.out.println("  " + (i+1) + ". 位置ID: " + location + ", 类型: " + piece);
        }

        // 特殊轨道片段
        System.out.println("\n特殊轨道片段:");
        for (Map.Entry<Integer, RoadPiece> entry : trackMap.entrySet()) {
            if (entry.getValue() == RoadPiece.START) {
                System.out.println("  起点: 位置ID " + entry.getKey());
            } else if (entry.getValue() == RoadPiece.FINISH) {
                System.out.println("  终点: 位置ID " + entry.getKey());
            } else if (entry.getValue() == RoadPiece.INTERSECTION) {
                System.out.println("  交叉口: 位置ID " + entry.getKey());
            }
        }

        // 监听器状态
        System.out.println("\n监听器状态:");
        System.out.println("  位置更新监听器: " + (positionListenerActive ? "已激活" : "未激活"));
        System.out.println("  轨道过渡监听器: " + (transitionListenerActive ? "已激活" : "未激活"));
        System.out.println("  总接收到的通知数: " + totalNotificationsReceived);
        System.out.println("  位置更新数: " + positionUpdates.size());
        System.out.println("  轨道过渡数: " + transitionUpdates.size());
    }

    public static void main(String[] args) {
        System.out.println("===== Anki轨道信息收集增强测试 =====");
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
        vehicle = new Vehicle(selectedDevice);

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

        // 设置监听器
        setupListeners();

        // 主菜单
        boolean exit = false;
        while (!exit) {
            System.out.println("\n===== Anki轨道收集菜单 =====");
            System.out.println("1: 检查状态");
            System.out.println("2: 手动设置速度");
            System.out.println("3: 手动切换车道");
            System.out.println("4: 启动轨道映射");
            System.out.println("5: 执行特殊测试");
            System.out.println("6: 生成轨道报告");
            System.out.println("7: 退出");
            System.out.println("8: 测试通知系统");

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
                    System.out.println("  当前位置: " + (currentLocation == -1 ? "未知" : currentLocation));
                    System.out.println("  当前轨道类型: " + (currentRoadPiece == null ? "未知" : currentRoadPiece));
                    System.out.println("  收集的轨道片段数: " + trackMap.size());
                    System.out.println("  位置更新数: " + positionUpdates.size());
                    System.out.println("  轨道过渡数: " + transitionUpdates.size());
                    System.out.println("  总通知数: " + totalNotificationsReceived);
                    break;

                case 2:
                    // 手动设置速度
                    System.out.print("请输入速度 (0-1000): ");
                    int speed = scanner.nextInt();
                    scanner.nextLine(); // 消耗换行符

                    try {
                        vehicle.setSpeed(speed);
                        System.out.println("已设置速度: " + speed);
                    } catch (Exception e) {
                        System.out.println("设置速度出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;

                case 3:
                    // 手动切换车道
                    System.out.print("请输入车道偏移量 (-1.0到1.0): ");
                    float offset = scanner.nextFloat();
                    scanner.nextLine(); // 消耗换行符

                    try {
                        vehicle.changeLane(offset);
                        System.out.println("已发送车道切换命令，偏移量: " + offset);
                    } catch (Exception e) {
                        System.out.println("切换车道出错: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;

                case 4:
                    // 启动轨道映射
                    startTrackMapping(scanner);
                    break;

                case 5:
                    // 执行特殊测试
                    performSpecialTest(scanner);
                    break;

                case 6:
                    // 生成轨道报告
                    generateTrackReport();
                    break;

                case 7:
                    // 退出
                    exit = true;
                    System.out.println("程序退出");
                    vehicle.setSpeed(0);
                    break;
                case 8:
                    // 测试通知系统
                    testNotificationSystem(scanner);
                    break;
                default:
                    System.out.println("无效的选择");
                    break;
            }
        }

        scanner.close();
    }
}