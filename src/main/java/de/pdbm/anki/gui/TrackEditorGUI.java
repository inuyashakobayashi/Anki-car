package de.pdbm.anki.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨道编辑器 - 可拖拽的轨道拼接工具
 *
 * 功能：
 * 1. 从左侧工具栏拖拽轨道片段到画布
 * 2. 旋转轨道片段（右键点击）
 * 3. 删除轨道片段（双击）
 * 4. 网格对齐
 */
public class TrackEditorGUI extends Application {

    private static final int TILE_SIZE = 150;
    private static final int GRID_SIZE = 50; // 网格间距
    private Pane canvas;
    private VBox toolbox;

    private List<DraggableTrackPiece> placedPieces = new ArrayList<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Anki 轨道编辑器");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // 创建工具栏（左侧）
        toolbox = createToolbox();
        ScrollPane toolboxScroll = new ScrollPane(toolbox);
        toolboxScroll.setFitToWidth(true);
        toolboxScroll.setStyle("-fx-background: #3c3c3c;");
        root.setLeft(toolboxScroll);

        // 创建画布（中央）
        canvas = createCanvas();
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setStyle("-fx-background: #2b2b2b;");
        root.setCenter(scrollPane);

        // 创建控制面板（顶部）
        HBox controls = createControls();
        root.setTop(controls);

        Scene scene = new Scene(root, 1400, 900);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * 创建工具栏 - 包含所有可拖拽的轨道片段
     */
    private VBox createToolbox() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #3c3c3c;");
        box.setPrefWidth(200);

        Label title = new Label("轨道片段");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");
        box.getChildren().add(title);

        // 添加所有轨道片段
        box.getChildren().add(createToolboxItem("start.png", "起点"));
        box.getChildren().add(createToolboxItem("straight0.png", "直道"));
        box.getChildren().add(createToolboxItem("curve0.png", "弯道 0 ⌟"));
        box.getChildren().add(createToolboxItem("curve1.png", "弯道 1 ⌞"));
        box.getChildren().add(createToolboxItem("curve2.png", "弯道 2 ⌜"));
        box.getChildren().add(createToolboxItem("curve3.png", "弯道 3 ⌝"));
        box.getChildren().add(createToolboxItem("intersection.png", "交叉路口"));

        return box;
    }

    /**
     * 创建工具栏中的单个项目
     */
    private VBox createToolboxItem(String imageName, String label) {
        VBox item = new VBox(5);
        item.setStyle("-fx-background-color: #4c4c4c; -fx-padding: 10; -fx-border-color: #5c5c5c; -fx-border-width: 1;");

        Image image = ActualTrackImageLoader.getTrackImageByName(imageName);
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(80);
            imageView.setFitHeight(80);
            imageView.setPreserveRatio(true);

            Label nameLabel = new Label(label);
            nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");

            item.getChildren().addAll(imageView, nameLabel);

            // 拖拽开始
            item.setOnDragDetected(event -> {
                Dragboard db = item.startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString(imageName);
                db.setContent(content);
                db.setDragView(image, 40, 40);
                event.consume();
            });

            item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: #5c5c5c; -fx-padding: 10; -fx-border-color: orange; -fx-border-width: 2;"));
            item.setOnMouseExited(e -> item.setStyle("-fx-background-color: #4c4c4c; -fx-padding: 10; -fx-border-color: #5c5c5c; -fx-border-width: 1;"));
        }

        return item;
    }

    /**
     * 创建画布
     */
    private Pane createCanvas() {
        Pane pane = new Pane();
        pane.setPrefSize(2400, 1800);
        pane.setStyle("-fx-background-color: #1e1e1e;");

        // 绘制网格
        drawGrid(pane);

        // 允许接收拖拽
        pane.setOnDragOver(event -> {
            if (event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        // 处理放置
        pane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasString()) {
                String imageName = db.getString();
                Image image = ActualTrackImageLoader.getTrackImageByName(imageName);

                if (image != null) {
                    // 网格对齐
                    double x = Math.round(event.getX() / GRID_SIZE) * GRID_SIZE;
                    double y = Math.round(event.getY() / GRID_SIZE) * GRID_SIZE;

                    DraggableTrackPiece piece = new DraggableTrackPiece(image, imageName, x, y);
                    pane.getChildren().add(piece.getImageView());
                    placedPieces.add(piece);

                    success = true;
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });

        return pane;
    }

    /**
     * 绘制网格背景
     */
    private void drawGrid(Pane pane) {
        int width = (int) pane.getPrefWidth();
        int height = (int) pane.getPrefHeight();

        for (int x = 0; x < width; x += GRID_SIZE) {
            Region line = new Region();
            line.setLayoutX(x);
            line.setLayoutY(0);
            line.setPrefSize(1, height);
            line.setStyle("-fx-background-color: #2b2b2b;");
            pane.getChildren().add(line);
        }

        for (int y = 0; y < height; y += GRID_SIZE) {
            Region line = new Region();
            line.setLayoutX(0);
            line.setLayoutY(y);
            line.setPrefSize(width, 1);
            line.setStyle("-fx-background-color: #2b2b2b;");
            pane.getChildren().add(line);
        }
    }

    /**
     * 创建控制面板
     */
    private HBox createControls() {
        HBox controls = new HBox(15);
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-background-color: #3c3c3c;");

        Label instructions = new Label("📌 拖拽轨道到画布 | 🔄 右键旋转 | ❌ 双击删除 | ⬆️⬇️⬅️➡️ 方向键微调");
        instructions.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        Button clearBtn = new Button("清空画布");
        clearBtn.setOnAction(e -> {
            canvas.getChildren().clear();
            drawGrid(canvas);
            placedPieces.clear();
        });

        Button exportBtn = new Button("导出坐标");
        exportBtn.setOnAction(e -> exportLayout());

        controls.getChildren().addAll(instructions, clearBtn, exportBtn);
        return controls;
    }

    /**
     * 导出当前布局的坐标信息
     */
    private void exportLayout() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 轨道布局 ===\n");
        sb.append("总片段数: ").append(placedPieces.size()).append("\n\n");

        for (int i = 0; i < placedPieces.size(); i++) {
            DraggableTrackPiece piece = placedPieces.get(i);
            sb.append(String.format("#%d: %s @ (%.0f, %.0f) 旋转: %.0f°\n",
                    i + 1,
                    piece.getImageName(),
                    piece.getX(),
                    piece.getY(),
                    piece.getRotation()));
        }

        System.out.println(sb.toString());

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("布局已导出");
        alert.setHeaderText("轨道坐标已输出到控制台");
        alert.setContentText("总共 " + placedPieces.size() + " 个轨道片段");
        alert.showAndWait();
    }

    /**
     * 可拖拽的轨道片段
     */
    private class DraggableTrackPiece {
        private ImageView imageView;
        private String imageName;
        private double offsetX, offsetY;
        private double rotation = 0;

        public DraggableTrackPiece(Image image, String imageName, double x, double y) {
            this.imageName = imageName;
            this.imageView = new ImageView(image);
            this.imageView.setFitWidth(TILE_SIZE);
            this.imageView.setFitHeight(TILE_SIZE);
            this.imageView.setPreserveRatio(false);
            this.imageView.setSmooth(true);
            this.imageView.setLayoutX(x);
            this.imageView.setLayoutY(y);

            // 添加高亮效果
            imageView.setOnMouseEntered(e -> {
                imageView.setStyle("-fx-effect: dropshadow(gaussian, orange, 15, 0.7, 0, 0);");
            });
            imageView.setOnMouseExited(e -> {
                imageView.setStyle("");
            });

            // 拖拽移动
            imageView.setOnMousePressed(event -> {
                if (event.isPrimaryButtonDown()) {
                    offsetX = event.getSceneX() - imageView.getLayoutX();
                    offsetY = event.getSceneY() - imageView.getLayoutY();
                    imageView.toFront();
                }
            });

            imageView.setOnMouseDragged(event -> {
                if (event.isPrimaryButtonDown()) {
                    double newX = event.getSceneX() - offsetX;
                    double newY = event.getSceneY() - offsetY;

                    // 网格对齐
                    newX = Math.round(newX / GRID_SIZE) * GRID_SIZE;
                    newY = Math.round(newY / GRID_SIZE) * GRID_SIZE;

                    imageView.setLayoutX(newX);
                    imageView.setLayoutY(newY);
                }
            });

            // 右键旋转
            imageView.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    rotation = (rotation + 90) % 360;
                    imageView.setRotate(rotation);
                    event.consume();
                }

                // 双击删除
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    canvas.getChildren().remove(imageView);
                    placedPieces.remove(this);
                    event.consume();
                }
            });

            // 方向键微调
            imageView.setOnKeyPressed(event -> {
                double step = event.isShiftDown() ? GRID_SIZE : 5;
                switch (event.getCode()) {
                    case UP:
                        imageView.setLayoutY(imageView.getLayoutY() - step);
                        break;
                    case DOWN:
                        imageView.setLayoutY(imageView.getLayoutY() + step);
                        break;
                    case LEFT:
                        imageView.setLayoutX(imageView.getLayoutX() - step);
                        break;
                    case RIGHT:
                        imageView.setLayoutX(imageView.getLayoutX() + step);
                        break;
                    case R:
                        rotation = (rotation + 90) % 360;
                        imageView.setRotate(rotation);
                        break;
                }
                event.consume();
            });

            imageView.setFocusTraversable(true);
        }

        public ImageView getImageView() {
            return imageView;
        }

        public String getImageName() {
            return imageName;
        }

        public double getX() {
            return imageView.getLayoutX();
        }

        public double getY() {
            return imageView.getLayoutY();
        }

        public double getRotation() {
            return rotation;
        }
    }
}
