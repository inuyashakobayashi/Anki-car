package de.pdbm.anki.tracking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 轨道地图的文件 I/O 操作
 * 使用 JSON 格式保存和加载地图数据
 *
 * @author Zijian Ying
 */
public class TrackMapIO {
    private static final String MAPS_DIR = "maps";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 保存地图到文件（使用时间戳命名）
     */
    public static String saveMap(TrackMapData mapData) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String filename = "track_" + sdf.format(new Date()) + ".json";
        return saveMap(mapData, filename);
    }

    /**
     * 保存地图到指定文件
     */
    public static String saveMap(TrackMapData mapData, String filename) throws IOException {
        // 确保 maps 目录存在
        Path mapsPath = Paths.get(MAPS_DIR);
        if (!Files.exists(mapsPath)) {
            Files.createDirectories(mapsPath);
        }

        // 保存到文件
        Path filePath = mapsPath.resolve(filename);
        try (Writer writer = new FileWriter(filePath.toFile())) {
            gson.toJson(mapData, writer);
        }

        return filePath.toString();
    }

    /**
     * 从文件加载地图
     */
    public static TrackMapData loadMap(String filepath) throws IOException {
        try (Reader reader = new FileReader(filepath)) {
            return gson.fromJson(reader, TrackMapData.class);
        }
    }

    /**
     * 列出所有保存的地图文件
     */
    public static File[] listMaps() {
        File mapsDir = new File(MAPS_DIR);
        if (!mapsDir.exists() || !mapsDir.isDirectory()) {
            return new File[0];
        }

        File[] files = mapsDir.listFiles((dir, name) -> name.endsWith(".json"));
        return files != null ? files : new File[0];
    }

    /**
     * 加载最新的地图文件
     */
    public static TrackMapData loadLatestMap() throws IOException {
        File[] maps = listMaps();
        if (maps.length == 0) {
            throw new IOException("No map files found in " + MAPS_DIR);
        }

        // 找到最新的文件
        File latestFile = maps[0];
        for (File file : maps) {
            if (file.lastModified() > latestFile.lastModified()) {
                latestFile = file;
            }
        }

        System.out.println("Loading map: " + latestFile.getName());
        return loadMap(latestFile.getPath());
    }
}
