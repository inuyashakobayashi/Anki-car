package de.pdbm.anki.example;

import de.pdbm.anki.api.AnkiController;
import de.pdbm.anki.impl.AnkiControllerImpl;
import de.pdbm.anki.gui.TrackMapVisualizer;
import de.pdbm.anki.gui.VehicleDashboard;
import de.pdbm.anki.tracking.SimpleTrackMapper;
import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;
import de.pdbm.anki.tracking.TrackMapData;
import de.pdbm.anki.tracking.TrackMapIO;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.notifications.PositionUpdate;
import de.pdbm.janki.notifications.PositionUpdateListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anki Overdrive 自动控制与可视化系统
 *
 * 功能特点：
 * 1. 自动扫描并连接多辆车
 * 2. 第一辆车负责跑圈建图
 * 3. 集成 TilesFX 仪表盘进行实时监控和控制
 * 4. 实时可视化所有车辆在地图上的位置
 */
public class TrackMappingWithGUI extends Application {

    private static final int MAPPING_SPEED = 300;
    private static final int TILE_SIZE = 150; // 需与 Visualizer 中的一致

    // GUI 组件
    private TrackMapVisualizer visualizer;
    private VBox dashboardContainer; // 右侧仪表盘容器
    private Label statusLabel;

    // 车辆管理 (Key: MAC地址)
    private final Map<String, AnkiController> connectedVehicles = new ConcurrentHashMap<>();
    private final Map<String, VehicleDashboard> vehicleDashboards = new ConcurrentHashMap<>();

    // 轨道映射相关
    private SimpleTrackMapper sharedMapper;
    private TrackMapData finalTrackData; // 建图完成后生成的地图数据

    // 系统状态
    private boolean mappingFinished = false;
    private boolean isScanning = true;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Anki Overdrive Control System");

        // 1. 初始化主布局
        BorderPane root = new BorderPane();
        root.setBackground(new Background(new BackgroundFill(Color.web("#222"), null, null)));

        // 2. 中央：轨道地图可视化
        visualizer = new TrackMapVisualizer();
        // 注意：需要在 TrackMapVisualizer 中实现 getTrackPane() 返回内部的 Pane
        root.setCenter(visualizer.getTrackPane());

        // 3. 右侧：车辆仪表盘区域 (带滚动条，防止车辆太多显示不下)
        dashboardContainer = new VBox(10);
        dashboardContainer.setPadding(new Insets(10));
        dashboardContainer.setStyle("-fx-background-color: #333;");

        ScrollPane scrollPane = new ScrollPane(dashboardContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #333; -fx-border-color: transparent;");
        scrollPane.setPrefWidth(320); // 仪表盘宽度
        root.setRight(scrollPane);

        // 4. 顶部：状态栏
        statusLabel = new Label("系统初始化...");
        statusLabel.setTextFill(Color.WHITE);
        statusLabel.setPadding(new Insets(10));
        statusLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        root.setTop(statusLabel);

        // 5. 显示窗口
        Scene scene = new Scene(root, 1400, 900);
        primaryStage.setScene(scene);
        primaryStage.show();

        // 6. 初始化映射器
        sharedMapper = new SimpleTrackMapper();

        // 7. 启动后台自动扫描线程
        Thread scanThread = new Thread(this::autoDiscoveryLoop);
        scanThread.setDaemon(true);
        scanThread.start();
    }

    /**
     * 自动发现循环：持续扫描并连接新车辆
     */
    private void autoDiscoveryLoop() {
        // 使用一个独立的 Controller 实例进行扫描
        AnkiController scanner = new AnkiControllerImpl();
        updateStatus("🚀 系统启动，正在自动扫描车辆...");

        while (isScanning) {
            try {
                System.out.println("🔍 正在扫描新设备...");
                List<String> foundDevices = scanner.scanDevices();

                for (String address : foundDevices) {
                    // 如果这辆车还没连接过，就尝试连接
                    if (!connectedVehicles.containsKey(address)) {
                        connectToNewVehicle(address);
                    }
                }

                // 每隔 5 秒扫描一次
                Thread.sleep(5000);
            } catch (Exception e) {
                System.err.println("扫描循环出错: " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * 连接到一辆新发现的车
     */
    private void connectToNewVehicle(String address) {
        Platform.runLater(() -> updateStatus("✨ 发现新车辆: " + address + "，正在连接..."));

        // 为每辆车创建一个独立的控制器
        AnkiController controller = new AnkiControllerImpl();
        boolean success = controller.connect(address);

        if (success) {
            System.out.println("✅ 连接成功: " + address);
            connectedVehicles.put(address, controller);

            // 在 GUI 线程中更新 UI
            Platform.runLater(() -> {
                // 1. 创建仪表盘并添加到侧边栏
                VehicleDashboard dashboard = new VehicleDashboard(controller);
                vehicleDashboards.put(address, dashboard);
                dashboardContainer.getChildren().add(dashboard);

                updateStatus("车辆已加入: " + address + " (总数: " + connectedVehicles.size() + ")");
            });

            // 配置这辆车的行为 (建图 vs 参赛)
            configureVehicleBehavior(controller, address);

        } else {
            System.err.println("❌ 连接失败: " + address);
        }
    }

    /**
     * 配置车辆行为：区分“建图车”和“参赛车”
     */
    private void configureVehicleBehavior(AnkiController controller, String address) {
        Vehicle vehicle = controller.getVehicle();
        if (vehicle == null) return;

        // 策略：第一辆连上的车负责建图，除非图已经建好了
        boolean isMapper = (connectedVehicles.size() == 1) && !mappingFinished;

        if (isMapper) {
            System.out.println("🗺️ 车辆 " + address + " 指定为【建图车辆】");
            Platform.runLater(() -> updateStatus("开始建图: " + address + " 正在行驶..."));
            startMappingRun(controller);
        } else {
            System.out.println("🏁 车辆 " + address + " 指定为【参赛车辆】");
            // 参赛车辆可以在这里做一些初始化，比如开灯
            vehicle.toggleAllLights(true);
        }

        // 注册位置监听器 (用于 GUI 可视化)
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                handleGlobalPositionUpdate(controller, update);
            }
        });
    }

    /**
     * 启动建图流程 (仅对第一辆车)
     */
    private void startMappingRun(AnkiController controller) {
        // 设置建图回调
        sharedMapper.startMapping(new SimpleTrackMapper.TrackMappingCallback() {
            @Override
            public void onTrackComplete(List<TrackPiece> pieces) {
                System.out.println("🎉 轨道闭合！建图完成！");
                mappingFinished = true;

                // 停止建图车
                controller.stopTrackMapping();
                controller.stop();

                // 生成并保存地图数据
                finalTrackData = new TrackMapData(pieces);

                // GUI 更新
                Platform.runLater(() -> {
                    visualizer.updateTrackMap(pieces);
                    updateStatus("✅ 地图构建完成！所有车辆已定位。");
                });

                // 保存文件
                try {
                    String path = TrackMapIO.saveMap(finalTrackData);
                    System.out.println("💾 地图已保存: " + path);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onPieceAdded(TrackPiece piece) {
                // 实时显示新发现的片段
                visualizer.updateTrackMap(sharedMapper.getTrackPieces());
            }
        });

        // 开始行驶建图
        controller.startTrackMapping(MAPPING_SPEED, sharedMapper);
    }

    /**
     * 全局位置处理：更新地图上的车辆图标和仪表盘数据
     */
    private void handleGlobalPositionUpdate(AnkiController controller, PositionUpdate update) {
        String vehicleId = controller.getVehicle().getMacAddress();

        // 1. 更新仪表盘 (如果有)
        // 注意：TilesFX 的 Gauge 不需要太频繁更新，这里是最实时的数据
        // 如果你在 VehicleDashboard 里没有单独监听 PositionUpdate，可以在这里调用
        // VehicleDashboard dashboard = vehicleDashboards.get(vehicleId);
        // if (dashboard != null) dashboard.updateSpeed(update.getSpeed()); // 假设 update 里有速度

        // 2. 更新地图位置 (仅当有地图数据时)
        if (finalTrackData != null) {
            // 查找当前位置对应的轨道片段
            TrackMapData.PieceLocationInfo info = finalTrackData.findPieceByLocationAndId(
                    update.getLocation(), update.getRoadPieceId());

            if (info != null) {
                TrackPiece piece = info.piece;

                // 计算屏幕坐标
                List<TrackPiece> pieces = finalTrackData.getPieces();
                int minX = pieces.stream().mapToInt(p -> p.x).min().orElse(0);
                int maxY = pieces.stream().mapToInt(p -> p.y).max().orElse(0);

                int normalizedX = piece.x - minX;
                int normalizedY = maxY - piece.y;

                // 居中显示在 Tile 上
                double screenX = normalizedX * TILE_SIZE + TILE_SIZE / 2.0;
                double screenY = normalizedY * TILE_SIZE + TILE_SIZE / 2.0;

                // 更新可视化器
                visualizer.updateVehiclePosition(vehicleId, screenX, screenY);

                if (piece.exitDirection != null) {
                    visualizer.updateVehicleDirection(vehicleId, piece.exitDirection);
                }
            }
        }
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
        });
    }

    @Override
    public void stop() {
        System.out.println("正在关闭系统...");
        isScanning = false;

        // 断开所有车辆连接并停止
        for (AnkiController c : connectedVehicles.values()) {
            c.stop();
            c.disconnect();
        }

        // 关闭可视化器资源
        if (visualizer != null) {
            // visualizer.close(); // 如果有资源需要释放
        }

        System.exit(0);
    }
}