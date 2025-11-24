package de.pdbm.anki.gui;

import de.pdbm.anki.api.AnkiController;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.notifications.BatteryListener;
import de.pdbm.janki.notifications.BatteryNotification;
import de.pdbm.janki.notifications.Message;
import eu.hansolo.tilesfx.Tile;
import eu.hansolo.tilesfx.TileBuilder;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

/**
 * 增强版车辆控制仪表盘
 * 新增：速度滑块控制、尾灯控制
 */
public class VehicleDashboard extends VBox {

    private final AnkiController controller;

    // 仪表盘组件
    private Tile statusTile;
    private Tile speedGaugeTile;   // 显示速度 (表盘)
    private Tile speedControlTile; // 控制速度 (滑块)
    private Tile batteryTile;
    private Tile headLightTile;
    private Tile tailLightTile;    // 新增：尾灯
    private Tile controlTile;      // 按钮区域

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
        // 左边是表盘(看)，右边是滑块(控)
        HBox speedRow = new HBox(10, speedGaugeTile, speedControlTile);

        // 3. 车灯控制行 (前灯 + 后灯)
        HBox lightRow = new HBox(10, headLightTile, tailLightTile);

        // 4. 将所有行加入主容器
        this.getChildren().addAll(
                topRow,
                speedRow,
                lightRow,
                controlTile
        );
    }

    private void initTiles() {
        // --- 1. 状态 Tile ---
        statusTile = TileBuilder.create()
                .skinType(Tile.SkinType.TEXT)
                .title("Status")
                .text("Connected")
                .description(getVehicleMacSafe())
                .textAlignment(TextAlignment.CENTER)
                .prefSize(180, 120) // 稍微调小一点以适应并排
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        // --- 2. 电池 Tile ---
        batteryTile = TileBuilder.create()
                .skinType(Tile.SkinType.PERCENTAGE)
                .title("Battery")
                .unit("mV")
                .maxValue(4200) // 满电电压约 4.2V
                .prefSize(180, 120)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        // --- 3. 速度显示 (Gauge) ---
        speedGaugeTile = TileBuilder.create()
                .skinType(Tile.SkinType.GAUGE)
                .title("Speedometer")
                .unit("mm/s")
                .maxValue(1000)
                .threshold(800)
                .prefSize(180, 180)
                .backgroundColor(Color.web("#222"))
                .build();

        // --- 4. 速度控制 (Slider) [新增!] ---
        speedControlTile = TileBuilder.create()
                .skinType(Tile.SkinType.SLIDER)
                .title("Throttle")
                .text("Set Speed")
                .unit("mm/s")
                .maxValue(1000)
                .value(0)
                .prefSize(180, 180)
                .backgroundColor(Color.web("#222"))
                .barColor(Color.web("#3498db")) // 蓝色进度条
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
                .title("Headlights")
                .prefSize(180, 120)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        headLightTile.setOnSwitchPressed(e -> {
            if (controller.getVehicle() != null) {
                // 这是一个简单的实现：开关前大灯
                controller.getVehicle().setLight(Message.LIGHT_HEADLIGHTS, headLightTile.isActive());
                // 顺便把前辅助灯也开了，更亮
                controller.getVehicle().setLight(Message.LIGHT_FRONTLIGHTS, headLightTile.isActive());
            }
        });

        // --- 6. 尾灯开关 [新增!] ---
        tailLightTile = TileBuilder.create()
                .skinType(Tile.SkinType.SWITCH)
                .title("Taillights")
                .prefSize(180, 120)
                .backgroundColor(Color.web("#3e3e3e"))
                .build();

        tailLightTile.setOnSwitchPressed(e -> {
            if (controller.getVehicle() != null) {
                // 控制刹车灯
                controller.getVehicle().setLight(Message.LIGHT_BRAKELIGHTS, tailLightTile.isActive());
            }
        });

        // --- 7. 按钮控制区 ---
        GridPane btnGrid = new GridPane();
        btnGrid.setHgap(5);
        btnGrid.setVgap(5);
        btnGrid.setPadding(new Insets(5));
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
                .title("Control Pad")
                .graphic(btnGrid)
                .prefSize(370, 250) // 宽度占满
                .backgroundColor(Color.web("#3e3e3e"))
                .build();
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
        btn.setPrefWidth(165); // 按钮宽度适配
        btn.setPrefHeight(40);
        return btn;
    }

    private void setupListeners() {
        Vehicle v = controller.getVehicle();
        if (v == null) return;

        // 监听电池电量 (需要之前的 BatteryNotification 支持)
        v.addNotificationListener((BatteryListener) update -> {
            Platform.runLater(() -> {
                // 更新电池 Tile，直接显示 mV
                batteryTile.setValue(update.getBatteryLevelMs());
                batteryTile.setDescription(String.format("%.1f V", update.getBatteryLevelMs() / 1000.0));
            });
        });
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