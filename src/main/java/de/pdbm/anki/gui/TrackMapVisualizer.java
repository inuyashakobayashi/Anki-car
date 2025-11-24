package de.pdbm.anki.gui;

import de.pdbm.anki.tracking.SimpleTrackMapper;
import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;
import de.pdbm.janki.RoadPiece;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轨道地图可视化器 - 使用真实的 PNG 图片显示轨道
 *
 * 基于 SimpleTrackMapper 收集的轨道数据，使用实际的轨道图片拼接显示完整地图
 */
public class TrackMapVisualizer {

    private static final int TILE_SIZE = 150; // 每个图片的显示大小
    private static final int PADDING = 20;
    private static final int CAR_SIZE = 60; // 小车图标大小

    private Stage stage;
    private Pane trackPane;
    private Map<String, ImageView> pieceViews;
    private Label statusLabel;

    // 小车显示相关
// 添加新的
    private Map<String, ImageView> vehicleViews = new HashMap<>(); // Key: 车辆MAC地址, Value: 图标
    private String[] carImages = {"car1.png", "car2.png"}; // 可用的车辆图片

    /**
     * 创建并显示可视化窗口
     */
    public void show(String title) {
        Platform.runLater(() -> {
            stage = new Stage();
            stage.setTitle(title);

            // 创建主布局
            BorderPane root = new BorderPane();
            root.setPadding(new Insets(PADDING));
            root.setBackground(new Background(new BackgroundFill(Color.DARKGRAY, null, null)));

            // 创建轨道显示区域
            trackPane = new Pane();
            trackPane.setBackground(new Background(new BackgroundFill(Color.LIGHTGRAY, null, null)));

            // 状态标签
            statusLabel = new Label("等待轨道数据...");
            statusLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: white;");
            statusLabel.setPadding(new Insets(10));

            root.setTop(statusLabel);
            root.setCenter(trackPane);

            Scene scene = new Scene(root, 1000, 800);
            stage.setScene(scene);
            stage.show();

            pieceViews = new HashMap<>();

            // TODO: 小车图标功能暂时禁用，先完成轨道映射
            // initializeVehicle();
        });
    }

    /**
     * 更新显示的轨道地图
     */
    public void updateTrackMap(List<TrackPiece> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            updateStatus("没有轨道数据");
            return;
        }

        Platform.runLater(() -> {
            trackPane.getChildren().clear();
            pieceViews.clear();

            // 找到坐标范围
            int minX = pieces.stream().mapToInt(p -> p.x).min().orElse(0);
            int maxX = pieces.stream().mapToInt(p -> p.x).max().orElse(0);
            int minY = pieces.stream().mapToInt(p -> p.y).min().orElse(0);
            int maxY = pieces.stream().mapToInt(p -> p.y).max().orElse(0);

            System.out.println("📊 Track bounds: X[" + minX + ", " + maxX + "], Y[" + minY + ", " + maxY + "]");

            // 渲染每个轨道片段
            for (TrackPiece piece : pieces) {
                renderTrackPiece(piece, minX, minY, maxY);
            }

            updateStatus(String.format("已显示 %d 个轨道片段", pieces.size()));

            // 调整窗口大小以适应轨道
            int width = (maxX - minX + 1) * TILE_SIZE + PADDING * 2;
            int height = (maxY - minY + 1) * TILE_SIZE + PADDING * 2 + 50; // +50 for status bar
            stage.setWidth(Math.max(800, width));
            stage.setHeight(Math.max(600, height));
        });
    }

    /**
     * 渲染单个轨道片段
     */
    private void renderTrackPiece(TrackPiece piece, int minX, int minY, int maxY) {
        // 获取对应的图片
        Image image = getImageForPiece(piece);
        if (image == null) {
            System.err.println("❌ No image for piece: " + piece);
            return;
        }

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(TILE_SIZE);
        imageView.setFitHeight(TILE_SIZE);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        // 计算旋转角度
        double rotation = getRotationForPiece(piece);
        imageView.setRotate(rotation);

        // 计算屏幕位置（标准化坐标）
        // Y 轴需要翻转（屏幕坐标 Y 向下增加，但网格坐标 Y 向上增加）
        int normalizedX = piece.x - minX;
        int normalizedY = maxY - piece.y;

        double screenX = normalizedX * TILE_SIZE;
        double screenY = normalizedY * TILE_SIZE;

        imageView.setLayoutX(screenX);
        imageView.setLayoutY(screenY);

        // 添加调试信息
        System.out.printf("  Rendering: %s (ID:%d) at grid(%d,%d) -> screen(%.0f,%.0f), rotation=%.0f°\n",
                piece.roadPiece, piece.roadPieceId, piece.x, piece.y, screenX, screenY, rotation);

        trackPane.getChildren().add(imageView);
        pieceViews.put(piece.x + "," + piece.y, imageView);
    }

    /**
     * 根据轨道片段选择合适的图片
     */
    private Image getImageForPiece(TrackPiece piece) {
        switch (piece.roadPiece) {
            case START:
            case FINISH:
                return ActualTrackImageLoader.getTrackImageByName("start.png");

            case STRAIGHT:
                // 根据方向选择直道图片
                return ActualTrackImageLoader.getTrackImageByName("straight0.png");

            case CORNER:
                // 根据 ascending 和 ASCII 字符选择弯道图片
                return getCornerImage(piece);

            case INTERSECTION:
                return ActualTrackImageLoader.getTrackImageByName("intersection.png");

            default:
                return ActualTrackImageLoader.getTrackImageByName("straight0.png");
        }
    }

    /**
     * 选择合适的弯道图片
     *
     * curve0.png: 橙色在左+下 (左下角)
     * curve1.png: 橙色在左+上 (左上角)
     * curve2.png: 橙色在右+上 (右上角)
     * curve3.png: 橙色在右+下 (右下角)
     *
     * 橙色边 = 轨道外侧边缘
     * 根据进入方向和离开方向选择正确的图片
     */
    private Image getCornerImage(TrackPiece piece) {
        if (piece.enterDirection == null || piece.exitDirection == null) {
            System.out.printf("  ⚠️ Missing direction info for piece at (%d,%d), using curve0.png\n",
                    piece.x, piece.y);
            return ActualTrackImageLoader.getTrackImageByName("curve0.png");
        }

        String selectedImage = selectCurveByEnterAndExit(piece.enterDirection, piece.exitDirection);

        System.out.printf("  🎨 Corner at (%d,%d): enter=%s, exit=%s -> %s\n",
                piece.x, piece.y, piece.enterDirection, piece.exitDirection, selectedImage);

        return ActualTrackImageLoader.getTrackImageByName(selectedImage);
    }

    /**
     * 根据进入和离开方向选择curve图片
     *
     * 用户规则：
     * - curve0: left px py 和 right nx ny
     * - curve1: left px ny 和 right nx py
     * - curve2: left nx ny 和 right px py
     * - curve3: left nx py 和 right px ny
     *
     * 说明：
     * - left/right 只跟 isAscending 有关
     * - px = POSITIVE_X, nx = NEGATIVE_X, py = POSITIVE_Y, ny = NEGATIVE_Y
     * - "px py" 表示 enter=POSITIVE_X, exit=POSITIVE_Y
     */
    private String selectCurveByEnterAndExit(SimpleTrackMapper.Direction enter, SimpleTrackMapper.Direction exit) {
        // 判断是左转还是右转（基于 isAscending）
        boolean isLeftTurn = (exit == enter.decrement());

        // LEFT 左转的情况 (isAscending = true)
        if (isLeftTurn) {
            // left px py → curve0
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) {
                return "curve0.png";
            }
            // left px ny → curve1
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) {
                return "curve1.png";
            }
            // left nx ny → curve2
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) {
                return "curve2.png";
            }
            // left nx py → curve3
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) {
                return "curve3.png";
            }
            // left py nx → curve3
            if (enter == SimpleTrackMapper.Direction.POSITIVE_Y && exit == SimpleTrackMapper.Direction.NEGATIVE_X) {
                return "curve3.png";
            }
            // left ny px → curve1
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_Y && exit == SimpleTrackMapper.Direction.POSITIVE_X) {
                return "curve1.png";
            }
        }
        // RIGHT 右转的情况 (isAscending = false)
        else {
            // right nx ny → curve0
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) {
                return "curve0.png";
            }
            // right nx py → curve1
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) {
                return "curve1.png";
            }
            // right px py → curve2
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.POSITIVE_Y) {
                return "curve2.png";
            }
            // right px ny → curve3
            if (enter == SimpleTrackMapper.Direction.POSITIVE_X && exit == SimpleTrackMapper.Direction.NEGATIVE_Y) {
                return "curve3.png";
            }
            // right ny nx → curve0
            if (enter == SimpleTrackMapper.Direction.NEGATIVE_Y && exit == SimpleTrackMapper.Direction.NEGATIVE_X) {
                return "curve0.png";
            }
            // right py px → curve2
            if (enter == SimpleTrackMapper.Direction.POSITIVE_Y && exit == SimpleTrackMapper.Direction.POSITIVE_X) {
                return "curve2.png";
            }
        }

        return "curve0.png";  // fallback
    }


    /**
     * 计算轨道片段的旋转角度
     *
     * 基于进入方向来计算正确的旋转角度
     *
     * curve0.png 假设是标准弯道：从右侧进入，向上离开（右转90度）
     * POSITIVE_X = 向右 (0°)
     * NEGATIVE_Y = 向下 (90°)
     * NEGATIVE_X = 向左 (180°)
     * POSITIVE_Y = 向上 (270°)
     */
    private double getRotationForPiece(TrackPiece piece) {
        if (piece.enterDirection == null) {
            return 0; // 如果没有方向信息，不旋转
        }

        switch (piece.roadPiece) {
            case START:
            case FINISH:
            case STRAIGHT:
                // 直道：根据进入方向旋转
                return getDirectionRotation(piece.enterDirection);

            case CORNER:
                // 弯道：根据进入方向和转向类型计算旋转
                return getCornerRotation(piece);

            case INTERSECTION:
                return 0; // 交叉路口不需要旋转

            default:
                return 0;
        }
    }

    /**
     * 根据方向获取基础旋转角度
     */
    private double getDirectionRotation(SimpleTrackMapper.Direction direction) {
        switch (direction) {
            case POSITIVE_X: return 0;    // 向右
            case NEGATIVE_Y: return 90;   // 向下
            case NEGATIVE_X: return 180;  // 向左
            case POSITIVE_Y: return 270;  // 向上
            default: return 0;
        }
    }

    /**
     * 计算弯道的旋转角度
     *
     * 由于我们在 getCornerImage() 中已经选择了正确朝向的图片
     * (curve0/1/2/3.png 代表4个不同方向的弯道)
     * 所以弯道不需要旋转
     */
    private double getCornerRotation(TrackPiece piece) {
        // 图片已经是正确朝向，不需要旋转
        return 0;
    }

    /**
     * 更新状态标签
     */
    public void updateStatus(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
        });
    }

    /**
     * 关闭窗口
     */
    public void close() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
            }
        });
    }

    /**
     * 检查窗口是否打开
     */
    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    /**
     * 清空显示
     */
    public void clear() {
        Platform.runLater(() -> {
            if (trackPane != null) {
                trackPane.getChildren().clear();
            }
            if (pieceViews != null) {
                pieceViews.clear();
            }
            updateStatus("等待轨道数据...");
        });
    }

    /**
     * 高亮显示特定片段
     */
    public void highlightPiece(int x, int y) {
        Platform.runLater(() -> {
            String key = x + "," + y;
            ImageView view = pieceViews.get(key);
            if (view != null) {
                view.setStyle("-fx-effect: dropshadow(gaussian, yellow, 20, 0.7, 0, 0);");
            }
        });
    }

    /**
     * 移除高亮
     */
    public void clearHighlight() {
        Platform.runLater(() -> {
            for (ImageView view : pieceViews.values()) {
                view.setStyle("");
            }
        });
    }

    /**
     * 启用小车显示（在实时追踪开始时调用）
     */
    public void enableVehicleDisplay() {
        Platform.runLater(() -> {
            // 清除旧的车辆显示（如果需要重置）
            for (ImageView view : vehicleViews.values()) {
                trackPane.getChildren().remove(view);
            }
            vehicleViews.clear();
        });
    }

    /**
     * 初始化小车图标
     */
    /**
     * 获取或创建指定车辆的图标
     */
    private ImageView getOrCreateVehicleView(String vehicleId) {
        if (vehicleViews.containsKey(vehicleId)) {
            return vehicleViews.get(vehicleId);
        }

        // 创建新图标
        // 简单的轮询分配图片：第1辆用car1，第2辆用car2...
        int index = vehicleViews.size() % carImages.length;
        String imageName = carImages[index];
        Image image = ActualTrackImageLoader.getTrackImageByName(imageName);

        if (image == null) {
            System.err.println("❌ 无法加载车辆图片: " + imageName);
            return null;
        }

        ImageView view = new ImageView(image);
        view.setFitWidth(CAR_SIZE);
        view.setFitHeight(CAR_SIZE);
        view.setPreserveRatio(true);
        view.setSmooth(true);

        // 初始位置在屏幕外
        view.setLayoutX(-100);
        view.setLayoutY(-100);

        // 添加到界面
        trackPane.getChildren().add(view);
        vehicleViews.put(vehicleId, view);

        System.out.println("🆕 新车辆加入显示: " + vehicleId + " (使用 " + imageName + ")");
        return view;
    }

    /**
     * 更新小车位置
     *
     * @param screenX 屏幕X坐标
     * @param screenY 屏幕Y坐标
     */
    /**
     * 更新指定车辆的位置
     * @param vehicleId 车辆唯一标识 (MAC地址)
     */
    public void updateVehiclePosition(String vehicleId, double screenX, double screenY) {
        Platform.runLater(() -> {
            ImageView view = getOrCreateVehicleView(vehicleId);
            if (view == null) return;

            // 居中显示
            double centerX = screenX - CAR_SIZE / 2.0;
            double centerY = screenY - CAR_SIZE / 2.0;

            view.setLayoutX(centerX);
            view.setLayoutY(centerY);
        });
    }

    /**
     * 更新指定车辆的方向
     * @param vehicleId 车辆唯一标识 (MAC地址)
     */
    public void updateVehicleDirection(String vehicleId, SimpleTrackMapper.Direction direction) {
        Platform.runLater(() -> {
            ImageView view = getOrCreateVehicleView(vehicleId);
            if (view == null || direction == null) return;

            double rotation = getDirectionRotation(direction);
            view.setRotate(rotation);
        });
    }


}
