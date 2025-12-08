package de.pdbm.anki.gui;

import de.pdbm.anki.tracking.SimpleTrackMapper;
import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;
import de.pdbm.janki.RoadPiece;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轨道地图可视化组件
 * 负责渲染轨道地图和车辆位置，支持速度预测平滑动画
 */
public class TrackMapVisualizer {

    private static final int TILE_SIZE = 250; // 需与 TrackMappingWithGUI 中的一致
    private static final int CAR_SIZE = 100;  // 小车图标大小

    // 动画参数
    private static final double SMOOTHING_FACTOR = 0.08;  // 平滑系数 (越小越平滑，动画持续更久)
    private static final double PREDICTION_FACTOR = 1.8;  // 预测系数
    private static final double DEFAULT_SPEED = 0.5;      // 默认速度 (像素/毫秒)

    private final Pane trackPane;

    // 缓存
    private final Map<String, ImageView> pieceViews = new HashMap<>();
    private final Map<String, ImageView> vehicleViews = new HashMap<>();

    // 动画状态 (Key: vehicleId)
    private final Map<String, VehicleAnimationState> animationStates = new HashMap<>();

    // 全局动画计时器
    private AnimationTimer animationTimer;
    private long lastFrameTime = 0;

    private final String[] carImages = {"car1.png", "car2.png"};

    /**
     * 车辆动画状态
     */
    private static class VehicleAnimationState {
        // 当前显示位置
        double currentX, currentY;
        double currentAngle;

        // 目标位置 (来自传感器数据)
        double targetX, targetY;
        double targetAngle;

        // 速度估算
        double speedX, speedY;  // 像素/毫秒
        double estimatedSpeed;  // 总速度

        // 时间追踪
        long lastUpdateTime;

        // 是否已初始化
        boolean initialized = false;

        VehicleAnimationState() {
            this.lastUpdateTime = System.currentTimeMillis();
            this.estimatedSpeed = DEFAULT_SPEED;
        }
    }

    public TrackMapVisualizer() {
        trackPane = new Pane();
        trackPane.setBackground(new Background(new BackgroundFill(Color.web("#2b2b2b"), null, null)));

        // 鼠标点击调试 (保留，方便你后续校准)
        trackPane.setOnMouseClicked(e -> {
            System.out.printf("🖱️ [DEBUG] 点击: (%.2f, %.2f)\n", e.getX(), e.getY());
        });

        // 启动动画循环
        startAnimationLoop();
    }

    /**
     * 启动动画循环 - 每帧更新所有车辆位置
     */
    private void startAnimationLoop() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastFrameTime == 0) {
                    lastFrameTime = now;
                    return;
                }

                double deltaMs = (now - lastFrameTime) / 1_000_000.0; // 转换为毫秒
                lastFrameTime = now;

                // 更新所有车辆
                for (Map.Entry<String, VehicleAnimationState> entry : animationStates.entrySet()) {
                    String vehicleId = entry.getKey();
                    VehicleAnimationState state = entry.getValue();
                    ImageView view = vehicleViews.get(vehicleId);

                    if (view != null && state.initialized) {
                        updateVehicleFrame(view, state, deltaMs);
                    }
                }
            }
        };
        animationTimer.start();
    }

    /**
     * 每帧更新车辆位置 - 核心动画逻辑
     */
    private void updateVehicleFrame(ImageView view, VehicleAnimationState state, double deltaMs) {
        // 1. 计算当前位置到目标位置的差距
        double dx = state.targetX - state.currentX;
        double dy = state.targetY - state.currentY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance > 1) {  // 如果还没到达目标
            // 2. 基于速度预测的移动
            double moveDistance = state.estimatedSpeed * deltaMs * PREDICTION_FACTOR;

            // 3. 同时应用平滑追踪 (结合预测和追踪)
            double smoothX = dx * SMOOTHING_FACTOR;
            double smoothY = dy * SMOOTHING_FACTOR;

            // 4. 计算实际移动量 (取预测和平滑的较大值，确保不会太慢)
            double predictX = (distance > 0) ? (dx / distance) * moveDistance : 0;
            double predictY = (distance > 0) ? (dy / distance) * moveDistance : 0;

            // 混合策略：距离远时用预测，距离近时用平滑
            double blendFactor = Math.min(1.0, distance / 100.0);  // 100像素内开始混合
            double moveX = predictX * blendFactor + smoothX * (1 - blendFactor);
            double moveY = predictY * blendFactor + smoothY * (1 - blendFactor);

            // 5. 限制不要超过目标
            if (Math.abs(moveX) > Math.abs(dx)) moveX = dx;
            if (Math.abs(moveY) > Math.abs(dy)) moveY = dy;

            state.currentX += moveX;
            state.currentY += moveY;
        }

        // 6. 角度平滑
        double angleDiff = normalizeAngle(state.targetAngle - state.currentAngle);
        state.currentAngle += angleDiff * SMOOTHING_FACTOR * 2;  // 角度可以快一点

        // 7. 更新显示
        view.setLayoutX(state.currentX - CAR_SIZE / 2.0);
        view.setLayoutY(state.currentY - CAR_SIZE / 2.0);
        view.setRotate(state.currentAngle);
    }

    /**
     * 角度归一化到 [-180, 180]
     */
    private double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }

    public Pane getTrackPane() {
        return trackPane;
    }

    public void updateTrackMap(List<TrackPiece> pieces) {
        if (pieces == null || pieces.isEmpty()) return;

        Platform.runLater(() -> {
            trackPane.getChildren().clear();
            pieceViews.clear();

            int minX = pieces.stream().mapToInt(p -> p.x).min().orElse(0);
            int maxX = pieces.stream().mapToInt(p -> p.x).max().orElse(0);
            int minY = pieces.stream().mapToInt(p -> p.y).min().orElse(0);
            int maxY = pieces.stream().mapToInt(p -> p.y).max().orElse(0);

            for (TrackPiece piece : pieces) {
                renderTrackPiece(piece, minX, minY, maxY);
            }

            // 重新添加车辆
            for (ImageView carView : vehicleViews.values()) {
                trackPane.getChildren().add(carView);
            }
        });
    }

    /**
     * 更新车辆目标位置 - 传感器数据到达时调用
     * 动画循环会平滑地将车辆移动到目标位置
     */
    public void updateVehiclePosition(String vehicleId, double screenX, double screenY) {
        Platform.runLater(() -> {
            ImageView view = getOrCreateVehicleView(vehicleId);
            if (view == null) return;

            VehicleAnimationState state = animationStates.computeIfAbsent(vehicleId, k -> new VehicleAnimationState());

            long now = System.currentTimeMillis();
            long timeDelta = now - state.lastUpdateTime;

            // 首次初始化：直接跳到目标位置
            if (!state.initialized) {
                state.currentX = screenX;
                state.currentY = screenY;
                state.targetX = screenX;
                state.targetY = screenY;
                state.initialized = true;
                state.lastUpdateTime = now;
                return;
            }

            // 距离太远则瞬移 (比如轨道切换)
            double dist = Math.sqrt(Math.pow(screenX - state.currentX, 2) + Math.pow(screenY - state.currentY, 2));
            if (dist > TILE_SIZE * 2) {
                state.currentX = screenX;
                state.currentY = screenY;
                state.targetX = screenX;
                state.targetY = screenY;
                state.lastUpdateTime = now;
                return;
            }

            // 计算速度 (基于上次更新)
            if (timeDelta > 0 && timeDelta < 3000) {  // 合理的时间范围
                double dx = screenX - state.targetX;
                double dy = screenY - state.targetY;
                double movedDist = Math.sqrt(dx * dx + dy * dy);

                // 更新估算速度 (快速响应变化)
                double newSpeed = movedDist / timeDelta;
                if (newSpeed > 0.05 && newSpeed < 2.0) {  // 合理的速度范围
                    // 如果新速度更快，快速适应；如果变慢，慢慢降
                    if (newSpeed > state.estimatedSpeed) {
                        state.estimatedSpeed = state.estimatedSpeed * 0.3 + newSpeed * 0.7;  // 快速加速
                    } else {
                        state.estimatedSpeed = state.estimatedSpeed * 0.8 + newSpeed * 0.2;  // 慢慢减速
                    }
                }
            }

            // 更新目标位置
            state.targetX = screenX;
            state.targetY = screenY;
            state.lastUpdateTime = now;
        });
    }

    /**
     * 更新车辆目标角度 - 传感器数据到达时调用
     */
    public void updateVehicleAngle(String vehicleId, double angle) {
        Platform.runLater(() -> {
            getOrCreateVehicleView(vehicleId);  // 确保 view 存在
            VehicleAnimationState state = animationStates.computeIfAbsent(vehicleId, k -> new VehicleAnimationState());

            // 首次设置角度
            if (!state.initialized) {
                state.currentAngle = angle;
            }

            state.targetAngle = angle;
        });
    }

    /**
     * 更新车辆方向 (保留旧接口，兼容性)
     */
    public void updateVehicleDirection(String vehicleId, SimpleTrackMapper.Direction direction) {
        if (direction != null) {
            double angle = getDirectionRotation(direction);
            updateVehicleAngle(vehicleId, angle);
        }
    }

    private ImageView getOrCreateVehicleView(String vehicleId) {
        if (vehicleViews.containsKey(vehicleId)) {
            return vehicleViews.get(vehicleId);
        }

        int index = vehicleViews.size() % carImages.length;
        String imageName = carImages[index];
        Image image = ActualTrackImageLoader.getTrackImageByName(imageName);

        if (image == null) return null;

        ImageView view = new ImageView(image);
        view.setFitWidth(CAR_SIZE);
        view.setFitHeight(CAR_SIZE);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setLayoutX(-1000);
        view.setLayoutY(-1000);

        trackPane.getChildren().add(view);
        vehicleViews.put(vehicleId, view);
        return view;
    }

    private void renderTrackPiece(TrackPiece piece, int minX, int minY, int maxY) {
        Image image = getImageForPiece(piece);
        if (image == null) return;

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(TILE_SIZE);
        imageView.setFitHeight(TILE_SIZE);

        double rotation = getRotationForPiece(piece);
        imageView.setRotate(rotation);

        int normalizedX = piece.x - minX;
        int normalizedY = maxY - piece.y;

        double screenX = normalizedX * TILE_SIZE;
        double screenY = normalizedY * TILE_SIZE;

        imageView.setLayoutX(screenX);
        imageView.setLayoutY(screenY);

        trackPane.getChildren().add(imageView);
        pieceViews.put(piece.x + "," + piece.y, imageView);
    }

    private Image getImageForPiece(TrackPiece piece) {
        switch (piece.roadPiece) {
            case START: case FINISH: return ActualTrackImageLoader.getTrackImageByName("start.png");
            case STRAIGHT: return ActualTrackImageLoader.getTrackImageByName("straight0.png");
            case CORNER: return getCornerImage(piece);
            case INTERSECTION: return ActualTrackImageLoader.getTrackImageByName("intersection.png");
            default: return ActualTrackImageLoader.getTrackImageByName("straight0.png");
        }
    }

    private Image getCornerImage(TrackPiece piece) {
        if (piece.enterDirection == null || piece.exitDirection == null) {
            return ActualTrackImageLoader.getTrackImageByName("curve0.png");
        }
        String selectedImage = selectCurveByEnterAndExit(piece.enterDirection, piece.exitDirection);
        return ActualTrackImageLoader.getTrackImageByName(selectedImage);
    }

    /**
     * 选择弯道图片逻辑 (你可以根据需要改回你自己觉得正确的版本)
     * 这里保留一个比较通用的推测版本
     */
    private String selectCurveByEnterAndExit(SimpleTrackMapper.Direction enter, SimpleTrackMapper.Direction exit) {
        boolean isLeftTurn = (exit == enter.decrement());
        if (isLeftTurn) {
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) return "curve0.png";
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) return "curve1.png";
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) return "curve2.png";
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) return "curve3.png";
            if (enter == SimpleTrackMapper.Direction.POSITIVE_Y && exit == SimpleTrackMapper.Direction.NEGATIVE_X) return "curve3.png";
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_Y && exit == SimpleTrackMapper.Direction.POSITIVE_X) return "curve1.png";
        } else {
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) return "curve0.png";
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) return "curve1.png";
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) return "curve2.png";
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) return "curve3.png";
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_Y && exit == SimpleTrackMapper.Direction.NEGATIVE_X) return "curve0.png";
            if (enter == SimpleTrackMapper.Direction.POSITIVE_Y && exit == SimpleTrackMapper.Direction.POSITIVE_X) return "curve2.png";
        }
        return "curve0.png";
    }

    private double getRotationForPiece(TrackPiece piece) {
        if (piece.enterDirection == null) return 0;
        if (piece.roadPiece == RoadPiece.STRAIGHT || piece.roadPiece == RoadPiece.START || piece.roadPiece == RoadPiece.FINISH) {
            return getDirectionRotation(piece.enterDirection);
        }
        return 0;
    }

    /**
     * 计算直道旋转角度
     * 针对【横向原图】(straight0.png 是东西向) 的修正
     */
    private double getDirectionRotation(SimpleTrackMapper.Direction direction) {
        switch (direction) {
            case POSITIVE_X: return 0;    // 向右 -> 不转 (保持横向)
            case NEGATIVE_X: return 180;  // 向左 -> 转180 (保持横向)
            case POSITIVE_Y: return 270;  // 向上 -> 转270 (变竖直)
            case NEGATIVE_Y: return 90;   // 向下 -> 转90 (变竖直)
            default: return 0;
        }
    }
}