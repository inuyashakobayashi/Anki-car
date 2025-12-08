package de.pdbm.janki.notifications;

import de.pdbm.janki.Vehicle;

/**
 * 道路中心偏移更新通知 (0x2d)
 * 当车辆的横向位置发生变化时，会发送此通知
 * 用于精确追踪车辆在车道上的位置
 */
public class OffsetFromRoadCenterUpdate extends Notification {

    private final float offsetMm;
    private final int laneChangeId;

    public OffsetFromRoadCenterUpdate(Vehicle vehicle, float offsetMm, int laneChangeId) {
        super(vehicle);
        this.offsetMm = offsetMm;
        this.laneChangeId = laneChangeId;
    }

    /**
     * 获取距离道路中心的偏移量 (毫米)
     * 正值表示向右偏移，负值表示向左偏移
     */
    public float getOffsetMm() {
        return offsetMm;
    }

    /**
     * 获取关联的变道命令 ID
     */
    public int getLaneChangeId() {
        return laneChangeId;
    }

    @Override
    public String toString() {
        return "OffsetFromRoadCenterUpdate{offset=" + offsetMm + "mm, laneChangeId=" + laneChangeId + "}";
    }
}
