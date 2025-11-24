package de.pdbm.anki.gui;

import de.pdbm.anki.tracking.SimpleTrackMapper;
import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;
import de.pdbm.janki.RoadPiece;
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
 * 负责渲染轨道地图和车辆位置
 */
public class TrackMapVisualizer {

    private static final int TILE_SIZE = 250; // 需与 TrackMappingWithGUI 中的一致
    private static final int CAR_SIZE = 100;   // 小车图标大小

    private final Pane trackPane;

    // 缓存轨道片段的 ImageView (Key: "x,y")
    private final Map<String, ImageView> pieceViews = new HashMap<>();

    // 多车支持 (Key: MAC地址)
    private final Map<String, ImageView> vehicleViews = new HashMap<>();

    // 可用的车辆图片资源
    private final String[] carImages = {"car1.png", "car2.png"};

    /**
     * 构造函数：初始化画布
     */
    public TrackMapVisualizer() {
        trackPane = new Pane();
        // 设置深色背景，模拟赛道环境
        trackPane.setBackground(new Background(new BackgroundFill(Color.web("#2b2b2b"), null, null)));
    }

    /**
     * 获取显示面板 (用于嵌入主界面)
     */
    public Pane getTrackPane() {
        return trackPane;
    }

    /**
     * 更新显示的轨道地图
     */
    public void updateTrackMap(List<TrackPiece> pieces) {
        if (pieces == null || pieces.isEmpty()) return;

        Platform.runLater(() -> {
            // 清除旧的轨道片段 (保留车辆图标)
            // 注意：为了不闪烁，最好只增量更新，但为了简单这里先全量重绘轨道层
            // 实际操作中需要把车辆 View 提出来再加回去，或者分层管理
            // 这里简单处理：先全清，再重绘轨道，再重绘车辆

            trackPane.getChildren().clear();
            pieceViews.clear();

            // 找到坐标范围
            int minX = pieces.stream().mapToInt(p -> p.x).min().orElse(0);
            int maxX = pieces.stream().mapToInt(p -> p.x).max().orElse(0);
            int minY = pieces.stream().mapToInt(p -> p.y).min().orElse(0);
            int maxY = pieces.stream().mapToInt(p -> p.y).max().orElse(0);

            // 渲染每个轨道片段
            for (TrackPiece piece : pieces) {
                renderTrackPiece(piece, minX, minY, maxY);
            }

            // 重新添加所有车辆图标 (因为刚才 clear() 掉了)
            for (ImageView carView : vehicleViews.values()) {
                trackPane.getChildren().add(carView);
            }
        });
    }

    /**
     * 更新指定车辆的位置
     * @param vehicleId 车辆 MAC 地址
     * @param screenX 屏幕 X 坐标 (Tile 中心点)
     * @param screenY 屏幕 Y 坐标 (Tile 中心点)
     */
    public void updateVehiclePosition(String vehicleId, double screenX, double screenY) {
        Platform.runLater(() -> {
            ImageView view = getOrCreateVehicleView(vehicleId);
            if (view == null) return;

            // 居中显示：减去图标的一半大小
            double centerX = screenX - CAR_SIZE / 2.0;
            double centerY = screenY - CAR_SIZE / 2.0;

            view.setLayoutX(centerX);
            view.setLayoutY(centerY);
            view.toFront(); // 确保车在轨道上面
        });
    }

    /**
     * 更新指定车辆的方向
     */
    public void updateVehicleDirection(String vehicleId, SimpleTrackMapper.Direction direction) {
        Platform.runLater(() -> {
            ImageView view = getOrCreateVehicleView(vehicleId);
            if (view == null || direction == null) return;

            double rotation = getDirectionRotation(direction);
            view.setRotate(rotation);
        });
    }

    /**
     * 获取或创建车辆图标
     */
    private ImageView getOrCreateVehicleView(String vehicleId) {
        if (vehicleViews.containsKey(vehicleId)) {
            return vehicleViews.get(vehicleId);
        }

        // 分配图片 (轮询)
        int index = vehicleViews.size() % carImages.length;
        String imageName = carImages[index];
        Image image = ActualTrackImageLoader.getTrackImageByName(imageName);

        if (image == null) return null;

        ImageView view = new ImageView(image);
        view.setFitWidth(CAR_SIZE);
        view.setFitHeight(CAR_SIZE);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        // 初始位置在屏幕外
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

        // 坐标转换：将网格坐标映射到屏幕坐标
        // Y轴反转：网格Y向上，屏幕Y向下
        int normalizedX = piece.x - minX;
        int normalizedY = maxY - piece.y;

        double screenX = normalizedX * TILE_SIZE;
        double screenY = normalizedY * TILE_SIZE;

        imageView.setLayoutX(screenX);
        imageView.setLayoutY(screenY);

        trackPane.getChildren().add(imageView);
        pieceViews.put(piece.x + "," + piece.y, imageView);
    }

    // === 辅助方法：选择图片和计算旋转 (逻辑保持不变) ===

    private Image getImageForPiece(TrackPiece piece) {
        switch (piece.roadPiece) {
            case START: case FINISH:
                return ActualTrackImageLoader.getTrackImageByName("start.png");
            case STRAIGHT:
                return ActualTrackImageLoader.getTrackImageByName("straight0.png");
            case CORNER:
                return getCornerImage(piece);
            case INTERSECTION:
                return ActualTrackImageLoader.getTrackImageByName("intersection.png");
            default:
                return ActualTrackImageLoader.getTrackImageByName("straight0.png");
        }
    }

    private Image getCornerImage(TrackPiece piece) {
        if (piece.enterDirection == null || piece.exitDirection == null) {
            return ActualTrackImageLoader.getTrackImageByName("curve0.png");
        }
        String selectedImage = selectCurveByEnterAndExit(piece.enterDirection, piece.exitDirection);
        return ActualTrackImageLoader.getTrackImageByName(selectedImage);
    }

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
        // 只有直道需要旋转，弯道靠图片本身区分，路口不需要
        if (piece.roadPiece == RoadPiece.STRAIGHT || piece.roadPiece == RoadPiece.START || piece.roadPiece == RoadPiece.FINISH) {
            return getDirectionRotation(piece.enterDirection);
        }
        return 0;
    }

    private double getDirectionRotation(SimpleTrackMapper.Direction direction) {
        switch (direction) {
            case POSITIVE_X: return 0;
            case NEGATIVE_Y: return 90;
            case NEGATIVE_X: return 180;
            case POSITIVE_Y: return 270;
            default: return 0;
        }
    }
}