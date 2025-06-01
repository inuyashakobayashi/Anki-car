package de.pdbm.anki.api;

import de.pdbm.janki.RoadPiece;

// 2. 轨道映射监听器 - TrackMappingListener.java
public interface TrackMappingListener {
    /**
     * 发现新的轨道片段时回调
     * @param locationId 位置ID
     * @param roadPiece 轨道类型
     */
    void onTrackPieceDiscovered(int locationId, RoadPiece roadPiece);

    /**
     * 位置更新时回调
     * @param locationId 当前位置ID
     * @param ascending 是否为正向行驶
     */
    void onLocationUpdate(int locationId, boolean ascending);
}


