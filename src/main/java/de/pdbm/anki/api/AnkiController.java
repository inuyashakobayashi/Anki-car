// 1. 核心API接口 - AnkiController.java
package de.pdbm.anki.api;

import de.pdbm.janki.RoadPiece;
import java.util.Map;
import java.util.List;

/**
 * Anki Overdrive 小车控制的最小化API
 * 提供简单易用的接口来控制小车和收集轨道信息
 */
public interface AnkiController {

    // === 连接管理 ===
    /**
     * 扫描并返回可用的Anki设备
     * @return 设备地址列表
     */
    List<String> scanDevices();

    /**
     * 连接到指定设备
     * @param deviceAddress 设备MAC地址
     * @return 连接是否成功
     */
    boolean connect(String deviceAddress);

    /**
     * 断开连接
     */
    void disconnect();

    /**
     * 检查连接状态
     * @return 是否已连接
     */
    boolean isConnected();

    // === 基本控制 ===
    /**
     * 设置速度
     * @param speed 速度值 (0-1000)
     */
    void setSpeed(int speed);

    /**
     * 停车
     */
    void stop();

    /**
     * 换道
     * @param offset 车道偏移 (-1.0 到 1.0)
     */
    void changeLane(float offset);

    // === 状态查询 ===
    /**
     * 获取当前速度
     */
    int getSpeed();

    /**
     * 获取当前位置ID
     */
    int getCurrentLocation();

    /**
     * 获取当前轨道类型
     */
    RoadPiece getCurrentRoadPiece();

    /**
     * 是否在充电器上
     */
    boolean isOnCharger();

    // === 轨道映射 ===
    /**
     * 开始轨道映射
     * @param speed 映射时的行驶速度
     * @param listener 轨道信息回调监听器
     */
    void startTrackMapping(int speed, TrackMappingListener listener);

    /**
     * 停止轨道映射
     */
    void stopTrackMapping();

    /**
     * 获取已收集的轨道地图
     * @return 位置ID -> 轨道类型的映射
     */
    Map<Integer, RoadPiece> getTrackMap();

    /**
     * 清空轨道地图
     */
    void clearTrackMap();



}