package de.pdbm.anki.example;

import de.pdbm.anki.api.AnkiController;
import de.pdbm.anki.impl.AnkiControllerImpl;
import de.pdbm.anki.gui.TrackMapVisualizer;
import de.pdbm.anki.tracking.SimpleTrackMapper;
import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;
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
    private TrackMapVisualizer visualizer;
    private AnkiController controller;
    private SimpleTrackMapper mapper;

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

            System.out.println("\n💡 GUI window is still open. Close it to exit.");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            updateVisualizerStatus("❌ 错误：" + e.getMessage());
        } finally {
            if (controller != null) {
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
     * 处理小车位置更新
     */
    private void handlePositionUpdate(PositionUpdate update) {
        int locationId = update.getLocation();

        // 先尝试从已映射的轨道片段中查找
        TrackPiece piece = findTrackPieceByLocation(locationId);

        if (piece != null) {
            // 找到对应的轨道片段，更新小车位置
            updateVehicleOnTrack(piece, update);
        }
    }

    /**
     * 根据 locationId 查找对应的轨道片段
     */
    private TrackPiece findTrackPieceByLocation(int locationId) {
        // 由于一个轨道片段可能包含多个location ID
        // 我们简化处理：直接找第一个匹配的片段
        // 这个方法可能需要根据实际情况优化

        List<TrackPiece> pieces = mapper.getTrackPieces();
        if (pieces.isEmpty()) {
            return null;
        }

        // 简化方案：根据location ID的范围估算对应哪个片段
        // 这里我们假设每个片段大约有4-6个location
        int pieceIndex = Math.min(locationId / 5, pieces.size() - 1);
        return pieces.get(pieceIndex);
    }

    /**
     * 在轨道上更新小车位置
     */
    private void updateVehicleOnTrack(TrackPiece piece, PositionUpdate update) {
        // 计算屏幕坐标
        List<TrackPiece> pieces = mapper.getTrackPieces();

        int minX = pieces.stream().mapToInt(p -> p.x).min().orElse(0);
        int maxY = pieces.stream().mapToInt(p -> p.y).max().orElse(0);

        // 标准化坐标（与TrackMapVisualizer中的渲染逻辑一致）
        int normalizedX = piece.x - minX;
        int normalizedY = maxY - piece.y;

        double screenX = normalizedX * 150;  // TILE_SIZE = 150
        double screenY = normalizedY * 150;

        // 更新小车位置和方向
        visualizer.updateVehiclePosition(screenX, screenY);
        if (piece.exitDirection != null) {
            visualizer.updateVehicleDirection(piece.exitDirection);
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
