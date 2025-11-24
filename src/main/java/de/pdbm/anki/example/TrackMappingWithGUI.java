package de.pdbm.anki.example;

import de.pdbm.anki.api.AnkiController;
import de.pdbm.anki.impl.AnkiControllerImpl;
import de.pdbm.anki.gui.TrackMapVisualizer;
import de.pdbm.anki.tracking.SimpleTrackMapper;
import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;
import de.pdbm.anki.tracking.TrackMapData;
import de.pdbm.anki.tracking.TrackMapIO;
import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.notifications.PositionUpdate;
import de.pdbm.janki.notifications.PositionUpdateListener;
import de.pdbm.janki.Vehicle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.List;
import java.util.Scanner;

/**
 * 带 GUI 可视化的轨道映射示例
 *
 * 使用真实的 PNG 图片显示检测到的轨道布局
 *
 * @author Zijian Ying
 */
public class TrackMappingWithGUI extends Application {

    private static final int MAPPING_SPEED = 350;
    private static final int TRACKING_SPEED = 300;
    private static final int TILE_SIZE = 150;  // Must match TrackMapVisualizer.TILE_SIZE

    private TrackMapVisualizer visualizer;
    private AnkiController controller;
    private SimpleTrackMapper mapper;
    private TrackMapData trackMapData;
    private boolean liveTrackingActive = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        System.out.println("=" .repeat(80));
        System.out.println(" Track Mapping with GUI Visualization");
        System.out.println("=".repeat(80));
        System.out.println();

        // 创建可视化器
        visualizer = new TrackMapVisualizer();
        visualizer.show("Anki Track Map - Real-time Visualization");

        // 在新线程中运行轨道映射
        new Thread(() -> runTrackMapping()).start();
    }

    private void runTrackMapping() {
        controller = new AnkiControllerImpl();
        mapper = new SimpleTrackMapper();

        try {
            // 1. 扫描设备
            System.out.println("🔍 Scanning for Anki vehicles...");
            List<String> devices = controller.scanDevices();

            if (devices.isEmpty()) {
                System.err.println("❌ No Anki vehicles found!");
                updateVisualizerStatus("❌ 未找到 Anki 车辆");
                return;
            }

            System.out.println("✓ Found " + devices.size() + " vehicle(s)");
            for (int i = 0; i < devices.size(); i++) {
                System.out.println("  [" + i + "] " + devices.get(i));
            }

            // 2. 选择设备
            Scanner scanner = new Scanner(System.in);
            int selectedIndex = 0;

            if (devices.size() > 1) {
                System.out.print("\n📝 Select vehicle (enter number 0-" + (devices.size() - 1) + "): ");
                try {
                    selectedIndex = Integer.parseInt(scanner.nextLine().trim());
                    if (selectedIndex < 0 || selectedIndex >= devices.size()) {
                        System.err.println("❌ Invalid selection! Using first vehicle.");
                        selectedIndex = 0;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("❌ Invalid input! Using first vehicle.");
                    selectedIndex = 0;
                }
            }

            String selectedDevice = devices.get(selectedIndex);
            System.out.println("\n🔗 Connecting to: " + selectedDevice);
            updateVisualizerStatus("🔗 正在连接到车辆...");

            if (!controller.connect(selectedDevice)) {
                System.err.println("❌ Failed to connect!");
                updateVisualizerStatus("❌ 连接失败");
                return;
            }
            System.out.println("✓ Connected successfully!\n");

            // 3. 等待用户准备
            System.out.println("📋 Instructions:");
            System.out.println("  1. Place the vehicle on the track");
            System.out.println("  2. The vehicle will drive at speed " + MAPPING_SPEED);
            System.out.println("  3. Watch the GUI window for real-time track visualization");
            System.out.print("\nPress ENTER when ready...");
            scanner.nextLine();

            // 4. 开始建图
            System.out.println("\n" + "=".repeat(80));
            System.out.println("🗺️  STARTING TRACK MAPPING");
            System.out.println("=".repeat(80));
            System.out.println();

            updateVisualizerStatus("🗺️ 正在检测轨道...");

            final boolean[] mappingComplete = {false};

            mapper.startMapping(new SimpleTrackMapper.TrackMappingCallback() {
                @Override
                public void onTrackComplete(List<TrackPiece> pieces) {
                    mappingComplete[0] = true;
                    System.out.println("\n🎉 Track mapping complete!");

                    // 更新GUI显示完整轨道
                    visualizer.updateTrackMap(pieces);
                    updateVisualizerStatus("✅ 轨道检测完成！");
                }

                @Override
                public void onPieceAdded(TrackPiece piece) {
                    // 实时更新GUI
                    System.out.println("  ➕ Added piece: " + piece);
                    visualizer.updateTrackMap(mapper.getTrackPieces());
                    updateVisualizerStatus(String.format("检测到 %d 个片段...",
                            mapper.getTrackPieces().size()));
                }
            });

            controller.startTrackMapping(MAPPING_SPEED, mapper);

            // TODO: 实时位置更新功能暂时禁用，先完成轨道映射
            // 等轨道映射完全正确后再启用
            /*
            Vehicle vehicle = controller.getVehicle();
            if (vehicle != null) {
                vehicle.addNotificationListener(new PositionUpdateListener() {
                    @Override
                    public void onPositionUpdate(PositionUpdate update) {
                        handlePositionUpdate(update);
                    }
                });
            }
            */

            // 5. 等待映射完成
            System.out.println("⏳ Mapping in progress...");
            System.out.println("   Watch the GUI window for real-time updates");
            System.out.println("   Press ENTER to stop manually\n");

            long startTime = System.currentTimeMillis();
            long timeout = 60000; // 60秒超时

            // 后台线程等待用户输入
            Thread inputThread = new Thread(() -> {
                System.out.println("💡 Tip: Press ENTER to stop mapping");
                try {
                    scanner.nextLine();
                    mappingComplete[0] = true;
                    System.out.println("\n⏹️ Manual stop requested...");
                } catch (Exception e) {
                    // Ignore
                }
            });
            inputThread.setDaemon(true);
            inputThread.start();

            while (!mappingComplete[0]) {
                try {
                    Thread.sleep(100);

                    if (System.currentTimeMillis() - startTime > timeout) {
                        System.out.println("\n⏰ Timeout reached (60s), stopping...");
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }

            // 6. 停止小车
            mapper.stopMapping();
            controller.stopTrackMapping();
            System.out.println("\n✓ Mapping stopped!");

            // 7. 显示最终结果
            System.out.println();
            mapper.printReport();

            // 更新最终GUI
            visualizer.updateTrackMap(mapper.getTrackPieces());
            updateVisualizerStatus(String.format("✅ 完成！检测到 %d 个片段",
                    mapper.getTrackPieces().size()));

            // 保存地图
            try {
                trackMapData = new TrackMapData(mapper.getTrackPieces());
                trackMapData.printStats();
                String savedPath = TrackMapIO.saveMap(trackMapData);
                System.out.println("💾 Map saved to: " + savedPath);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to save map: " + e.getMessage());
            }

            // 询问是否开始实时追踪
            System.out.println("\n" + "=".repeat(80));
            System.out.println("🚗 Live Tracking Mode");
            System.out.println("=".repeat(80));
            System.out.print("\nStart live tracking? (Y/n): ");

            Scanner trackingScanner = new Scanner(System.in);
            String response = "";
            try {
                response = trackingScanner.nextLine().trim().toLowerCase();
            } catch (Exception e) {
                System.err.println("⚠️ Failed to read input, defaulting to 'no'");
                response = "n";
            }

            if (response.isEmpty() || response.equals("y") || response.equals("yes")) {
                liveTrackingActive = true;
                startLiveTracking();
            } else {
                System.out.println("\n💡 GUI window is still open. Close it to exit.");
            }

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            updateVisualizerStatus("❌ 错误：" + e.getMessage());
        } finally {
            // 如果在实时追踪模式，不要断开连接
            if (!liveTrackingActive && controller != null) {
                controller.disconnect();
                System.out.println("\n👋 Disconnected. GUI window remains open.");
            }
        }
    }

    private void updateVisualizerStatus(String message) {
        if (visualizer != null) {
            Platform.runLater(() -> visualizer.updateStatus(message));
        }
    }

    /**
     * 启动实时追踪模式
     */
    private void startLiveTracking() {
        if (trackMapData == null) {
            System.err.println("❌ No map data available for tracking!");
            return;
        }

        System.out.println("\n🚗 Starting live tracking mode...");
        System.out.println("   Speed: " + TRACKING_SPEED);
        System.out.println("   Press Ctrl+C to stop\n");

        updateVisualizerStatus("🚗 实时追踪模式");

        // 启用小车显示
        visualizer.enableVehicleDisplay();

        // 注册位置更新监听器
        Vehicle vehicle = controller.getVehicle();
        if (vehicle != null) {
            vehicle.addNotificationListener(new PositionUpdateListener() {
                @Override
                public void onPositionUpdate(PositionUpdate update) {
                    handleLivePositionUpdate(vehicle, update);
                }
            });
        }

        // 启动小车
        controller.setSpeed(TRACKING_SPEED);
        System.out.println("✓ Live tracking started!");
        System.out.println("   Vehicle is now being tracked in real-time");
        System.out.println("   Press Ctrl+C or close the GUI window to stop\n");
    }

    /**
     * 处理实时位置更新（使用 locationId + roadPieceId 精确定位）
     */
    private void handleLivePositionUpdate(Vehicle vehicle,PositionUpdate update) {
        int locationId = update.getLocation();
        int roadPieceId = update.getRoadPieceId();
        RoadPiece roadPieceType =update.getRoadPiece();
        String vehicleId = vehicle.getMacAddress(); // 获取 MAC 地址

        // 使用 (locationId, roadPieceId) 精确组合查找对应的 piece
        TrackMapData.PieceLocationInfo info = trackMapData.findPieceByLocationAndId(locationId, roadPieceId);

        if (info != null) {
            TrackPiece piece = info.piece;

            // 计算屏幕坐标（简单版：显示在piece中心）
            List<TrackPiece> pieces = trackMapData.getPieces();
            int minX = pieces.stream().mapToInt(p -> p.x).min().orElse(0);
            int maxY = pieces.stream().mapToInt(p -> p.y).max().orElse(0);

            // 标准化坐标
            int normalizedX = piece.x - minX;
            int normalizedY = maxY - piece.y;

            // 转换为屏幕坐标（tile中心）
            double screenX = normalizedX * TILE_SIZE + TILE_SIZE / 2.0;
            double screenY = normalizedY * TILE_SIZE + TILE_SIZE / 2.0;

// === 修改这里：调用带 vehicleId 的新方法 ===
            visualizer.updateVehiclePosition(vehicleId, screenX, screenY);

            if (piece.exitDirection != null) {
                visualizer.updateVehicleDirection(vehicleId, piece.exitDirection);
            }

            // 打印调试信息
            System.out.printf("📍 Loc: %d, ID: %d, Type: %s, Piece: (%d,%d), Screen: (%.0f,%.0f), Progress: %.2f\n",
                             locationId, piece.roadPieceId, roadPieceType, piece.x, piece.y, screenX, screenY, info.progress);
        } else {
            // 如果找不到对应的 piece，打印警告
            System.out.printf("⚠️ Location %d + ID:%d (%s) not found in map!\n", locationId, roadPieceId, roadPieceType);
        }
    }

    @Override
    public void stop() {
        System.out.println("Closing application...");
        if (controller != null) {
            controller.disconnect();
        }
        System.exit(0);
    }
}
