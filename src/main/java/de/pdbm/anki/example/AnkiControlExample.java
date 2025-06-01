package de.pdbm.anki.example;

import de.pdbm.anki.api.*;
import de.pdbm.janki.RoadPiece;
import java.util.List;
import java.util.Scanner;

public class AnkiControlExample {

    public static void main(String[] args) {
        // 创建控制器
        AnkiController controller = AnkiControllerFactory.create();
        Scanner scanner = new Scanner(System.in);

        try {
            // 1. 扫描设备
            System.out.println("扫描Anki设备...");
            List<String> devices = controller.scanDevices();

            if (devices.isEmpty()) {
                System.out.println("未找到Anki设备");
                return;
            }

            // 2. 显示设备并选择
            System.out.println("找到的设备:");
            for (int i = 0; i < devices.size(); i++) {
                System.out.println((i + 1) + ": " + devices.get(i));
            }

            System.out.print("选择设备 (1-" + devices.size() + "): ");
            int choice = scanner.nextInt() - 1;

            // 3. 连接设备
            System.out.println("连接中...");
            boolean connected = controller.connect(devices.get(choice));

            if (!connected) {
                System.out.println("连接失败");
                return;
            }

            System.out.println("连接成功!");

            // 4. 简单控制演示
            demonstrateBasicControl(controller, scanner);

            // 5. 轨道映射演示
            demonstrateTrackMapping(controller, scanner);

        } finally {
            controller.disconnect();
            scanner.close();
        }
    }

    private static void demonstrateBasicControl(AnkiController controller, Scanner scanner) {
        System.out.println("\n=== 基本控制演示 ===");

        // 设置速度
        System.out.println("设置速度为300...");
        controller.setSpeed(300);

        // 等待3秒
        try { Thread.sleep(3000); } catch (InterruptedException e) {}

        // 换道
        System.out.println("向左换道...");
        controller.changeLane(-0.3f);

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // 回到中间
        System.out.println("回到中间...");
        controller.changeLane(0.0f);

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // 停车
        System.out.println("停车...");
        controller.stop();
    }

    private static void demonstrateTrackMapping(AnkiController controller, Scanner scanner) {
        System.out.println("\n=== 轨道映射演示 ===");
        System.out.println("按回车键开始轨道映射...");
        scanner.nextLine();
        scanner.nextLine();

        // 开始轨道映射
        controller.startTrackMapping(300, new TrackMappingListener() {
            @Override
            public void onTrackPieceDiscovered(int locationId, RoadPiece roadPiece) {
                System.out.println("发现轨道: ID=" + locationId + ", 类型=" + roadPiece);
            }

            @Override
            public void onLocationUpdate(int locationId, boolean ascending) {
                System.out.println("位置更新: ID=" + locationId + ", 方向=" +
                        (ascending ? "正向" : "反向"));
            }
        });

        System.out.println("轨道映射中... 按回车键停止");
        scanner.nextLine();

        // 停止映射
        controller.stopTrackMapping();

        // 显示结果
        System.out.println("\n收集到的轨道地图:");
        controller.getTrackMap().forEach((id, piece) ->
                System.out.println("  位置ID: " + id + " -> " + piece));
    }
}