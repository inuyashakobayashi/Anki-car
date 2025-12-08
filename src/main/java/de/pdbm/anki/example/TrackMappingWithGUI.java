package de.pdbm.anki.example;

import de.pdbm.anki.api.AnkiController;
import de.pdbm.anki.impl.AnkiControllerImpl;
import de.pdbm.anki.gui.TrackMapVisualizer;
import de.pdbm.anki.gui.VehicleDashboard;
import de.pdbm.anki.tracking.SimpleTrackMapper;
import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;
import de.pdbm.anki.tracking.TrackMapData;
import de.pdbm.anki.tracking.TrackMapIO;
import de.pdbm.janki.RoadPiece;
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
    private static final int TILE_SIZE = 250; // 需与 Visualizer 中的一致

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

    // 追踪每辆车当前所在的片段索引 (Key: vehicleId, Value: pieceIndex)
    private final Map<String, Integer> vehicleCurrentPiece = new ConcurrentHashMap<>();
    // 追踪每辆车在当前片段内的最大进度（用于过滤后退噪声）
    private final Map<String, Double> vehicleMaxProgress = new ConcurrentHashMap<>();
    // 每辆车自己的 locationId 映射 (Key: vehicleId, Value: 该车的 TrackMapData)
    private final Map<String, TrackMapData> vehicleTrackMaps = new ConcurrentHashMap<>();
    // 正在校准的车辆
    private final Map<String, SimpleTrackMapper> vehicleCalibrationMappers = new ConcurrentHashMap<>();

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
        scrollPane.setPrefWidth(480); // 仪表盘宽度 (扩大以适应更大的 Tile)
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
     * 配置车辆行为：每辆车都需要建图以获取自己的 locationId 映射
     */
    private void configureVehicleBehavior(AnkiController controller, String address) {
        Vehicle vehicle = controller.getVehicle();
        if (vehicle == null) return;

        // 策略：每辆车都需要跑一圈建图，获取自己的 locationId 映射
        boolean isFirstMapper = (connectedVehicles.size() == 1) && !mappingFinished;
        boolean needsCalibration = !vehicleTrackMaps.containsKey(address);

        if (isFirstMapper) {
            // 第一辆车：完整建图
            System.out.println("🗺️ 车辆 " + address + " 指定为【主建图车辆】");
            Platform.runLater(() -> updateStatus("开始建图: " + address + " 正在行驶..."));
            startMappingRun(controller);
        } else if (needsCalibration && mappingFinished) {
            // 后续车辆：校准建图（使用已知的轨道结构）
            System.out.println("🔧 车辆 " + address + " 开始【校准建图】");
            Platform.runLater(() -> updateStatus("校准中: " + address + " 正在跑一圈..."));
            startCalibrationRun(controller, address);
        } else {
            System.out.println("🏁 车辆 " + address + " 已就绪");
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
     * 启动校准建图流程（后续车辆用）
     * 使用已知的轨道结构，只记录该车的 locationId 映射
     */
    private void startCalibrationRun(AnkiController controller, String vehicleId) {
        // 为这辆车创建独立的 mapper
        SimpleTrackMapper calibrationMapper = new SimpleTrackMapper();
        vehicleCalibrationMappers.put(vehicleId, calibrationMapper);

        calibrationMapper.startMapping(new SimpleTrackMapper.TrackMappingCallback() {
            @Override
            public void onTrackComplete(List<TrackPiece> pieces) {
                System.out.println("🎉 车辆 " + vehicleId + " 校准完成！");

                // 停止车辆
                controller.stopTrackMapping();
                controller.stop();

                // 存储这辆车专属的地图数据
                TrackMapData vehicleMapData = new TrackMapData(pieces);
                vehicleTrackMaps.put(vehicleId, vehicleMapData);

                // 清理
                vehicleCalibrationMappers.remove(vehicleId);

                // 打印 location 范围
                System.out.println("\n📍 车辆 " + vehicleId + " 的 Location 范围:");
                for (int i = 0; i < pieces.size(); i++) {
                    TrackPiece p = pieces.get(i);
                    System.out.printf("  #%d: %s ID=%d  Location:[%d-%d]\n",
                            i, p.roadPiece, p.roadPieceId,
                            p.startLocation, p.endLocation);
                }

                Platform.runLater(() -> updateStatus("✅ 车辆 " + vehicleId + " 校准完成！"));
            }

            @Override
            public void onPieceAdded(TrackPiece piece) {
                // 校准时不更新地图显示（地图已经有了）
            }
        });

        // 开始行驶校准
        controller.startTrackMapping(MAPPING_SPEED, calibrationMapper);
    }

    /**
     * 检查所有已连接车辆，为未校准的车辆启动校准
     */
    private void startPendingCalibrations() {
        for (Map.Entry<String, AnkiController> entry : connectedVehicles.entrySet()) {
            String address = entry.getKey();
            AnkiController controller = entry.getValue();

            // 跳过已有地图数据的车辆
            if (vehicleTrackMaps.containsKey(address)) {
                continue;
            }

            // 跳过正在校准的车辆
            if (vehicleCalibrationMappers.containsKey(address)) {
                continue;
            }

            System.out.println("🔧 检测到未校准车辆: " + address + "，启动校准...");
            Platform.runLater(() -> updateStatus("校准中: " + address + " 正在跑一圈..."));
            startCalibrationRun(controller, address);
        }
    }

    /**
     * 启动建图流程 (仅对第一辆车)
     */
    private void startMappingRun(AnkiController controller) {
        // 设置建图回调
        sharedMapper.startMapping(new SimpleTrackMapper.TrackMappingCallback() {
            @Override
            public void onTrackComplete(List<TrackPiece> pieces) {
                String vehicleId = controller.getVehicle().getMacAddress();
                System.out.println("🎉 车辆 " + vehicleId + " 轨道闭合！建图完成！");
                mappingFinished = true;

                // 停止建图车
                controller.stopTrackMapping();
                controller.stop();

                // 生成并保存地图数据
                finalTrackData = new TrackMapData(pieces);
                // 同时为这辆车存储专属的地图数据
                vehicleTrackMaps.put(vehicleId, finalTrackData);

                // 调试：打印每个片段的 location 范围
                System.out.println("\n📍 轨道片段 Location 范围:");
                for (int i = 0; i < pieces.size(); i++) {
                    TrackPiece p = pieces.get(i);
                    System.out.printf("  #%d: (%d,%d) %s ID=%d  Location:[%d-%d]\n",
                            i, p.x, p.y, p.roadPiece, p.roadPieceId,
                            p.startLocation, p.endLocation);
                }
                System.out.println();

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

                // 建图完成后，检查其他已连接的车辆是否需要校准
                startPendingCalibrations();
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

    private void handleGlobalPositionUpdate(AnkiController controller, PositionUpdate update) {
        String vehicleId = controller.getVehicle().getMacAddress();

        // 必须有全局地图数据才能显示
        if (finalTrackData == null) return;

        // 获取该车自己的地图数据（用于 locationId 查找）
        TrackMapData vehicleData = vehicleTrackMaps.get(vehicleId);
        if (vehicleData == null) {
            // 还没校准，暂时不显示
            return;
        }

        List<TrackPiece> vehiclePieces = vehicleData.getPieces();
        List<TrackPiece> canonicalPieces = finalTrackData.getPieces();
        int pieceCount = vehiclePieces.size();

        int locationId = update.getLocation();
        int roadPieceId = update.getRoadPieceId();

        // 获取当前片段索引
        int currentIndex = vehicleCurrentPiece.getOrDefault(vehicleId, -1);

        int matchedIndex = -1;
        double progress = 0.5;

        // 优先在当前和相邻片段中搜索（只匹配 roadPieceId）
        if (currentIndex >= 0) {
            int[] searchOrder = {
                currentIndex,
                (currentIndex + 1) % pieceCount,
                (currentIndex - 1 + pieceCount) % pieceCount
            };

            for (int idx : searchOrder) {
                TrackPiece piece = vehiclePieces.get(idx);
                if (isRoadPieceIdMatch(piece.roadPieceId, roadPieceId)) {
                    matchedIndex = idx;
                    // 计算进度（使用该车自己的 locationId 范围）
                    int start = Math.min(piece.startLocation, piece.endLocation);
                    int end = Math.max(piece.startLocation, piece.endLocation);
                    if (end > start && locationId >= start && locationId <= end) {
                        progress = (locationId - start) / (double)(end - start);
                    }
                    break;
                }
            }
        }

        // 如果相邻搜索失败，全局搜索（首次定位）
        if (matchedIndex < 0) {
            for (int i = 0; i < pieceCount; i++) {
                TrackPiece piece = vehiclePieces.get(i);
                if (isRoadPieceIdMatch(piece.roadPieceId, roadPieceId)) {
                    int start = Math.min(piece.startLocation, piece.endLocation);
                    int end = Math.max(piece.startLocation, piece.endLocation);
                    // 优先选择 locationId 在范围内的
                    if (locationId >= start && locationId <= end) {
                        matchedIndex = i;
                        if (end > start) {
                            progress = (locationId - start) / (double)(end - start);
                        }
                        break;
                    }
                }
            }
        }

        if (matchedIndex < 0 || matchedIndex >= canonicalPieces.size()) return;

        // 过滤同一片段内的后退（防止 3→2→3 的抖动）
        int previousIndex = vehicleCurrentPiece.getOrDefault(vehicleId, -1);
        double previousMaxProgress = vehicleMaxProgress.getOrDefault(vehicleId, 0.0);

        if (matchedIndex == previousIndex) {
            // 还在同一片段内，进度只能向前，不能后退
            if (progress < previousMaxProgress) {
                // 后退了，忽略这次更新（使用之前的最大进度）
                progress = previousMaxProgress;
            } else {
                // 前进了，更新最大进度
                vehicleMaxProgress.put(vehicleId, progress);
            }
        } else {
            // 换了片段，重置进度追踪
            vehicleCurrentPiece.put(vehicleId, matchedIndex);
            vehicleMaxProgress.put(vehicleId, progress);
        }

        // 用相同索引从全局地图获取"标准"片段（坐标正确）
        TrackPiece displayPiece = canonicalPieces.get(matchedIndex);

        // 计算屏幕坐标
        int minX = canonicalPieces.stream().mapToInt(p -> p.x).min().orElse(0);
        int maxY = canonicalPieces.stream().mapToInt(p -> p.y).max().orElse(0);

        double tileOriginX = (displayPiece.x - minX) * TILE_SIZE;
        double tileOriginY = (maxY - displayPiece.y) * TILE_SIZE;

        TrajectoryCalculator.TrajectoryPoint localPoint = TrajectoryCalculator.calculatePoint(
                displayPiece.roadPiece,
                displayPiece.enterDirection,
                displayPiece.exitDirection,
                progress,
                TILE_SIZE
        );

        double screenX = tileOriginX + localPoint.x;
        double screenY = tileOriginY + localPoint.y;

        visualizer.updateVehiclePosition(vehicleId, screenX, screenY);
        visualizer.updateVehicleAngle(vehicleId, localPoint.angle);
    }

    /**
     * 检查 roadPieceId 是否匹配（考虑 START=33 和 FINISH=34 互换）
     */
    private boolean isRoadPieceIdMatch(int pieceId, int queryId) {
        if (pieceId == queryId) return true;
        // START(33) 和 FINISH(34) 视为相同
        return (pieceId == 33 || pieceId == 34) && (queryId == 33 || queryId == 34);
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
    /**
     * 轨迹计算器：计算车子在 Tile 内的精确 (x,y) 坐标
     * * 针对 "车头朝左" 的图片进行了角度修正 (+180度)
     */
    public static class TrajectoryCalculator {

        public static class TrajectoryPoint {
            public double x, y, angle;
            public TrajectoryPoint(double x, double y, double angle) {
                this.x = x; this.y = y; this.angle = angle;
            }
        }

        public static TrajectoryPoint calculatePoint(RoadPiece type,
                                                     SimpleTrackMapper.Direction enter,
                                                     SimpleTrackMapper.Direction exit,
                                                     double progress,
                                                     double size) {

            if (type == RoadPiece.STRAIGHT || type == RoadPiece.START || type == RoadPiece.FINISH) {
                return calculateStraight(enter, progress, size);
            }

            if (type == RoadPiece.CORNER) {
                return calculateCurve(enter, exit, progress, size);
            }

            // INTERSECTION 默认直行
            return calculateStraight(enter, progress, size);
        }

        private static TrajectoryPoint calculateStraight(SimpleTrackMapper.Direction enter, double p, double s) {
            double m = s / 2.0;
            double x = m, y = m, angle = 0;

            // 修正逻辑：
            // 1. 计算出车子应该朝向的【地理角度】(右=0, 下=90, 左=180, 上=270)
            // 2. 因为原图是朝左(180)的，所以【显示角度】 = 【地理角度】 + 180

            switch (enter) {
                case POSITIVE_X: // 向右行驶 (地理角度 0)
                    x = p * s;
                    y = m;
                    angle = 0 + 180; // 修正后 180
                    break;
                case NEGATIVE_X: // 向左行驶 (地理角度 180)
                    x = s - (p * s);
                    y = m;
                    angle = 180 + 180; // 修正后 0
                    break;
                case POSITIVE_Y: // 向上行驶 (地理角度 270)
                    x = m;
                    y = s - (p * s);
                    angle = 270 + 180; // 修正后 90
                    break;
                case NEGATIVE_Y: // 向下行驶 (地理角度 90)
                    x = m;
                    y = p * s;
                    angle = 90 + 180; // 修正后 270
                    break;
            }
            return new TrajectoryPoint(x, y, angle);
        }

        private static TrajectoryPoint calculateCurve(SimpleTrackMapper.Direction enter, SimpleTrackMapper.Direction exit, double p, double s) {
            double r = s / 2.0;

            // 弯道计算：基于标准圆弧公式，计算出地理角度
            // 参数: 圆心(cx, cy), 半径r, 起始角度start, 扫过角度sweep, 进度p

            // 1. 右转: 向右进 -> 向下出
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) {
                return calcArc(0, s, r, 270, 90, p);
            }
            // 2. 左转: 向左进 -> 向下出
            else if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) {
                return calcArc(s, s, r, 270, -90, p);
            }
            // 3. 左转: 向右进 -> 向上出
            else if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) {
                return calcArc(0, 0, r, 90, -90, p);
            }
            // 4. 右转: 向左进 -> 向上出
            else if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) {
                return calcArc(s, 0, r, 90, 90, p);
            }
            // 5. 左转: 向下进 -> 向右出
            else if (enter == SimpleTrackMapper.Direction.NEGATIVE_Y && exit == SimpleTrackMapper.Direction.POSITIVE_X) {
                return calcArc(s, 0, r, 180, -90, p);
            }
            // 6. 右转: 向下进 -> 向左出
            else if (enter == SimpleTrackMapper.Direction.NEGATIVE_Y && exit == SimpleTrackMapper.Direction.NEGATIVE_X) {
                return calcArc(0, 0, r, 0, 90, p); // Start 0 (Right) -> 90 (Down)
            }
            // 7. 右转: 向上进 -> 向右出
            else if (enter == SimpleTrackMapper.Direction.POSITIVE_Y && exit == SimpleTrackMapper.Direction.POSITIVE_X) {
                return calcArc(s, s, r, 180, 90, p);
            }
            // 8. 左转: 向上进 -> 向左出
            else if (enter == SimpleTrackMapper.Direction.POSITIVE_Y && exit == SimpleTrackMapper.Direction.NEGATIVE_X) {
                return calcArc(0, s, r, 0, -90, p);
            }

            return new TrajectoryPoint(s/2, s/2, 0);
        }

        private static TrajectoryPoint calcArc(double cx, double cy, double r, double startDeg, double sweepDeg, double p) {
            double currentDeg = startDeg + sweepDeg * p;
            double rad = Math.toRadians(currentDeg);

            // 计算位置 (基于 JavaFX 坐标系)
            double x = cx + r * Math.cos(rad);
            double y = cy + r * Math.sin(rad);

            // 计算地理车头角度 (切线方向)
            // 顺时针(sweep>0) -> 角度+90, 逆时针(sweep<0) -> 角度-90
            double geoAngle = currentDeg + (sweepDeg > 0 ? 90 : -90);

            // === 修正车头朝左原图 ===
            double finalAngle = geoAngle + 180;

            return new TrajectoryPoint(x, y, finalAngle);
        }
    }
}
