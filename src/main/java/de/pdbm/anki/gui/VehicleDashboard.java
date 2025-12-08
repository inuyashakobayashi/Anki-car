package de.pdbm.anki.gui;

import de.pdbm.anki.api.AnkiController;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.notifications.*;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * 增强版车辆控制仪表盘
 * 功能：速度控制、车灯控制、灯光图案、脱轨警告
 */
public class VehicleDashboard extends VBox {

    private final AnkiController controller;

    // 仪表盘组件
    private Tile statusTile;
    private Tile speedGaugeTile;   // 显示速度 (表盘)
    private Tile speedControlTile; // 控制速度 (滑块)
    private Tile batteryTile;
    private Tile headLightTile;
    private Tile tailLightTile;    // 尾灯
    private Tile controlTile;      // 按钮区域
    private Tile lightPatternTile; // 灯光图案控制

    // 脱轨警告相关
    private boolean isDelocalized = false;
    private Timeline delocalizedFlashTimeline;
    private final Color normalStatusColor = Color.web("#3e3e3e");
    private final Color warningColor = Color.web("#e74c3c");

    // 额外状态
    private String firmwareVersion = "Unknown";
    private boolean isOnCharger = false;

    public VehicleDashboard(AnkiController controller) {
        this.controller = controller;
        this.setSpacing(10);
        this.setPadding(new Insets(10));
        // 给每个仪表盘设置不同的深色背景，区分度更好
        this.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #444; -fx-border-width: 1; -fx-border-radius: 5;");

        initTiles();
        setupListeners();

        // --- 布局组装 ---

        // 1. 状态与电池并排
        HBox topRow = new HBox(10, statusTile, batteryTile);

        // 2. 速度显示与控制并排
        HBox speedRow = new HBox(10, speedGaugeTile, speedControlTile);

        // 3. 车灯控制行 (前灯 + 后灯)
        HBox lightRow = new HBox(10, headLightTile, tailLightTile);

        // 4. 将所有行加入主容器
        this.getChildren().addAll(
                topRow,
                speedRow,
                lightRow,
                lightPatternTile,  // 灯光图案控制
                controlTile
        );

        // 初始化脱轨警告闪烁动画
        initDelocalizedFlashAnimation();

        // 启动时查询固件版本
        queryInitialInfo();
    }

    /**
     * 初始化脱轨警告闪烁动画
     */
    private void initDelocalizedFlashAnimation() {
        delocalizedFlashTimeline = new Timeline(
                new KeyFrame(Duration.millis(0), e -> {
                    if (isDelocalized) {
                        statusTile.setBackgroundColor(warningColor);
                    }
                }),
                new KeyFrame(Duration.millis(500), e -> {
                    if (isDelocalized) {
                        statusTile.setBackgroundColor(Color.web("#8B0000")); // 深红
                    }
                })
        );
        delocalizedFlashTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    /**
     * 启动时查询固件版本
     */
    private void queryInitialInfo() {
        Platform.runLater(() -> {
            if (controller.getVehicle() != null) {
                controller.getVehicle().queryVersion();
            }
        });
    }

    // 统一尺寸常量 - 方便调整 (增大尺寸以显示更大字体)
    private static final double TILE_WIDTH = 220;        // 单个 Tile 宽度
    private static final double TILE_HEIGHT_SMALL = 150; // 小 Tile 高度
    private static final double TILE_HEIGHT_LARGE = 210; // 大 Tile 高度
    private static final double TOTAL_WIDTH = TILE_WIDTH * 2 + 10; // 总宽度

    private void initTiles() {
        // --- 1. 状态 Tile ---
        statusTile = TileBuilder.create()
                .skinType(Tile.SkinType.TEXT)
                .title("STATUS")
                .text("Connected")
                .description(getVehicleMacSafe())
                .textAlignment(TextAlignment.CENTER)
                .prefSize(TILE_WIDTH, TILE_HEIGHT_SMALL)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        // --- 2. 电池 Tile ---
        batteryTile = TileBuilder.create()
                .skinType(Tile.SkinType.PERCENTAGE)
                .title("BATTERY")
                .unit("mV")
                .maxValue(4200)
                .prefSize(TILE_WIDTH, TILE_HEIGHT_SMALL)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        // --- 3. 速度显示 (Gauge) ---
        speedGaugeTile = TileBuilder.create()
                .skinType(Tile.SkinType.GAUGE)
                .title("SPEEDOMETER")
                .unit("mm/s")
                .maxValue(1000)
                .threshold(800)
                .prefSize(TILE_WIDTH, TILE_HEIGHT_LARGE)
                .backgroundColor(Color.web("#222"))
                .build();

        // --- 4. 速度控制 (Slider) ---
        speedControlTile = TileBuilder.create()
                .skinType(Tile.SkinType.SLIDER)
                .title("THROTTLE")
                .text("Set Speed")
                .unit("mm/s")
                .maxValue(1000)
                .value(0)
                .prefSize(TILE_WIDTH, TILE_HEIGHT_LARGE)
                .backgroundColor(Color.web("#222"))
                .barColor(Color.web("#3498db"))
                .build();

        // 监听滑块事件 -> 设置车速
        speedControlTile.valueProperty().addListener((obs, oldVal, newVal) -> {
            int targetSpeed = newVal.intValue();
            // 只有连接状态下才发送，避免报错
            if (controller.getVehicle() != null && controller.isConnected()) {
                controller.setSpeed(targetSpeed);
                // 同时更新左边的表盘显示，让反馈更直接
                speedGaugeTile.setValue(targetSpeed);
            }
        });

        // --- 5. 前灯开关 ---
        headLightTile = TileBuilder.create()
                .skinType(Tile.SkinType.SWITCH)
                .title("HEADLIGHTS")
                .prefSize(TILE_WIDTH, TILE_HEIGHT_SMALL)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        headLightTile.setOnSwitchPressed(e -> {
            if (controller.getVehicle() != null) {
                controller.getVehicle().setLight(Message.LIGHT_HEADLIGHTS, headLightTile.isActive());
                controller.getVehicle().setLight(Message.LIGHT_FRONTLIGHTS, headLightTile.isActive());
            }
        });

        // --- 6. 尾灯开关 ---
        tailLightTile = TileBuilder.create()
                .skinType(Tile.SkinType.SWITCH)
                .title("TAILLIGHTS")
                .prefSize(TILE_WIDTH, TILE_HEIGHT_SMALL)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        tailLightTile.setOnSwitchPressed(e -> {
            if (controller.getVehicle() != null) {
                // 控制刹车灯
                controller.getVehicle().setLight(Message.LIGHT_BRAKELIGHTS, tailLightTile.isActive());
            }
        });

        // --- 7. 灯光图案控制 ---
        VBox lightPatternBox = new VBox(10);
        lightPatternBox.setAlignment(Pos.CENTER);
        lightPatternBox.setPadding(new Insets(8));

        Label patternLabel = new Label("RGB Light Effects");
        patternLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        // 灯光效果按钮 - 使用 GridPane 布局
        GridPane patternBtnGrid = new GridPane();
        patternBtnGrid.setHgap(5);
        patternBtnGrid.setVgap(5);
        patternBtnGrid.setAlignment(Pos.CENTER);

        Button btnRedThrob = createSmallButton("Red", "#e74c3c");
        btnRedThrob.setOnAction(e -> {
            if (controller.getVehicle() != null) controller.getVehicle().lightPatternRedThrob(30);
        });

        Button btnBlueThrob = createSmallButton("Blue", "#3498db");
        btnBlueThrob.setOnAction(e -> {
            if (controller.getVehicle() != null) controller.getVehicle().lightPatternBlueThrob(30);
        });

        Button btnGreenThrob = createSmallButton("Green", "#2ecc71");
        btnGreenThrob.setOnAction(e -> {
            if (controller.getVehicle() != null) controller.getVehicle().lightPatternGreenThrob(30);
        });

        Button btnPolice = createSmallButton("Police", "#9b59b6");
        btnPolice.setOnAction(e -> {
            if (controller.getVehicle() != null) controller.getVehicle().lightPatternPolice();
        });

        Button btnRainbow = createSmallButton("Rainbow", "#f39c12");
        btnRainbow.setOnAction(e -> {
            if (controller.getVehicle() != null) controller.getVehicle().lightPatternRainbow();
        });

        Button btnLightOff = createSmallButton("OFF", "#7f8c8d");
        btnLightOff.setOnAction(e -> {
            if (controller.getVehicle() != null) controller.getVehicle().lightPatternOff();
        });

        // 布局: 3x2
        patternBtnGrid.add(btnRedThrob, 0, 0);
        patternBtnGrid.add(btnBlueThrob, 1, 0);
        patternBtnGrid.add(btnGreenThrob, 2, 0);
        patternBtnGrid.add(btnPolice, 0, 1);
        patternBtnGrid.add(btnRainbow, 1, 1);
        patternBtnGrid.add(btnLightOff, 2, 1);

        lightPatternBox.getChildren().addAll(patternLabel, patternBtnGrid);

        lightPatternTile = TileBuilder.create()
                .skinType(Tile.SkinType.CUSTOM)
                .title("LIGHT PATTERNS")
                .graphic(lightPatternBox)
                .prefSize(TOTAL_WIDTH, 150)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        // --- 8. 按钮控制区 ---
        GridPane btnGrid = new GridPane();
        btnGrid.setHgap(8);
        btnGrid.setVgap(8);
        btnGrid.setPadding(new Insets(10));
        btnGrid.setAlignment(Pos.CENTER);

        Button btnStop = createStyledButton("STOP", "#e74c3c");
        btnStop.setOnAction(e -> {
            // 急停：速度设为0，更新滑块和表盘
            controller.setSpeed(0);
            speedControlTile.setValue(0);
            speedGaugeTile.setValue(0);
        });

        Button btnUTurn = createStyledButton("U-Turn", "#e67e22");
        btnUTurn.setOnAction(e -> {
            if (controller.getVehicle() != null) controller.getVehicle().uTurn();
        });

        Button btnLeft = createStyledButton("<< Lane", "#95a5a6");
        btnLeft.setOnAction(e -> controller.changeLane(-68));

        Button btnRight = createStyledButton("Lane >>", "#95a5a6");
        btnRight.setOnAction(e -> controller.changeLane(68));

        Button btnBattery = createStyledButton("Ping Batt", "#2ecc71");
        btnBattery.setOnAction(e -> {
            if (controller.getVehicle() != null) controller.getVehicle().queryBatteryLevel();
        });

        // 布局按钮 (2列布局)
        btnGrid.add(btnLeft, 0, 0);
        btnGrid.add(btnRight, 1, 0);
        btnGrid.add(btnStop, 0, 1, 2, 1); // STOP 按钮占满一行
        btnGrid.add(btnUTurn, 0, 2);
        btnGrid.add(btnBattery, 1, 2);

        controlTile = TileBuilder.create()
                .skinType(Tile.SkinType.CUSTOM)
                .title("CONTROL PAD")
                .graphic(btnGrid)
                .prefSize(TOTAL_WIDTH, 270)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");
        btn.setPrefWidth(210);
        btn.setPrefHeight(45);
        return btn;
    }

    private Button createSmallButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        btn.setPrefWidth(140);
        btn.setPrefHeight(35);
        return btn;
    }

    private void setupListeners() {
        Vehicle v = controller.getVehicle();
        if (v == null) return;

        // 监听电池电量
        v.addNotificationListener((BatteryListener) update -> {
            Platform.runLater(() -> {
                batteryTile.setValue(update.getBatteryLevelMs());
                batteryTile.setDescription(String.format("%.1f V", update.getBatteryLevelMs() / 1000.0));
            });
        });

        // 监听脱轨事件
        v.addNotificationListener((DelocalizedListener) notification -> {
            Platform.runLater(() -> {
                setDelocalized(true);
            });
        });

        // 监听位置更新 - 收到位置说明车辆回到轨道
        v.addNotificationListener((PositionUpdateListener) update -> {
            Platform.runLater(() -> {
                if (isDelocalized) {
                    setDelocalized(false);
                }
            });
        });

        // 监听版本响应
        v.addNotificationListener((VersionResponseListener) response -> {
            Platform.runLater(() -> {
                firmwareVersion = response.getVersionString();
                updateStatusDisplay();
            });
        });

        // 监听充电器状态
        v.addNotificationListener((ChargerInfoNotificationListener) notification -> {
            Platform.runLater(() -> {
                isOnCharger = notification.isOnCharger();
                updateStatusDisplay();
            });
        });
    }

    /**
     * 设置脱轨状态
     */
    private void setDelocalized(boolean delocalized) {
        this.isDelocalized = delocalized;
        if (delocalized) {
            // 开始闪烁警告
            statusTile.setText("OFF TRACK!");
            statusTile.setTextColor(Color.WHITE);
            delocalizedFlashTimeline.play();
        } else {
            // 恢复正常
            delocalizedFlashTimeline.stop();
            statusTile.setBackgroundColor(normalStatusColor);
            statusTile.setText("Connected");
            updateStatusDisplay();
        }
    }

    /**
     * 更新状态显示 (版本、充电状态)
     */
    private void updateStatusDisplay() {
        StringBuilder desc = new StringBuilder();
        desc.append(getVehicleMacSafe());

        if (!"Unknown".equals(firmwareVersion)) {
            desc.append("\nFW: v").append(firmwareVersion);
        }

        if (isOnCharger) {
            desc.append("\n[CHARGING]");
            statusTile.setText("Charging");
        } else if (!isDelocalized) {
            statusTile.setText("Connected");
        }

        statusTile.setDescription(desc.toString());
    }

    // 安全获取 MAC 地址的辅助方法
    private String getVehicleMacSafe() {
        if (controller.getVehicle() != null) {
            String mac = controller.getVehicle().getMacAddress();
            // 只显示后半段 MAC，节省空间
            return "..." + mac.substring(mac.length() - 8);
        }
        return "Unknown";
    }
}