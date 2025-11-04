package de.pdbm.anki.tracking;

import de.pdbm.janki.RoadPiece;
import de.pdbm.anki.api.TrackMappingListener;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * 简化的轨道映射器 - 基于JAnki项目的Tracking实现
 *
 * 核心思想：
 * 1. 轨道在网格上，每个片段占据一个(x,y)坐标
 * 2. 追踪小车的方向（POSITIVE_X, NEGATIVE_Y, NEGATIVE_X, POSITIVE_Y）
 * 3. 使用isAscendingLocations来判断转向
 * 4. 当坐标回到(0,0)时，轨道完成
 *
 * @author Zijian Ying (based on Bernd's JAnki implementation)
 */
public class SimpleTrackMapper implements TrackMappingListener {

    private static final int X_START = 0;
    private static final int Y_START = 0;

    private final Deque<TrackPiece> trackPieces;
    private VehiclePosition oldPosition;
    private RoadPiece lastRoadPiece = null;
    private int lastLocation = -1;
    private boolean isAscending = true;

    private boolean gathering = false;
    private TrackMappingCallback callback;

    /**
     * 方向枚举 - 顺时针顺序很重要！
     */
    public enum Direction {
        POSITIVE_X,  // 向右 (East)
        NEGATIVE_Y,  // 向下 (South)
        NEGATIVE_X,  // 向左 (West)
        POSITIVE_Y;  // 向上 (North)

        public Direction increment() {
            return Direction.values()[(this.ordinal() + 1) % 4];
        }

        public Direction decrement() {
            return Direction.values()[(this.ordinal() + 3) % 4];
        }
    }

    /**
     * 小车位置信息
     */
    public static class VehiclePosition {
        final int x;
        final int y;
        final Direction direction;

        public VehiclePosition(int x, int y, Direction direction) {
            this.x = x;
            this.y = y;
            this.direction = direction;
        }
    }

    /**
     * 轨道片段 - 包含坐标和类型
     */
    public static class TrackPiece {
        public final int x;
        public final int y;
        public final RoadPiece roadPiece;
        public final char asciiChar;
        public final Direction enterDirection; // 进入此片段的方向
        public final Direction exitDirection;  // 离开此片段的方向

        public TrackPiece(int x, int y, RoadPiece roadPiece, char asciiChar, Direction enterDirection, Direction exitDirection) {
            this.x = x;
            this.y = y;
            this.roadPiece = roadPiece;
            this.asciiChar = asciiChar;
            this.enterDirection = enterDirection;
            this.exitDirection = exitDirection;
        }

        public TrackPiece shift(int dx, int dy) {
            return new TrackPiece(this.x + dx, this.y + dy, this.roadPiece, this.asciiChar, this.enterDirection, this.exitDirection);
        }

        @Override
        public String toString() {
            return String.format("(%d,%d) %s [%c]", x, y, roadPiece, asciiChar);
        }
    }

    /**
     * 回调接口
     */
    public interface TrackMappingCallback {
        void onTrackComplete(List<TrackPiece> pieces);
        void onPieceAdded(TrackPiece piece);
    }

    public SimpleTrackMapper() {
        this.trackPieces = new ConcurrentLinkedDeque<>();
    }

    /**
     * 开始地图收集
     */
    public void startMapping(TrackMappingCallback callback) {
        this.callback = callback;
        this.gathering = true;
        this.trackPieces.clear();
        this.oldPosition = null;
        this.lastRoadPiece = null;
        this.lastLocation = -1;
        System.out.println("🗺️ SimpleTrackMapper started");
    }

    /**
     * 停止地图收集
     */
    public void stopMapping() {
        this.gathering = false;
        System.out.println("⏹️ SimpleTrackMapper stopped");
    }

    /**
     * 获取收集的轨道片段
     */
    public List<TrackPiece> getTrackPieces() {
        return trackPieces.stream().collect(Collectors.toList());
    }

    /**
     * TrackMappingListener 实现 - 当发现新轨道片段时
     */
    @Override
    public void onTrackPieceDiscovered(int location, RoadPiece roadPiece) {
        if (!gathering) return;

        // 检查是否是新片段
        if (!isNewRoadPiece(location, roadPiece, isAscending)) {
            return;
        }

        System.out.printf("📍 New piece: Loc=%d, Type=%s, Ascending=%s\n",
                location, roadPiece, isAscending);

        // 第一个片段 - 初始化
        if (trackPieces.isEmpty()) {
            char c = getAsciiChar(roadPiece, Direction.POSITIVE_X, false);
            oldPosition = new VehiclePosition(X_START, Y_START, Direction.POSITIVE_X);

            // 如果第一个是CORNER，需要根据ascending判断初始方向
            if (roadPiece == RoadPiece.CORNER) {
                if (isAscending) {
                    c = '/';
                    oldPosition = new VehiclePosition(X_START, Y_START, Direction.POSITIVE_Y);
                } else {
                    c = '\\';
                    oldPosition = new VehiclePosition(X_START, Y_START, Direction.NEGATIVE_Y);
                }
            }

            // 第一个片段的exit方向暂时设为与enter相同
            TrackPiece piece = new TrackPiece(oldPosition.x, oldPosition.y, roadPiece, c, oldPosition.direction, oldPosition.direction);
            trackPieces.addLast(piece);
            System.out.println("  ✓ First piece: " + piece);

            if (callback != null) {
                callback.onPieceAdded(piece);
            }
            return;
        }

        // 计算新位置
        VehiclePosition newPosition = calculateNewPosition(oldPosition, roadPiece, isAscending);

        // 检查是否完成循环
        if (isTrackComplete(newPosition)) {
            gathering = false;
            System.out.println("\n" + "=".repeat(60));
            System.out.println("🔄 TRACK COMPLETE! Loop closed at (0,0)");
            System.out.println("Total pieces: " + trackPieces.size());
            System.out.println("=".repeat(60) + "\n");

            if (callback != null) {
                callback.onTrackComplete(getTrackPieces());
            }
            return;
        }

        // 检查坐标是否已存在（避免重复添加 INTERSECTION）
        boolean coordinateExists = trackPieces.stream()
                .anyMatch(p -> p.x == newPosition.x && p.y == newPosition.y);

        if (coordinateExists) {
            System.out.printf("  ⚠ Skipping duplicate at (%d,%d) - already recorded\n",
                    newPosition.x, newPosition.y);
            oldPosition = newPosition;
            return;
        }

        // 添加新片段
        char c = getAsciiChar(roadPiece, oldPosition.direction, isAscending);
        // 使用 oldPosition.direction 作为进入方向，newPosition.direction 作为离开方向
        TrackPiece piece = new TrackPiece(newPosition.x, newPosition.y, roadPiece, c, oldPosition.direction, newPosition.direction);
        trackPieces.addLast(piece);
        System.out.println("  ✓ Added: " + piece + " (enter from: " + oldPosition.direction + ", exit to: " + newPosition.direction + ")");

        if (callback != null) {
            callback.onPieceAdded(piece);
        }

        oldPosition = newPosition;
    }

    /**
     * TrackMappingListener 实现 - 位置更新
     */
    @Override
    public void onLocationUpdate(int location, boolean ascending) {
        // Update ascending state - critical for determining turn direction
        this.isAscending = ascending;
    }

    /**
     * 检查是否是新的轨道片段
     */
    private boolean isNewRoadPiece(int location, RoadPiece roadPiece, boolean ascending) {
        if (lastRoadPiece == null) {
            lastRoadPiece = roadPiece;
            lastLocation = location;
            return true;
        }

        // START 和 FINISH 是同一个 straight 片段的两个检测点，应视为同一类型
        RoadPiece normalizedRoadPiece = normalizeRoadPiece(roadPiece);
        RoadPiece normalizedLastRoadPiece = normalizeRoadPiece(lastRoadPiece);

        // 特殊情况：START 和 FINISH 互相转换（如 FINISH→START 或 START→FINISH）
        // 这种情况下它们是同一个物理片段的两个检测点，不应该被视为新片段
        if (isStartFinishTransition(lastRoadPiece, roadPiece)) {
            lastLocation = location;
            lastRoadPiece = roadPiece;
            return false;  // 还在同一个片段上
        }

        // 不同类型的片段肯定是新的
        if (normalizedLastRoadPiece != normalizedRoadPiece) {
            lastRoadPiece = roadPiece;
            lastLocation = location;
            return true;
        }

        // 同类型片段，检查location是否连续
        if (ascending) {
            if (lastLocation + 1 == location) {
                lastLocation = location;
                return false;  // 还在同一个片段上
            }
        } else {
            if (lastLocation - 1 == location) {
                lastLocation = location;
                return false;  // 还在同一个片段上
            }
        }

        lastLocation = location;
        lastRoadPiece = roadPiece;
        return true;
    }

    /**
     * 检查是否是 START 和 FINISH 之间的转换
     */
    private boolean isStartFinishTransition(RoadPiece piece1, RoadPiece piece2) {
        return (piece1 == RoadPiece.START && piece2 == RoadPiece.FINISH) ||
               (piece1 == RoadPiece.FINISH && piece2 == RoadPiece.START);
    }

    /**
     * 将 START 和 FINISH 标准化为 STRAIGHT
     * 因为它们实际上是同一个 straight 片段的两个检测点
     */
    private RoadPiece normalizeRoadPiece(RoadPiece roadPiece) {
        if (roadPiece == RoadPiece.START || roadPiece == RoadPiece.FINISH) {
            return RoadPiece.STRAIGHT;
        }
        return roadPiece;
    }

    /**
     * 根据当前位置和新片段计算新位置
     * ⭐ 这是核心算法！
     */
    private VehiclePosition calculateNewPosition(VehiclePosition current, RoadPiece roadPiece,
                                                  boolean ascending) {
        int newX = current.x;
        int newY = current.y;
        Direction newDirection = current.direction;

        switch (current.direction) {
            case POSITIVE_X:  // 向右移动
                if (roadPiece == RoadPiece.STRAIGHT || roadPiece == RoadPiece.START ||
                    roadPiece == RoadPiece.FINISH) {
                    newX = current.x + 1;
                } else if (roadPiece == RoadPiece.CORNER) {
                    newX = current.x + 1;
                    if (ascending) {
                        newDirection = current.direction.decrement();  // 左转
                    } else {
                        newDirection = current.direction.increment();  // 右转
                    }
                } else if (roadPiece == RoadPiece.INTERSECTION) {
                    // INTERSECTION: 继续直行，不改变方向
                    newX = current.x + 1;
                }
                break;

            case NEGATIVE_Y:  // 向下移动
                if (roadPiece == RoadPiece.STRAIGHT || roadPiece == RoadPiece.START ||
                    roadPiece == RoadPiece.FINISH) {
                    newY = current.y - 1;
                } else if (roadPiece == RoadPiece.CORNER) {
                    newY = current.y - 1;
                    if (ascending) {
                        newDirection = current.direction.decrement();
                    } else {
                        newDirection = current.direction.increment();
                    }
                } else if (roadPiece == RoadPiece.INTERSECTION) {
                    newY = current.y - 1;
                }
                break;

            case NEGATIVE_X:  // 向左移动
                if (roadPiece == RoadPiece.STRAIGHT || roadPiece == RoadPiece.START ||
                    roadPiece == RoadPiece.FINISH) {
                    newX = current.x - 1;
                } else if (roadPiece == RoadPiece.CORNER) {
                    newX = current.x - 1;
                    if (ascending) {
                        newDirection = current.direction.decrement();
                    } else {
                        newDirection = current.direction.increment();
                    }
                } else if (roadPiece == RoadPiece.INTERSECTION) {
                    newX = current.x - 1;
                }
                break;

            case POSITIVE_Y:  // 向上移动
                if (roadPiece == RoadPiece.STRAIGHT || roadPiece == RoadPiece.START ||
                    roadPiece == RoadPiece.FINISH) {
                    newY = current.y + 1;
                } else if (roadPiece == RoadPiece.CORNER) {
                    newY = current.y + 1;
                    if (ascending) {
                        newDirection = current.direction.decrement();
                    } else {
                        newDirection = current.direction.increment();
                    }
                } else if (roadPiece == RoadPiece.INTERSECTION) {
                    newY = current.y + 1;
                }
                break;
        }

        return new VehiclePosition(newX, newY, newDirection);
    }

    /**
     * 获取ASCII字符
     */
    private char getAsciiChar(RoadPiece roadPiece, Direction direction, boolean ascending) {
        if (roadPiece == RoadPiece.STRAIGHT || roadPiece == RoadPiece.START ||
            roadPiece == RoadPiece.FINISH) {
            if (direction == Direction.POSITIVE_X || direction == Direction.NEGATIVE_X) {
                return '-';
            } else {
                return '|';
            }
        } else if (roadPiece == RoadPiece.CORNER) {
            if (ascending) {
                if (direction == Direction.POSITIVE_X || direction == Direction.NEGATIVE_X) {
                    return '/';
                } else {
                    return '\\';
                }
            } else {
                if (direction == Direction.POSITIVE_X || direction == Direction.NEGATIVE_X) {
                    return '\\';
                } else {
                    return '/';
                }
            }
        } else if (roadPiece == RoadPiece.INTERSECTION) {
            return '+';
        }

        return '?';
    }

    /**
     * 检查轨道是否完成
     *
     * 改进：不依赖固定的 (0,0)，而是检查是否回到第一个片段的坐标
     */
    private boolean isTrackComplete(VehiclePosition position) {
        if (trackPieces.isEmpty()) {
            return false;
        }

        // 获取第一个片段的坐标
        TrackPiece firstPiece = trackPieces.getFirst();

        // 如果回到第一个片段的位置，且已经收集了足够的片段（至少4个），认为完成
        if (position.x == firstPiece.x && position.y == firstPiece.y && trackPieces.size() >= 4) {
            return true;
        }

        return false;
    }

    /**
     * 生成ASCII艺术轨道
     */
    public String getAsciiArt() {
        if (trackPieces.isEmpty()) {
            return "No track data";
        }

        List<TrackPiece> pieces = getTrackPieces();

        // 找到边界
        int minX = pieces.stream().mapToInt(p -> p.x).min().orElse(0);
        int minY = pieces.stream().mapToInt(p -> p.y).min().orElse(0);

        // 标准化坐标
        List<TrackPiece> normalized = pieces.stream()
                .map(p -> p.shift(-minX, -minY))
                .collect(Collectors.toList());

        int maxX = normalized.stream().mapToInt(p -> p.x).max().orElse(0);
        int maxY = normalized.stream().mapToInt(p -> p.y).max().orElse(0);

        // 创建网格
        char[][] grid = new char[maxY + 1][maxX + 1];
        for (int y = 0; y <= maxY; y++) {
            for (int x = 0; x <= maxX; x++) {
                grid[y][x] = ' ';
            }
        }

        // 填充轨道
        for (TrackPiece piece : normalized) {
            grid[piece.y][piece.x] = piece.asciiChar;
        }

        // 生成字符串（从上到下）
        StringBuilder sb = new StringBuilder();
        for (int y = maxY; y >= 0; y--) {
            sb.append(grid[y]);
            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * 打印详细报告
     */
    public void printReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SIMPLE TRACK MAPPER REPORT");
        System.out.println("=".repeat(80));
        System.out.println("Total pieces: " + trackPieces.size());
        System.out.println("\nPiece list:");
        int index = 0;
        for (TrackPiece piece : trackPieces) {
            System.out.printf("#%03d: %s\n", index++, piece);
        }

        System.out.println("\nASCII Art Track:");
        System.out.println("-".repeat(80));
        System.out.println(getAsciiArt());
        System.out.println("=".repeat(80) + "\n");
    }
}
