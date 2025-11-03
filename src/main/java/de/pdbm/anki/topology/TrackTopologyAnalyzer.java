package de.pdbm.anki.topology;

import de.pdbm.anki.api.TrackMappingListener;
import de.pdbm.janki.RoadPiece;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 轨道拓扑分析器
 *
 * 通过监听小车行驶过程中的位置更新，自动推断轨道的拓扑结构
 *
 * 使用方法：
 * <pre>{@code
 * TrackTopologyAnalyzer analyzer = new TrackTopologyAnalyzer();
 * controller.startTrackMapping(200, analyzer);
 * // 让小车行驶一段时间...
 * TrackTopology topology = analyzer.getTopology();
 * System.out.println(topology.getDescription());
 * }</pre>
 */
public class TrackTopologyAnalyzer implements TrackMappingListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TrackTopologyAnalyzer.class);

    private final TrackTopology topology;
    private final List<LocationRecord> history;  // 位置历史记录

    private Integer previousLocation;
    private boolean previousAscending;

    /**
     * 位置记录 - 记录某个时刻小车的位置和方向
     */
    private static class LocationRecord {
        final int locationId;
        final boolean ascending;
        final long timestamp;

        LocationRecord(int locationId, boolean ascending) {
            this.locationId = locationId;
            this.ascending = ascending;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("[%d, %s]", locationId, ascending ? "↑" : "↓");
        }
    }

    public TrackTopologyAnalyzer() {
        this.topology = new TrackTopology();
        this.history = new ArrayList<>();
        this.previousLocation = null;
        this.previousAscending = true;
    }

    @Override
    public void onTrackPieceDiscovered(int locationId, RoadPiece roadPiece) {
        LOGGER.info("Discovered: location={}, type={}", locationId, roadPiece);
        topology.addNode(locationId, roadPiece);
    }

    @Override
    public void onLocationUpdate(int locationId, boolean ascending) {
        // 记录历史
        history.add(new LocationRecord(locationId, ascending));

        // 如果有前一个位置，建立连接关系
        if (previousLocation != null && previousLocation != locationId) {
            // 根据方向判断连接关系
            if (ascending) {
                // 正向行驶：从 previous 到 current
                topology.addConnection(previousLocation, locationId);
            } else {
                // 反向行驶：从 current 到 previous
                topology.addConnection(locationId, previousLocation);
            }

            LOGGER.debug("Connection: {} -> {} (ascending={})",
                previousLocation, locationId, ascending);
        }

        // 更新状态
        previousLocation = locationId;
        previousAscending = ascending;
    }

    /**
     * 获取构建的轨道拓扑
     */
    public TrackTopology getTopology() {
        // 清理噪音连接
        cleanupTopology();
        // 在返回之前分析拓扑类型
        analyzeTopologyType();
        return topology;
    }

    /**
     * 清理拓扑结构中的噪音连接
     *
     * 策略：
     * 1. 统计边的访问频率
     * 2. 对于出度>1的节点，只保留最频繁的连接
     * 3. 合并双向连接（A⇄B 简化为单向环路）
     */
    private void cleanupTopology() {
        // 统计每条边的访问频率
        Map<String, Integer> edgeFrequency = new HashMap<>();

        Integer prev = null;
        for (LocationRecord record : history) {
            if (prev != null && prev != record.locationId) {
                String edge = prev + "->" + record.locationId;
                edgeFrequency.merge(edge, 1, Integer::sum);
            }
            prev = record.locationId;
        }

        LOGGER.debug("Edge frequency map: {}", edgeFrequency);

        // 第1步：移除低频噪音连接
        for (TrackTopology.TrackNode node : new ArrayList<>(topology.getNodes().values())) {
            if (node.getNextLocations().size() > 1) {
                // 找出频率最高的后继
                int maxFreq = 0;
                int bestNext = -1;

                for (int next : node.getNextLocations()) {
                    String edge = node.getLocationId() + "->" + next;
                    int freq = edgeFrequency.getOrDefault(edge, 0);
                    LOGGER.debug("Node {} -> {}: frequency = {}", node.getLocationId(), next, freq);
                    if (freq > maxFreq) {
                        maxFreq = freq;
                        bestNext = next;
                    }
                }

                // 移除其他连接
                Set<Integer> toRemove = new HashSet<>(node.getNextLocations());
                if (bestNext >= 0) {
                    toRemove.remove(bestNext);
                }

                for (int next : toRemove) {
                    node.removeNextLocation(next);
                    TrackTopology.TrackNode nextNode = topology.getNodes().get(next);
                    if (nextNode != null) {
                        nextNode.removePrevLocation(node.getLocationId());
                    }
                    LOGGER.info("Removed noisy connection: {} -> {}", node.getLocationId(), next);
                }
            }
        }

        // 第2步：检测并修复双向连接（A⇄B）
        // 这通常是小车在同一位置来回穿越导致的
        for (TrackTopology.TrackNode node : new ArrayList<>(topology.getNodes().values())) {
            for (int next : new HashSet<>(node.getNextLocations())) {
                TrackTopology.TrackNode nextNode = topology.getNodes().get(next);
                if (nextNode != null && nextNode.getNextLocations().contains(node.getLocationId())) {
                    // 发现双向连接 A⇄B
                    // 保留访问频率更高的方向
                    String forwardEdge = node.getLocationId() + "->" + next;
                    String backwardEdge = next + "->" + node.getLocationId();
                    int forwardFreq = edgeFrequency.getOrDefault(forwardEdge, 0);
                    int backwardFreq = edgeFrequency.getOrDefault(backwardEdge, 0);

                    LOGGER.info("Bidirectional connection detected: {} ⇄ {} (freq: {} vs {})",
                        node.getLocationId(), next, forwardFreq, backwardFreq);

                    // 保留频率高的，移除频率低的
                    if (forwardFreq < backwardFreq) {
                        node.removeNextLocation(next);
                        nextNode.removePrevLocation(node.getLocationId());
                        LOGGER.info("Kept {} -> {}, removed {} -> {}",
                            next, node.getLocationId(), node.getLocationId(), next);
                    } else {
                        nextNode.removeNextLocation(node.getLocationId());
                        node.removePrevLocation(next);
                        LOGGER.info("Kept {} -> {}, removed {} -> {}",
                            node.getLocationId(), next, next, node.getLocationId());
                    }
                }
            }
        }
    }

    /**
     * 获取位置历史记录
     */
    public List<LocationRecord> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /**
     * 分析轨道拓扑类型
     */
    private void analyzeTopologyType() {
        if (topology.getNodes().isEmpty()) {
            topology.setType(TrackTopology.TopologyType.UNKNOWN);
            return;
        }

        // 检查是否有交叉路口
        boolean hasIntersection = topology.getNodes().values().stream()
            .anyMatch(node -> node.getRoadPiece() == RoadPiece.INTERSECTION);

        if (hasIntersection) {
            topology.setType(TrackTopology.TopologyType.BRANCHING);
            return;
        }

        // 检查节点的连接度
        int maxOutDegree = topology.getNodes().values().stream()
            .mapToInt(node -> node.getNextLocations().size())
            .max()
            .orElse(0);

        int maxInDegree = topology.getNodes().values().stream()
            .mapToInt(node -> node.getPrevLocations().size())
            .max()
            .orElse(0);

        // 如果有节点的入度或出度大于1，说明有分支
        if (maxOutDegree > 1 || maxInDegree > 1) {
            topology.setType(TrackTopology.TopologyType.COMPLEX);
            return;
        }

        // 检查是否形成环路
        if (topology.hasCompleteCycle()) {
            // 检查是否有双向连接（既有 A->B 又有 B->A）
            boolean isBidirectional = checkBidirectional();

            if (isBidirectional) {
                topology.setType(TrackTopology.TopologyType.BIDIRECTIONAL_LOOP);
            } else {
                // 检查是否是8字形
                if (detectFigureEight()) {
                    topology.setType(TrackTopology.TopologyType.FIGURE_EIGHT);
                } else if (detectOval()) {
                    topology.setType(TrackTopology.TopologyType.OVAL);
                } else {
                    topology.setType(TrackTopology.TopologyType.SIMPLE_LOOP);
                }
            }
        } else {
            topology.setType(TrackTopology.TopologyType.UNKNOWN);
        }
    }

    /**
     * 检查是否有双向连接
     */
    private boolean checkBidirectional() {
        for (TrackTopology.TrackNode node : topology.getNodes().values()) {
            for (int next : node.getNextLocations()) {
                TrackTopology.TrackNode nextNode = topology.getNodes().get(next);
                if (nextNode != null && nextNode.getNextLocations().contains(node.getLocationId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检测是否是8字形轨道
     *
     * 8字形的特征：
     * 1. 有一个中心交叉点（但不是 INTERSECTION 类型）
     * 2. 从该点可以到达两个不同的环路
     */
    private boolean detectFigureEight() {
        // 找到入度和出度都为2的节点（可能是8字的中心）
        for (TrackTopology.TrackNode node : topology.getNodes().values()) {
            if (node.getNextLocations().size() == 2 && node.getPrevLocations().size() == 2) {
                // 可能是8字中心点
                return true;
            }
        }
        return false;
    }

    /**
     * 检测是否是椭圆形轨道
     *
     * 椭圆形特征：
     * 1. 2-4个CORNER（Anki轨道中一个弯道可能对应多个location）
     * 2. CORNER之间有直道连接
     * 3. 形成一个简单环路
     * 4. STRAIGHT数量 > CORNER数量（说明有较长的直道段）
     */
    private boolean detectOval() {
        Map<RoadPiece, Integer> stats = topology.getRoadPieceStatistics();
        int cornerCount = stats.getOrDefault(RoadPiece.CORNER, 0);
        int straightCount = stats.getOrDefault(RoadPiece.STRAIGHT, 0);

        // 椭圆：2-4个corner + straight数量要多于corner
        // 这是因为椭圆有两个长边（直道）和两个短边（弯道）
        if (cornerCount >= 2 && cornerCount <= 4 && straightCount > cornerCount) {
            LOGGER.debug("Oval pattern detected: {} corners, {} straights", cornerCount, straightCount);
            return true;
        }

        return false;
    }

    /**
     * 重置分析器状态
     */
    public void reset() {
        topology.getNodes().clear();
        history.clear();
        previousLocation = null;
        previousAscending = true;
        LOGGER.info("Analyzer reset");
    }

    /**
     * 获取轨道序列的字符串表示（用于调试）
     *
     * 例如: "START -> STRAIGHT -> CORNER -> CORNER -> STRAIGHT -> START"
     */
    public String getTrackSequence() {
        if (history.isEmpty()) {
            return "No data";
        }

        StringBuilder sb = new StringBuilder();
        Set<Integer> visited = new HashSet<>();

        for (LocationRecord record : history) {
            if (!visited.contains(record.locationId)) {
                TrackTopology.TrackNode node = topology.getNodes().get(record.locationId);
                if (node != null) {
                    if (sb.length() > 0) {
                        sb.append(" -> ");
                    }
                    sb.append(node.getRoadPiece());
                    visited.add(record.locationId);
                }
            }
        }

        return sb.toString();
    }

    /**
     * 打印分析报告
     */
    public void printReport() {
        System.out.println("\n" + topology.getDescription());
        System.out.println("\n--- Track Sequence ---");
        System.out.println(getTrackSequence());
        System.out.println("\n--- Location History (last 20) ---");

        int start = Math.max(0, history.size() - 20);
        for (int i = start; i < history.size(); i++) {
            System.out.println("  " + history.get(i));
        }
    }
}
