package de.pdbm.anki.gui;

import de.pdbm.janki.RoadPiece;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.HashMap;
import java.util.Map;

/**
 * 实际轨道图片加载器
 * 基于你项目中的真实图片文件名
 */
public class ActualTrackImageLoader {

    // 存储所有轨道图片的映射
    private static final Map<String, Image> trackImages = new HashMap<>();
    private static final Map<RoadPiece, String> roadPieceMapping = new HashMap<>();

    // 你的实际图片文件列表
    private static final String[] IMAGE_FILES = {
            "curve0.png",
            "curve1.png",
            "curve2.png",
            "curve3.png",
            "start.png",
            "intersection.png",
            "straight0.png",
            "straight1.png",
            "straight2.png",
            "straight3.png",
            "car1.png",
            "car2.png"
    };

    // 静态初始化块，在类加载时加载所有图片
    static {
        loadAllTrackImages();
        setupRoadPieceMappings();
    }

    /**
     * 加载所有轨道图片到内存中
     */
    private static void loadAllTrackImages() {
        System.out.println("🖼️ Loading track images from your project...");

        for (String fileName : IMAGE_FILES) {
            try {
                String imagePath = "/images/" + fileName;
                Image image = loadImageFromResources(imagePath);

                if (image != null && !image.isError()) {
                    trackImages.put(fileName, image);
                    System.out.println("✅ Loaded: " + fileName + " (" +
                            (int)image.getWidth() + "x" + (int)image.getHeight() + ")");
                } else {
                    System.out.println("❌ Failed to load: " + fileName);
                }
            } catch (Exception e) {
                System.err.println("❌ Error loading " + fileName + ": " + e.getMessage());
            }
        }

        System.out.println("🎉 Track images loading completed! Loaded " + trackImages.size() + "/" + IMAGE_FILES.length + " images.");
    }

    /**
     * 从resources目录加载图片
     */
    private static Image loadImageFromResources(String imagePath) {
        try {
            var inputStream = ActualTrackImageLoader.class.getResourceAsStream(imagePath);
            if (inputStream != null) {
                return new Image(inputStream);
            } else {
                System.out.println("⚠️ Image not found: " + imagePath);
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load image from " + imagePath + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 设置RoadPiece到图片文件的映射关系
     */
    private static void setupRoadPieceMappings() {
        System.out.println("🔗 Setting up RoadPiece mappings...");

        // 根据你的RoadPiece枚举和图片文件建立映射
        roadPieceMapping.put(RoadPiece.START, "start.png");
        roadPieceMapping.put(RoadPiece.FINISH, "start.png"); // 复用start图片，或者你可以创建finish.png
        roadPieceMapping.put(RoadPiece.STRAIGHT, "straight0.png"); // 默认使用straight0
        roadPieceMapping.put(RoadPiece.CORNER, "curve0.png"); // 默认使用curve0
        roadPieceMapping.put(RoadPiece.INTERSECTION, "intersection.png");

        // 打印映射关系
        for (Map.Entry<RoadPiece, String> entry : roadPieceMapping.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
    }

    /**
     * 根据RoadPiece类型获取对应的轨道图片
     */
    public static Image getTrackImage(RoadPiece roadPiece) {
        if (roadPiece == null) {
            return getDefaultImage();
        }

        String fileName = roadPieceMapping.get(roadPiece);
        if (fileName != null && trackImages.containsKey(fileName)) {
            return trackImages.get(fileName);
        }

        // 如果没有找到映射，返回默认图片
        return getDefaultImage();
    }

    /**
     * 根据文件名直接获取图片
     */
    public static Image getTrackImageByName(String fileName) {
        return trackImages.get(fileName);
    }

    /**
     * 获取默认图片（第一个可用图片）
     */
    private static Image getDefaultImage() {
        if (!trackImages.isEmpty()) {
            return trackImages.values().iterator().next();
        }
        return null;
    }

    /**
     * 创建轨道图片的ImageView
     */
    public static ImageView createTrackImageView(RoadPiece roadPiece) {
        Image image = getTrackImage(roadPiece);
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            return imageView;
        }
        return null;
    }

    /**
     * 创建指定大小的轨道图片ImageView
     */
    public static ImageView createTrackImageView(RoadPiece roadPiece, double width, double height) {
        ImageView imageView = createTrackImageView(roadPiece);
        if (imageView != null) {
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
        }
        return imageView;
    }

    /**
     * 根据文件名创建ImageView
     */
    public static ImageView createImageViewByName(String fileName, double width, double height) {
        Image image = getTrackImageByName(fileName);
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            return imageView;
        }
        return null;
    }

    /**
     * 获取所有可用的图片文件名
     */
    public static String[] getAvailableImageNames() {
        return trackImages.keySet().toArray(new String[0]);
    }

    /**
     * 获取特定类型的图片列表
     */
    public static String[] getCurveImages() {
        return new String[]{"curve0.png", "curve1.png", "curve2.png", "curve3.png"};
    }

    public static String[] getStraightImages() {
        return new String[]{"straight0.png", "straight1.png", "straight2.png", "straight3.png"};
    }

    /**
     * 检查图片是否成功加载
     */
    public static boolean isImageLoaded(String fileName) {
        Image image = trackImages.get(fileName);
        return image != null && !image.isError();
    }

    /**
     * 获取加载的图片统计信息
     */
    public static String getLoadingStats() {
        int totalFiles = IMAGE_FILES.length;
        int loadedCount = trackImages.size();
        return String.format("Loaded %d/%d track images", loadedCount, totalFiles);
    }

    /**
     * 检查是否有可用图片
     */
    public static boolean hasImages() {
        return !trackImages.isEmpty();
    }

    /**
     * 手动设置RoadPiece映射（用于自定义）
     */
    public static void setMapping(RoadPiece roadPiece, String fileName) {
        if (trackImages.containsKey(fileName)) {
            roadPieceMapping.put(roadPiece, fileName);
            System.out.println("✅ Updated mapping: " + roadPiece + " -> " + fileName);
        } else {
            System.out.println("❌ Cannot map " + roadPiece + " to " + fileName + " (image not loaded)");
        }
    }

    /**
     * 获取智能推荐的图片（基于位置和方向）
     */
    public static Image getSmartTrackImage(RoadPiece roadPiece, int locationId, boolean ascending) {
        switch (roadPiece) {
            case STRAIGHT:
                // 根据位置选择不同的直道图片
                String[] straights = getStraightImages();
                int straightIndex = locationId % straights.length;
                return getTrackImageByName(straights[straightIndex]);

            case CORNER:
                // 根据位置和方向选择弯道图片
                String[] curves = getCurveImages();
                int curveIndex = (locationId + (ascending ? 0 : 2)) % curves.length;
                return getTrackImageByName(curves[curveIndex]);

            case START:
                return getTrackImageByName("start.png");

            default:
                return getTrackImage(roadPiece);
        }
    }

    /**
     * 打印所有图片信息（调试用）
     */
    public static void printImageInfo() {
        System.out.println("\n📊 Loaded Track Images Information:");
        for (Map.Entry<String, Image> entry : trackImages.entrySet()) {
            Image img = entry.getValue();
            System.out.printf("  %s: %.0fx%.0f pixels\n",
                    entry.getKey(), img.getWidth(), img.getHeight());
        }

        System.out.println("\n🔗 RoadPiece Mappings:");
        for (Map.Entry<RoadPiece, String> entry : roadPieceMapping.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
    }
}