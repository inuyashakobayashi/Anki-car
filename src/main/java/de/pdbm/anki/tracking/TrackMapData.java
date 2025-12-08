package de.pdbm.anki.tracking;

import de.pdbm.anki.tracking.SimpleTrackMapper.TrackPiece;

import java.util.*;

/**
 * 存储完整的轨道地图数据
 * 包括所有轨道片段及其 location ID 映射
 *
 * @author Zijian Ying
 */
public class TrackMapData {
    private final List<TrackPiece> pieces;
    private final String version = "1.0";
    private final long timestamp;
    private final int minX, maxX, minY, maxY;

    // (location ID + roadPieceType) -> piece 的映射（用于实时追踪）
    private transient Map<String, PieceLocationInfo> locationMap;

    public TrackMapData(List<TrackPiece> pieces) {
        this.pieces = new ArrayList<>(pieces);
        this.timestamp = System.currentTimeMillis();

        // 计算边界
        if (!pieces.isEmpty()) {
            this.minX = pieces.stream().mapToInt(p -> p.x).min().orElse(0);
            this.maxX = pieces.stream().mapToInt(p -> p.x).max().orElse(0);
            this.minY = pieces.stream().mapToInt(p -> p.y).min().orElse(0);
            this.maxY = pieces.stream().mapToInt(p -> p.y).max().orElse(0);
        } else {
            this.minX = this.maxX = this.minY = this.maxY = 0;
        }

        // 构建 location 映射
        buildLocationMap();
    }

    /**
     * 构建 locationId -> piece 的映射
     * 使用 roadPieceId 确保精确匹配
     */
    private void buildLocationMap() {
        locationMap = new HashMap<>();

        for (TrackPiece piece : pieces) {
            if (piece.startLocation != -1 && piece.endLocation != -1) {
                int start = Math.min(piece.startLocation, piece.endLocation);
                int end = Math.max(piece.startLocation, piece.endLocation);

                // 将这个范围内的所有 location 都映射到这个 piece
                for (int loc = start; loc <= end; loc++) {
                    double progress = 0.5; // 默认中点
                    if (end > start) {
                        progress = (loc - start) / (double)(end - start);
                    }
                    // 使用 (locationId, roadPieceId) 组合作为key - 更精确！
                    String key = makeLocationKeyWithId(loc, piece.roadPieceId);
                    locationMap.put(key, new PieceLocationInfo(piece, progress));
                }
            }
        }
    }

    /**
     * 创建 location 的组合键：locationId + roadPieceId（精确匹配）
     */
    private String makeLocationKeyWithId(int locationId, int roadPieceId) {
        return locationId + "_" + roadPieceId;
    }

    /**
     * 创建 location 的组合键：locationId + roadPieceType（fallback）
     */
    private String makeLocationKey(int locationId, de.pdbm.janki.RoadPiece roadPieceType) {
        return locationId + "_" + roadPieceType.name();
    }

    /**
     * 根据 locationId 和 roadPieceId 精确查找对应的 piece（推荐使用）
     */
    public PieceLocationInfo findPieceByLocationAndId(int locationId, int roadPieceId) {
        if (locationMap == null) {
            buildLocationMap();
        }

        // START(33) 和 FINISH(34) 是同一个物理片段，需要互相尝试
        int normalizedId = normalizeStartFinish(roadPieceId);
        int alternateId = getAlternateStartFinish(roadPieceId);

        String key = makeLocationKeyWithId(locationId, normalizedId);
        PieceLocationInfo result = locationMap.get(key);

        // 尝试备用 ID (START <-> FINISH)
        if (result == null && alternateId != normalizedId) {
            key = makeLocationKeyWithId(locationId, alternateId);
            result = locationMap.get(key);
        }

        // 如果精确匹配找不到，尝试查找最近的相同 roadPieceId 的片段
        if (result == null) {
            result = findNearestPieceById(locationId, normalizedId);
        }
        if (result == null && alternateId != normalizedId) {
            result = findNearestPieceById(locationId, alternateId);
        }

        return result;
    }

    /**
     * 标准化 START/FINISH ID
     */
    private int normalizeStartFinish(int roadPieceId) {
        // START=33, FINISH=34 都返回原值，让后续逻辑处理
        return roadPieceId;
    }

    /**
     * 获取 START/FINISH 的备用 ID
     */
    private int getAlternateStartFinish(int roadPieceId) {
        if (roadPieceId == 33) return 34;  // START -> FINISH
        if (roadPieceId == 34) return 33;  // FINISH -> START
        return roadPieceId;
    }

    /**
     * 根据 locationId 和 roadPieceType 查找对应的 piece 和进度
     * 如果精确匹配找不到，尝试查找同类型的相邻 location
     */
    public PieceLocationInfo findPieceByLocation(int locationId, de.pdbm.janki.RoadPiece roadPieceType) {
        if (locationMap == null) {
            buildLocationMap();
        }
        String key = makeLocationKey(locationId, roadPieceType);
        PieceLocationInfo result = locationMap.get(key);

        // 如果精确匹配找不到，尝试查找最近的同类型片段
        if (result == null) {
            result = findNearestPieceByType(locationId, roadPieceType);
        }

        return result;
    }

    /**
     * 查找最近的相同 roadPieceId 的片段（fallback策略）
     */
    private PieceLocationInfo findNearestPieceById(int locationId, int roadPieceId) {
        TrackPiece nearestPiece = null;
        int minDistance = Integer.MAX_VALUE;

        for (TrackPiece piece : pieces) {
            if (piece.roadPieceId == roadPieceId &&
                piece.startLocation != -1 && piece.endLocation != -1) {

                int start = Math.min(piece.startLocation, piece.endLocation);
                int end = Math.max(piece.startLocation, piece.endLocation);

                // 计算到这个片段范围的距离
                int distance;
                if (locationId < start) {
                    distance = start - locationId;
                } else if (locationId > end) {
                    distance = locationId - end;
                } else {
                    distance = 0; // 在范围内
                }

                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPiece = piece;
                }
            }
        }

        if (nearestPiece != null) {
            // 估算progress
            int start = Math.min(nearestPiece.startLocation, nearestPiece.endLocation);
            int end = Math.max(nearestPiece.startLocation, nearestPiece.endLocation);
            double progress = 0.5; // 默认中点

            if (end > start) {
                progress = Math.max(0.0, Math.min(1.0,
                    (locationId - start) / (double)(end - start)));
            }

            return new PieceLocationInfo(nearestPiece, progress);
        }

        return null;
    }

    /**
     * 查找最近的同类型片段（fallback策略）
     */
    private PieceLocationInfo findNearestPieceByType(int locationId, de.pdbm.janki.RoadPiece roadPieceType) {
        TrackPiece nearestPiece = null;
        int minDistance = Integer.MAX_VALUE;

        for (TrackPiece piece : pieces) {
            if (piece.roadPiece == roadPieceType &&
                piece.startLocation != -1 && piece.endLocation != -1) {

                int start = Math.min(piece.startLocation, piece.endLocation);
                int end = Math.max(piece.startLocation, piece.endLocation);

                // 计算到这个片段范围的距离
                int distance;
                if (locationId < start) {
                    distance = start - locationId;
                } else if (locationId > end) {
                    distance = locationId - end;
                } else {
                    distance = 0; // 在范围内
                }

                if (distance < minDistance) {
                    minDistance = distance;
                    nearestPiece = piece;
                }
            }
        }

        if (nearestPiece != null) {
            // 估算progress（如果在范围外，使用边界值）
            int start = Math.min(nearestPiece.startLocation, nearestPiece.endLocation);
            int end = Math.max(nearestPiece.startLocation, nearestPiece.endLocation);
            double progress = 0.5; // 默认中点

            if (end > start) {
                progress = Math.max(0.0, Math.min(1.0,
                    (locationId - start) / (double)(end - start)));
            }

            return new PieceLocationInfo(nearestPiece, progress);
        }

        return null;
    }

    /**
     * 获取所有片段
     */
    public List<TrackPiece> getPieces() {
        return Collections.unmodifiableList(pieces);
    }

    /**
     * 获取片段数量
     */
    public int getPieceCount() {
        return pieces.size();
    }

    /**
     * 打印统计信息
     */
    public void printStats() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 Track Map Statistics");
        System.out.println("=".repeat(80));
        System.out.println("Version: " + version);
        System.out.println("Timestamp: " + new Date(timestamp));
        System.out.println("Total pieces: " + pieces.size());
        System.out.println("Bounds: X[" + minX + ", " + maxX + "], Y[" + minY + ", " + maxY + "]");

        // 统计 location 映射
        if (locationMap != null) {
            System.out.println("Mapped location combinations: " + locationMap.size());
            // 示例：显示几个映射的key
            if (!locationMap.isEmpty()) {
                System.out.print("Sample keys: ");
                locationMap.keySet().stream().limit(5).forEach(key -> System.out.print(key + " "));
                System.out.println();
            }
        }

        System.out.println("=".repeat(80) + "\n");
    }

    /**
     * Location 信息：piece + 在 piece 内的进度
     */
    public static class PieceLocationInfo {
        public final TrackPiece piece;
        public final double progress;  // 0.0 = start, 1.0 = end

        public PieceLocationInfo(TrackPiece piece, double progress) {
            this.piece = piece;
            this.progress = Math.max(0.0, Math.min(1.0, progress));
        }
    }
}
