package de.pdbm.anki.example;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothManager;
import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.notifications.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 综合的Anki Overdrive控制器示例
 * 集成了设备连接、轨道映射、车辆控制等完整功能
 * 修改版：将日志输出分离到文件
 */
public class AnkiControlExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnkiControlExample.class);

    // === 文件输出 ===
    private static PrintWriter logWriter;
    private static final String LOG_FILE = "anki_vehicle_log.txt";

    // === 车辆和连接 ===
    private static Vehicle vehicle;

    // === 轨道信息 ===
    private static final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();
    private static final List<PositionUpdate> positionUpdates = new ArrayList<>();
    private static final List<TransitionUpdate> transitionUpdates = new ArrayList<>();

    // === 当前位置 ===
    private static int currentLocation = -1;
    private static RoadPiece currentRoadPiece = null;
    private static boolean ascendingLocation = true;

    // === 状态跟踪 ===
    private static boolean positionListenerActive = false;
    private static boolean transitionListenerActive = false;
    private static int totalNotificationsReceived = 0;

    /**
     * 初始化日志文件
     */
    private static void initializeLogFile() {
        try {
            // 创建带时间戳的日志文件
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String logFileName = "anki_log_" + timestamp + ".txt";

            logWriter = new PrintWriter(new FileWriter(logFileName, true));

            // 写入文件头
            logWriter.println("===== Anki Overdrive 车辆日志 =====");
            logWriter.println("开始时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            logWriter.println("==========================================");
            logWriter.flush();

            // 只在控制台显示日志文件信息
            System.out.println("📝 日志文件已创建: " + logFileName);

        } catch (IOException e) {
            System.err.println("❌ 无法创建日志文件: " + e.getMessage());
        }
    }

    /**
     * 写入日志到文件（不在控制台显示）
     */
    private static void writeToLog(String message) {
        if (logWriter != null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            logWriter.println("[" + timestamp + "] " + message);
            logWriter.flush();
        }
    }

    /**
     * 写入日志到文件并在控制台显示（用于重要信息）
     */
    private static void writeToLogAndConsole(String message) {
        writeToLog(message);
        System.out.println(message);
    }

    /**
     * 线程延迟辅助方法
     */
    private static void delay(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("线程被中断");
        }
    }

    /**
     * 配置车辆事件监听器
     */
    private static void setupListeners() {
        System.out.println("配置事件监听器...");

        // 位置更新监听器 - 只写入日志文件
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

                // 只写入日志文件，不在控制台显示
                writeToLog("POSITION UPDATE #" + positionUpdates.size() + ":");
                writeToLog("  Location ID: " + currentLocation);
                writeToLog("  Road Type: " + currentRoadPiece);
                writeToLog("  Direction: " + (ascendingLocation ? "Forward" : "Reverse"));
                writeToLog(""); // 空行分隔
            }
        });

        // 转换监听器 - 只写入日志文件
        vehicle.addNotificationListener(new TransitionUpdateListener() {
            @Override
            public void onTransitionUpdate(TransitionUpdate update) {
                transitionListenerActive = true;
                totalNotificationsReceived++;
                transitionUpdates.add(update);

                // 只显示重要的转换，且只写入日志文件
                if (isSignificantTransition(update)) {
                    writeToLog("TRACK TRANSITION #" + transitionUpdates.size() + ":");
                    writeToLog("  Location ID: " + update.getLocation());
                    writeToLog("  Road Type: " +
                            (update.getRoadPiece() != null ? update.getRoadPiece() : "Transitioning"));
                    writeToLog(""); // 空行分隔
                } else {
                    writeToLog("Transition (filtered): ID=" + update.getLocation());
                }
            }
        });

        // 充电器信息监听器 - 写入日志文件和控制台
        vehicle.addNotificationListener(new ChargerInfoNotificationListener() {
            @Override
            public void onChargerInfoNotification(ChargerInfoNotification notification) {
                String message = "🔋 充电器状态: " + (notification.isOnCharger() ? "在充电器上" : "不在充电器上");
                writeToLogAndConsole(message);
            }
        });

        System.out.println("✓ 事件监听器配置完成");
        writeToLog("事件监听器已配置完成");
    }

    /**
     * 判断转换是否重要，过滤冗余转换
     */
    private static boolean isSignificantTransition(TransitionUpdate update) {
        return update.getRoadPiece() != null ||
                (update.getLocation() != 0 && update.getLocation() != currentLocation);
    }

    /**
     * 开始轨道映射模式
     */
    private static void startTrackMapping(Scanner scanner) {
        System.out.println("\n===== 轨道映射模式 =====");
        System.out.print("输入映射速度 (推荐 300-500): ");
        int speed = scanner.nextInt();
        scanner.nextLine(); // 消费换行符

        // 清除之前的数据
        trackMap.clear();
        positionUpdates.clear();
        transitionUpdates.clear();
        currentLocation = -1;
        currentRoadPiece = null;

        writeToLog("===== 开始轨道映射 =====");
        writeToLog("映射速度: " + speed);

        try {
            // 重新初始化连接
            System.out.println("确保SDK模式和通知设置...");
            boolean reinitialized = vehicle.initializeCharacteristics();
            System.out.println("初始化: " + (reinitialized ? "✓ 成功" : "✗ 失败"));
            writeToLog("重新初始化: " + (reinitialized ? "成功" : "失败"));

            // 等待通知系统
            System.out.println("等待通知系统准备 (5秒)...");
            for (int i = 0; i < 5; i++) {
                System.out.print(".");
                delay(1000);
            }
            System.out.println(" ✓ 准备完成");

            // 开始轨道映射
            System.out.println("\n🚗 轨道映射开始，速度: " + speed);
            System.out.println("📝 位置更新正在记录到日志文件...");
            System.out.println("按回车键停止...");
            writeToLog("轨道映射开始，速度: " + speed);

            // 设置速度
            vehicle.setSpeed(speed);

            // 车道校准
            System.out.println("进行车道校准...");
            vehicle.changeLane(0.0f);
            delay(1000);

            // 等待用户输入
            scanner.nextLine();

            // 停止
            vehicle.setSpeed(0);
            System.out.println("🛑 轨道映射停止");
            writeToLog("轨道映射停止");

            // 显示结果
            displayMappingResults();

        } catch (Exception e) {
            String errorMsg = "✗ 轨道映射错误: " + e.getMessage();
            System.out.println(errorMsg);
            writeToLog("ERROR: " + errorMsg);
            LOGGER.error("轨道映射失败", e);
            vehicle.setSpeed(0); // 安全停止
        }
    }

    /**
     * 显示映射结果
     */
    private static void displayMappingResults() {
        System.out.println("\n===== 映射结果 =====");
        System.out.println("📊 收集的轨道段: " + trackMap.size());
        System.out.println("📍 位置更新: " + positionUpdates.size());
        System.out.println("🔄 轨道转换: " + transitionUpdates.size());
        System.out.println("📝 详细信息已保存到日志文件");

        // 将摘要信息写入日志文件
        writeToLog("===== MAPPING RESULTS SUMMARY =====");
        writeToLog("Collected track segments: " + trackMap.size());
        writeToLog("Total position updates: " + positionUpdates.size());
        writeToLog("Total track transitions: " + transitionUpdates.size());

        if (!trackMap.isEmpty()) {
            writeToLog("Track Map:");
            List<Integer> sortedLocations = new ArrayList<>(trackMap.keySet());
            Collections.sort(sortedLocations);

            for (Integer location : sortedLocations) {
                RoadPiece piece = trackMap.get(location);
                String icon = getAsciiIconForRoadPiece(piece);
                writeToLog("  " + icon + " ID: " + location + " -> " + piece);
            }
        } else {
            writeToLog("No track information collected");
        }
        writeToLog("====================================");
    }

    /**
     * 为轨道类型返回合适的图标 (控制台版本)
     */
    private static String getIconForRoadPiece(RoadPiece piece) {
        if (piece == null) return "❓";

        return switch (piece) {
            case STRAIGHT -> "➡️";
            case CORNER -> "🔄";
            case START -> "🏁";
            case FINISH -> "🏁";
            case INTERSECTION -> "✖️";
            default -> "⭕";
        };
    }

    /**
     * 为轨道类型返回ASCII图标 (日志文件版本)
     */
    private static String getAsciiIconForRoadPiece(RoadPiece piece) {
        if (piece == null) return "[?]";

        return switch (piece) {
            case STRAIGHT -> "[->]";
            case CORNER -> "[C]";
            case START -> "[S]";
            case FINISH -> "[F]";
            case INTERSECTION -> "[X]";
            default -> "[O]";
        };
    }

    /**
     * 测试通知系统
     */
    private static void testNotificationSystem(Scanner scanner) {
        System.out.println("\n===== 通知系统测试 =====");
        System.out.println("此测试检查通知系统是否正常工作");
        System.out.println("📝 测试结果将记录到日志文件");
        System.out.println("按回车键开始...");
        scanner.nextLine();

        int startNotifications = totalNotificationsReceived;
        writeToLog("===== 通知系统测试开始 =====");

        System.out.println("设置低速并执行车道变换...");
        vehicle.setSpeed(200);
        writeToLog("设置速度: 200");

        System.out.println("执行多次车道变换...");
        for (int i = 0; i < 3; i++) {
            System.out.println("  车道变换 " + (i+1) + "/3...");
            writeToLog("车道变换 " + (i+1) + "/3");

            vehicle.changeLane(-0.3f);
            writeToLog("  → 左车道 (-0.3)");
            delay(1000);

            vehicle.changeLane(0.3f);
            writeToLog("  → 右车道 (0.3)");
            delay(1000);

            vehicle.changeLane(0.0f);
            writeToLog("  → 中间 (0.0)");
            delay(1000);
        }

        vehicle.setSpeed(0);
        writeToLog("停止车辆");

        int endNotifications = totalNotificationsReceived;
        int newNotifications = endNotifications - startNotifications;

        System.out.println("\n测试结果:");
        System.out.println("接收到: " + newNotifications + " 个新通知");

        writeToLog("测试结果: 接收到 " + newNotifications + " 个新通知");

        if (newNotifications > 0) {
            System.out.println("✓ 通知系统正常工作!");
            writeToLog("✓ 通知系统正常工作");
        } else {
            System.out.println("✗ 未接收到通知。建议:");
            System.out.println("1. 检查车辆电池");
            System.out.println("2. 重启车辆");
            System.out.println("3. 检查车辆在轨道上的正确放置");

            writeToLog("✗ 未接收到通知 - 可能的问题:");
            writeToLog("1. 车辆电池低电量");
            writeToLog("2. 车辆需要重启");
            writeToLog("3. 车辆位置不正确");
        }

        writeToLog("===== 通知系统测试结束 =====");
    }

    /**
     * 执行特殊车辆测试
     */
    private static void performSpecialTest(Scanner scanner) {
        System.out.println("\n===== 特殊车辆测试 =====");
        System.out.println("1: 启动-停止测试");
        System.out.println("2: 车道变换测试");
        System.out.println("3: 返回");
        System.out.print("选择测试: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // 消费换行符

        switch (choice) {
            case 1 -> emergencyStartStopTest(scanner);
            case 2 -> laneChangeTest(scanner);
            case 3 -> { /* 返回 */ }
            default -> System.out.println("无效选择");
        }
    }

    /**
     * 快速启动-停止循环测试
     */
    private static void emergencyStartStopTest(Scanner scanner) {
        System.out.println("\n===== 启动-停止测试 =====");
        System.out.println("测试快速启动-停止循环以获得更多位置更新");
        System.out.println("📝 测试过程将记录到日志文件");
        System.out.println("按回车键开始...");
        scanner.nextLine();

        int beforeCount = positionUpdates.size();
        int cycles = 10;

        writeToLog("===== 启动-停止测试开始 =====");
        writeToLog("测试循环次数: " + cycles);

        try {
            System.out.println("执行 " + cycles + " 个启动-停止循环...");

            for (int i = 0; i < cycles; i++) {
                System.out.println("  循环 " + (i+1) + "/" + cycles);
                writeToLog("循环 " + (i+1) + "/" + cycles + ":");

                System.out.println("    🚀 启动 (速度 500)");
                writeToLog("  启动 - 速度 500");
                vehicle.setSpeed(500);
                delay(1000);

                System.out.println("    🛑 停止");
                writeToLog("  停止");
                vehicle.setSpeed(0);
                delay(500);
            }

            int afterCount = positionUpdates.size();
            int updateCount = afterCount - beforeCount;

            System.out.println("\n📊 测试结果: " + updateCount + " 个新位置更新");
            writeToLog("测试完成 - 新位置更新: " + updateCount);
            writeToLog("===== 启动-停止测试结束 =====");

        } catch (Exception e) {
            String errorMsg = "✗ 测试失败: " + e.getMessage();
            System.out.println(errorMsg);
            writeToLog("ERROR: " + errorMsg);
            vehicle.setSpeed(0);
        }
    }

    /**
     * 车道变换测试
     */
    private static void laneChangeTest(Scanner scanner) {
        System.out.println("\n===== 车道变换测试 =====");
        System.out.println("测试行驶中的车道变换");
        System.out.println("📝 测试过程将记录到日志文件");
        System.out.println("按回车键开始...");
        scanner.nextLine();

        int beforeCount = positionUpdates.size();
        writeToLog("===== 车道变换测试开始 =====");

        try {
            System.out.println("🚗 开始行驶 (速度 300)");
            writeToLog("开始行驶 - 速度 300");
            vehicle.setSpeed(300);
            delay(2000);

            System.out.println("⬅️ 变换到左车道");
            writeToLog("变换到左车道 (-0.5)");
            vehicle.changeLane(-0.5f);
            delay(3000);

            System.out.println("➡️ 变换到右车道");
            writeToLog("变换到右车道 (0.5)");
            vehicle.changeLane(0.5f);
            delay(3000);

            System.out.println("⬆️ 回到中间");
            writeToLog("回到中间车道 (0.0)");
            vehicle.changeLane(0.0f);
            delay(3000);

            vehicle.setSpeed(0);
            writeToLog("停止车辆");

            int afterCount = positionUpdates.size();
            int updateCount = afterCount - beforeCount;

            System.out.println("\n📊 测试结果: " + updateCount + " 个新位置更新");
            writeToLog("测试完成 - 新位置更新: " + updateCount);
            writeToLog("===== 车道变换测试结束 =====");

        } catch (Exception e) {
            String errorMsg = "✗ 测试失败: " + e.getMessage();
            System.out.println(errorMsg);
            writeToLog("ERROR: " + errorMsg);
            vehicle.setSpeed(0);
        }
    }

    /**
     * 生成详细轨道报告
     */
    private static void generateTrackReport() {
        System.out.println("\n===== 详细轨道报告 =====");

        if (trackMap.isEmpty()) {
            System.out.println("⚠️ 无轨道信息可用");
            return;
        }

        writeToLog("===== DETAILED TRACK REPORT =====");

        // 轨道类型统计
        Map<RoadPiece, Integer> pieceTypeCounts = new HashMap<>();
        for (RoadPiece piece : trackMap.values()) {
            pieceTypeCounts.put(piece, pieceTypeCounts.getOrDefault(piece, 0) + 1);
        }

        System.out.println("📊 轨道类型统计:");
        writeToLog("Track Type Statistics:");
        for (Map.Entry<RoadPiece, Integer> entry : pieceTypeCounts.entrySet()) {
            String consoleIcon = getIconForRoadPiece(entry.getKey());
            String logIcon = getAsciiIconForRoadPiece(entry.getKey());
            String consoleLine = "  " + consoleIcon + " " + entry.getKey() + ": " + entry.getValue() + " 段";
            String logLine = "  " + logIcon + " " + entry.getKey() + ": " + entry.getValue() + " segments";
            System.out.println(consoleLine);
            writeToLog(logLine);
        }

        // 轨道序列
        System.out.println("\n🗺️ 轨道序列 (按位置排序):");
        System.out.println("📝 详细序列信息已保存到日志文件");

        writeToLog("Track Sequence (sorted by location):");
        List<Integer> sortedLocations = new ArrayList<>(trackMap.keySet());
        Collections.sort(sortedLocations);

        for (int i = 0; i < sortedLocations.size(); i++) {
            Integer location = sortedLocations.get(i);
            RoadPiece piece = trackMap.get(location);
            String icon = getAsciiIconForRoadPiece(piece);
            writeToLog("  " + (i+1) + ". " + icon + " Location: " + location + " -> " + piece);
        }

        // 特殊轨道段
        System.out.println("\n🎯 特殊轨道段:");
        writeToLog("Special Track Segments:");
        boolean foundSpecial = false;
        for (Map.Entry<Integer, RoadPiece> entry : trackMap.entrySet()) {
            String consoleSpecial = switch (entry.getValue()) {
                case START -> "🏁 起始线";
                case FINISH -> "🏁 终点线";
                case INTERSECTION -> "✖️ 交叉口";
                default -> null;
            };
            String logSpecial = switch (entry.getValue()) {
                case START -> "[S] Start Line";
                case FINISH -> "[F] Finish Line";
                case INTERSECTION -> "[X] Intersection";
                default -> null;
            };
            if (consoleSpecial != null && logSpecial != null) {
                String consoleLine = "  " + consoleSpecial + ": 位置 " + entry.getKey();
                String logLine = "  " + logSpecial + ": Location " + entry.getKey();
                System.out.println(consoleLine);
                writeToLog(logLine);
                foundSpecial = true;
            }
        }
        if (!foundSpecial) {
            System.out.println("  未找到特殊段");
            writeToLog("  No special segments found");
        }

        // 系统状态
        System.out.println("\n🔧 系统状态:");
        System.out.println("  位置监听器: " + (positionListenerActive ? "✓ 活跃" : "✗ 非活跃"));
        System.out.println("  转换监听器: " + (transitionListenerActive ? "✓ 活跃" : "✗ 非活跃"));
        System.out.println("  总通知数: " + totalNotificationsReceived);
        System.out.println("  位置更新: " + positionUpdates.size());
        System.out.println("  轨道转换: " + transitionUpdates.size());

        writeToLog("System Status:");
        writeToLog("  Position Listener: " + (positionListenerActive ? "Active" : "Inactive"));
        writeToLog("  Transition Listener: " + (transitionListenerActive ? "Active" : "Inactive"));
        writeToLog("  Total Notifications: " + totalNotificationsReceived);
        writeToLog("  Position Updates: " + positionUpdates.size());
        writeToLog("  Track Transitions: " + transitionUpdates.size());
        writeToLog("===================================");
    }

    /**
     * 基本控制演示
     */
    private static void demonstrateBasicControl(Scanner scanner) {
        System.out.println("\n=== 基本控制演示 ===");
        System.out.println("📝 演示过程将记录到日志文件");
        System.out.println("按回车键开始演示...");
        scanner.nextLine();

        writeToLog("===== 基本控制演示开始 =====");

        // 设置速度
        System.out.println("设置速度为300...");
        writeToLog("设置速度: 300");
        vehicle.setSpeed(300);
        delay(3000);

        // 换道
        System.out.println("向左换道...");
        writeToLog("向左换道 (-0.3)");
        vehicle.changeLane(-0.3f);
        delay(2000);

        // 回到中间
        System.out.println("回到中间...");
        writeToLog("回到中间 (0.0)");
        vehicle.changeLane(0.0f);
        delay(2000);

        // 停车
        System.out.println("停车...");
        writeToLog("停车");
        vehicle.setSpeed(0);
        System.out.println("✓ 基本控制演示完成");
        writeToLog("基本控制演示完成");
        writeToLog("===== 基本控制演示结束 =====");
    }

    /**
     * 关闭日志文件
     */
    private static void closeLogFile() {
        if (logWriter != null) {
            writeToLog("程序结束");
            writeToLog("结束时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writeToLog("==========================================");
            logWriter.close();
            System.out.println("📝 日志文件已保存");
        }
    }

    // === 主方法 ===
    public static void main(String[] args) {
        System.out.println("===== Anki Overdrive 综合控制器 =====");

        // 初始化日志文件
        initializeLogFile();

        System.out.println("初始化蓝牙...");
        writeToLog("程序启动 - 初始化蓝牙");

        BluetoothManager manager = BluetoothManager.getBluetoothManager();
        List<BluetoothDevice> devices = manager.getDevices();

        // 查找Anki设备
        System.out.println("搜索Anki车辆:");
        List<BluetoothDevice> ankiDevices = new ArrayList<>();
        for (BluetoothDevice device : devices) {
            List<String> uuids = device.getUUIDs();
            if (uuids != null) {
                for (String uuid : uuids) {
                    if (uuid.toLowerCase().contains("beef")) {
                        ankiDevices.add(device);
                        System.out.println((ankiDevices.size()) + ": MAC: " + device.getAddress() +
                                " [Anki车辆]");
                        writeToLog("发现Anki车辆: " + device.getAddress());
                        break;
                    }
                }
            }
        }

        if (ankiDevices.isEmpty()) {
            System.out.println("❌ 未找到Anki车辆");
            writeToLog("未找到Anki车辆");
            closeLogFile();
            return;
        }

        // 选择设备
        Scanner scanner = new Scanner(System.in);
        System.out.print("选择车辆 (1-" + ankiDevices.size() + "): ");
        int choice = scanner.nextInt();
        scanner.nextLine();

        if (choice < 1 || choice > ankiDevices.size()) {
            System.out.println("❌ 无效选择");
            writeToLog("无效的车辆选择: " + choice);
            closeLogFile();
            return;
        }

        BluetoothDevice selectedDevice = ankiDevices.get(choice - 1);
        System.out.println("🚗 已选择车辆: " + selectedDevice.getAddress());
        writeToLog("选择车辆: " + selectedDevice.getAddress());

        // 建立连接
        System.out.println("连接中...");
        boolean connected = selectedDevice.connect();
        System.out.println("连接: " + (connected ? "✓ 成功" : "❌ 失败"));
        writeToLog("连接状态: " + (connected ? "成功" : "失败"));

        if (!connected) {
            closeLogFile();
            scanner.close();
            return;
        }

        delay(1000);

        // 创建Vehicle对象
        System.out.println("创建Vehicle对象...");
        vehicle = new Vehicle(selectedDevice);

        // 初始化
        System.out.println("等待初始化...");
        for (int i = 0; i < 10; i++) {
            System.out.print(".");
            delay(500);
        }
        System.out.println();

        System.out.println("初始化车辆特性...");
        boolean initialized = vehicle.initializeCharacteristics();
        System.out.println("初始化: " + (initialized ? "✓ 成功" : "❌ 失败"));
        writeToLog("车辆初始化: " + (initialized ? "成功" : "失败"));

        if (!initialized) {
            System.out.println("❌ 无法初始化车辆");
            writeToLog("无法初始化车辆 - 程序退出");
            closeLogFile();
            scanner.close();
            return;
        }

        // 配置事件监听器
        setupListeners();

        // 主菜单
        boolean exit = false;
        while (!exit) {
            System.out.println("\n===== 🚗 Anki 车辆控制器 =====");
            System.out.println("1: 📊 检查状态");
            System.out.println("2: 🏃 设置速度");
            System.out.println("3: ↔️ 车道变换");
            System.out.println("4: 🗺️ 轨道映射");
            System.out.println("5: 🎮 基本控制演示");
            System.out.println("6: 🧪 特殊测试");
            System.out.println("7: 📋 轨道报告");
            System.out.println("8: 🔔 通知测试");
            System.out.println("9: ❌ 退出");
            System.out.println("📝 注意: 车辆活动日志正在记录到文件中");

            System.out.print("选择: ");

            int cmd = scanner.nextInt();
            scanner.nextLine();

            writeToLog("用户选择菜单项: " + cmd);

            switch (cmd) {
                case 1 -> {
                    // 检查状态
                    System.out.println("\n📊 车辆状态:");
                    System.out.println("  🔗 连接: " + (vehicle.isConnected() ? "✓ 已连接" : "❌ 断开"));
                    System.out.println("  ⚡ 准备就绪: " + (vehicle.isReadyToStart() ? "✓ 是" : "❌ 否"));
                    System.out.println("  🔋 充电器: " + (vehicle.isOnCharger() ? "✓ 是" : "❌ 否"));
                    System.out.println("  🏃 速度: " + vehicle.getSpeed());
                    System.out.println("  📍 位置: " + (currentLocation == -1 ? "未知" : currentLocation));
                    System.out.println("  🛣️ 轨道类型: " + (currentRoadPiece == null ? "未知" : currentRoadPiece));
                    System.out.println("  🗺️ 已映射段: " + trackMap.size());
                    System.out.println("  📊 通知数: " + totalNotificationsReceived);

                    // 记录状态查询到日志
                    writeToLog("===== 状态查询 =====");
                    writeToLog("连接状态: " + (vehicle.isConnected() ? "已连接" : "断开"));
                    writeToLog("准备就绪: " + (vehicle.isReadyToStart() ? "是" : "否"));
                    writeToLog("充电器状态: " + (vehicle.isOnCharger() ? "在充电器上" : "不在充电器上"));
                    writeToLog("当前速度: " + vehicle.getSpeed());
                    writeToLog("当前位置: " + (currentLocation == -1 ? "未知" : String.valueOf(currentLocation)));
                    writeToLog("当前轨道类型: " + (currentRoadPiece == null ? "未知" : currentRoadPiece.toString()));
                    writeToLog("已映射轨道段数: " + trackMap.size());
                    writeToLog("总通知数: " + totalNotificationsReceived);
                    writeToLog("==================");
                }
                case 2 -> {
                    // 设置速度
                    System.out.print("速度 (0-1000): ");
                    int speed = scanner.nextInt();
                    scanner.nextLine();

                    try {
                        vehicle.setSpeed(speed);
                        System.out.println("✓ 速度已设置: " + speed);
                        writeToLog("设置速度: " + speed);
                    } catch (Exception e) {
                        String errorMsg = "❌ 设置速度错误: " + e.getMessage();
                        System.out.println(errorMsg);
                        writeToLog("ERROR - " + errorMsg);
                    }
                }
                case 3 -> {
                    // 车道变换
                    System.out.print("车道偏移 (-1.0 到 1.0): ");
                    float offset = scanner.nextFloat();
                    scanner.nextLine();

                    try {
                        vehicle.changeLane(offset);
                        System.out.println("✓ 车道变换完成: " + offset);
                        writeToLog("车道变换: " + offset);
                    } catch (Exception e) {
                        String errorMsg = "❌ 车道变换错误: " + e.getMessage();
                        System.out.println(errorMsg);
                        writeToLog("ERROR - " + errorMsg);
                    }
                }
                case 4 -> startTrackMapping(scanner);
                case 5 -> demonstrateBasicControl(scanner);
                case 6 -> performSpecialTest(scanner);
                case 7 -> generateTrackReport();
                case 8 -> testNotificationSystem(scanner);
                case 9 -> {
                    exit = true;
                    System.out.println("🛑 程序退出");
                    vehicle.setSpeed(0);
                    writeToLog("程序正常退出");
                }
                default -> {
                    System.out.println("❌ 无效选择");
                    writeToLog("无效的菜单选择: " + cmd);
                }
            }
        }

        // 清理资源
        closeLogFile();
        scanner.close();
    }
}