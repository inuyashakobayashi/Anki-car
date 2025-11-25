package de.pdbm.anki.gui;

import de.pdbm.anki.tracking.SimpleTrackMapper;
import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;
import de.pdbm.janki.RoadPiece;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轨道地图可视化组件
 * 负责渲染轨道地图和车辆位置，支持平滑动画
 */
public class TrackMapVisualizer {

    private static final int TILE_SIZE = 250; // 需与 TrackMappingWithGUI 中的一致
    private static final int CAR_SIZE = 100;  // 小车图标大小
    private static final double ANIMATION_DURATION = 200; // 动画时长 (毫秒)

    private final Pane trackPane;

    // 缓存
    private final Map<String, ImageView> pieceViews = new HashMap<>();
    private final Map<String, ImageView> vehicleViews = new HashMap<>();

    // 动画状态缓存 (Key: vehicleId)
    private final Map<String, Timeline> positionAnimations = new HashMap<>();
    private final Map<String, Timeline> rotationAnimations = new HashMap<>();

    private final String[] carImages = {"car1.png", "car2.png"};

    public TrackMapVisualizer() {
        trackPane = new Pane();
        trackPane.setBackground(new Background(new BackgroundFill(Color.web("#2b2b2b"), null, null)));

        // 鼠标点击调试 (保留，方便你后续校准)
        trackPane.setOnMouseClicked(e -> {
            System.out.printf("🖱️ [DEBUG] 点击: (%.2f, %.2f)\n", e.getX(), e.getY());
        });
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
     * 平滑更新车辆位置
     */
    public void updateVehiclePosition(String vehicleId, double screenX, double screenY) {
        Platform.runLater(() -> {
            ImageView view = getOrCreateVehicleView(vehicleId);
            if (view == null) return;

            double targetX = screenX - CAR_SIZE / 2.0;
            double targetY = screenY - CAR_SIZE / 2.0;

            // 距离太远则瞬移 (比如初始化)
            double dist = Math.sqrt(Math.pow(targetX - view.getLayoutX(), 2) + Math.pow(targetY - view.getLayoutY(), 2));
            if (dist > TILE_SIZE * 2 || view.getLayoutX() < -500) {
                view.setLayoutX(targetX);
                view.setLayoutY(targetY);
                return;
            }

            // 停止旧动画
            if (positionAnimations.containsKey(vehicleId)) {
                positionAnimations.get(vehicleId).stop();
            }

            // 启动新动画
            Timeline timeline = new Timeline();
            KeyValue kvX = new KeyValue(view.layoutXProperty(), targetX, Interpolator.LINEAR);
            KeyValue kvY = new KeyValue(view.layoutYProperty(), targetY, Interpolator.LINEAR);
            KeyFrame kf = new KeyFrame(Duration.millis(ANIMATION_DURATION), kvX, kvY);
            timeline.getKeyFrames().add(kf);
            timeline.play();

            positionAnimations.put(vehicleId, timeline);
        });
    }

    /**
     * [关键修复] 直接更新车辆角度 (0-360度)
     * 配合 TrajectoryCalculator 使用
     */
    public void updateVehicleAngle(String vehicleId, double angle) {
        Platform.runLater(() -> {
            ImageView view = getOrCreateVehicleView(vehicleId);
            if (view == null) return;

            double currentAngle = view.getRotate();

            // 智能旋转计算 (寻找最短路径，处理 0/360 跳变)
            currentAngle = currentAngle % 360;
            if (currentAngle < 0) currentAngle += 360;

            double targetAngle = angle % 360;
            if (targetAngle < 0) targetAngle += 360;

            double diff = targetAngle - currentAngle;
            if (diff > 180) diff -= 360;
            if (diff < -180) diff += 360;

            double finalAngle = currentAngle + diff;

            // 停止旧动画
            if (rotationAnimations.containsKey(vehicleId)) {
                rotationAnimations.get(vehicleId).stop();
            }

            // 启动旋转动画
            Timeline timeline = new Timeline();
            KeyValue kvRot = new KeyValue(view.rotateProperty(), finalAngle, Interpolator.LINEAR);
            KeyFrame kf = new KeyFrame(Duration.millis(ANIMATION_DURATION), kvRot);
            timeline.getKeyFrames().add(kf);
            timeline.play();

            rotationAnimations.put(vehicleId, timeline);
        });
    }

    /**
     * 更新车辆方向 (保留旧接口，兼容性)
     */
    public void updateVehicleDirection(String vehicleId, SimpleTrackMapper.Direction direction) {
        Platform.runLater(() -> {
            // 如果已经用了 updateVehicleAngle，这个方法通常可以忽略，或者作为 fallback
            // 这里简单的将其转换为角度调用
            if (direction != null) {
                // 注意：这里的角度可能需要根据你的车头朝向调整
                double angle = getDirectionRotation(direction);
                updateVehicleAngle(vehicleId, angle);
            }
        });
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