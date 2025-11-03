package de.pdbm.anki.topology;

import de.pdbm.janki.RoadPiece;

import java.util.*;

/**
 * 轨道拓扑结构的表示类
 *
 * 记录轨道的物理结构，包括各个位置的连接关系和类型
 */
public class TrackTopology {

    /**
     * 轨道节点 - 表示轨道上的一个位置点
     */
    public static class TrackNode {
        private final int locationId;
        private final RoadPiece roadPiece;
        private final Set<Integer> nextLocations;  // 后继位置
        private final Set<Integer> prevLocations;  // 前驱位置

        public TrackNode(int locationId, RoadPiece roadPiece) {
            this.locationId = locationId;
            this.roadPiece = roadPiece;
            this.nextLocations = new HashSet<>();
            this.prevLocations = new HashSet<>();
        }

        public int getLocationId() {
            return locationId;
        }

        public RoadPiece getRoadPiece() {
            return roadPiece;
        }

        public Set<Integer> getNextLocations() {
            return Collections.unmodifiableSet(nextLocations);
        }

        public Set<Integer> getPrevLocations() {
            return Collections.unmodifiableSet(prevLocations);
        }

        public void addNextLocation(int next) {
            nextLocations.add(next);
        }

        public void addPrevLocation(int prev) {
            prevLocations.add(prev);
        }

        public void removeNextLocation(int next) {
            nextLocations.remove(next);
        }

        public void removePrevLocation(int prev) {
            prevLocations.remove(prev);
        }

        @Override
        public String toString() {
            return String.format("Node[id=%d, type=%s, next=%s, prev=%s]",
                locationId, roadPiece, nextLocations, prevLocations);
        }
    }

    /**
     * 轨道拓扑类型
     */
    public enum TopologyType {
        UNKNOWN,          // 未知
        SIMPLE_LOOP,      // 简单圈（单向循环）
        OVAL,             // 椭圆形（2个长边 + 2个短边，各由弯道连接）
        BIDIRECTIONAL_LOOP, // 双向圈
        FIGURE_EIGHT,     // 8字形
        BRANCHING,        // 有分支（交叉路口）
        COMPLEX           // 复杂结构
    }

    private final Map<Integer, TrackNode> nodes;
    private TopologyType type;

    public TrackTopology() {
        this.nodes = new LinkedHashMap<>();
        this.type = TopologyType.UNKNOWN;
    }

    /**
     * 添加或更新一个轨道节点
     */
    public void addNode(int locationId, RoadPiece roadPiece) {
        nodes.putIfAbsent(locationId, new TrackNode(locationId, roadPiece));
    }

    /**
     * 添加连接关系（从 from 到 to）
     */
    public void addConnection(int fromLocation, int toLocation) {
        TrackNode fromNode = nodes.get(fromLocation);
        TrackNode toNode = nodes.get(toLocation);

        if (fromNode != null && toNode != null) {
            fromNode.addNextLocation(toLocation);
            toNode.addPrevLocation(fromLocation);
        }
    }

    /**
     * 获取所有节点
     */
    public Map<Integer, TrackNode> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    /**
     * 获取拓扑类型
     */
    public TopologyType getType() {
        return type;
    }

    /**
     * 设置拓扑类型
     */
    public void setType(TopologyType type) {
        this.type = type;
    }

    /**
     * 获取轨道的总长度（节点数）
     */
    public int getTrackLength() {
        return nodes.size();
    }

    /**
     * 检查是否形成了完整的环路
     *
     * 改进版：即使图不完全连通，只要存在任何环路就返回true
     */
    public boolean hasCompleteCycle() {
        if (nodes.isEmpty()) {
            return false;
        }

        // 检查是否有任何节点能形成环路
        for (int startNode : nodes.keySet()) {
            Set<Integer> visited = new HashSet<>();
            if (hasCycleDFS(startNode, startNode, visited, true)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasCycleDFS(int current, int target, Set<Integer> visited, boolean firstStep) {
        if (!firstStep && current == target) {
            return true;
        }

        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        TrackNode node = nodes.get(current);
        if (node == null) {
            return false;
        }

        for (int next : node.getNextLocations()) {
            if (hasCycleDFS(next, target, visited, false)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 统计各类型赛道块的数量
     */
    public Map<RoadPiece, Integer> getRoadPieceStatistics() {
        Map<RoadPiece, Integer> stats = new EnumMap<>(RoadPiece.class);

        for (TrackNode node : nodes.values()) {
            stats.merge(node.getRoadPiece(), 1, Integer::sum);
        }

        return stats;
    }

    /**
     * 生成轨道的描述信息
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Track Topology ===\n");
        sb.append("Type: ").append(type).append("\n");
        sb.append("Total Segments: ").append(nodes.size()).append("\n");
        sb.append("Has Complete Cycle: ").append(hasCompleteCycle()).append("\n");
        sb.append("\n--- Road Piece Statistics ---\n");

        Map<RoadPiece, Integer> stats = getRoadPieceStatistics();
        for (Map.Entry<RoadPiece, Integer> entry : stats.entrySet()) {
            sb.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
        }

        sb.append("\n--- Node Details ---\n");
        for (TrackNode node : nodes.values()) {
            sb.append("  ").append(node).append("\n");
        }

        return sb.toString();
    }
}
