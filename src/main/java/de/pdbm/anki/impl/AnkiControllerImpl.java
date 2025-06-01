package de.pdbm.anki.impl;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothManager;
import de.pdbm.janki.Vehicle;
import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.notifications.*;
import de.pdbm.anki.api.AnkiController;
import de.pdbm.anki.api.TrackMappingListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnkiControllerImpl implements AnkiController {

    private BluetoothManager bluetoothManager;
    private BluetoothDevice connectedDevice;
    private Vehicle vehicle;

    // 轨道信息
    private final Map<Integer, RoadPiece> trackMap = new ConcurrentHashMap<>();
    private int currentLocation = -1;
    private RoadPiece currentRoadPiece = null;
    private boolean isTrackMapping = false;
    private TrackMappingListener mappingListener;

    public AnkiControllerImpl() {
        this.bluetoothManager = BluetoothManager.getBluetoothManager();
    }

    @Override
    public List<String> scanDevices() {
        List<String> ankiDevices = new ArrayList<>();
        List<BluetoothDevice> devices = bluetoothManager.getDevices();

        for (BluetoothDevice device : devices) {
            List<String> uuids = device.getUUIDs();
            if (uuids != null) {
                for (String uuid : uuids) {
                    if (uuid.toLowerCase().contains("beef")) {
                        ankiDevices.add(device.getAddress());
                        break;
                    }
                }
            }
        }
        return ankiDevices;
    }

    @Override
    public boolean connect(String deviceAddress) {
        // 查找设备
        List<BluetoothDevice> devices = bluetoothManager.getDevices();
        BluetoothDevice targetDevice = null;

        for (BluetoothDevice device : devices) {
            if (device.getAddress().equals(deviceAddress)) {
                targetDevice = device;
                break;
            }
        }

        if (targetDevice == null) {
            return false;
        }

        // 连接设备
        boolean connected = targetDevice.connect();
        if (!connected) {
            return false;
        }

        this.connectedDevice = targetDevice;
        this.vehicle = new Vehicle(targetDevice);

        // 初始化特性
        try {
            Thread.sleep(1000); // 等待连接稳定
            boolean initialized = vehicle.initializeCharacteristics();
            if (initialized) {
                setupListeners();
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return false;
    }

    private void setupListeners() {
        // 位置更新监听器
        vehicle.addNotificationListener(new PositionUpdateListener() {
            @Override
            public void onPositionUpdate(PositionUpdate update) {
                currentLocation = update.getLocation();
                currentRoadPiece = update.getRoadPiece();

                // 添加到轨道地图
                trackMap.put(currentLocation, currentRoadPiece);

                // 如果正在进行轨道映射，通知监听器
                if (isTrackMapping && mappingListener != null) {
                    mappingListener.onTrackPieceDiscovered(currentLocation, currentRoadPiece);
                    mappingListener.onLocationUpdate(currentLocation, update.isAscendingLocations());
                }
            }
        });
    }

    @Override
    public void disconnect() {
        if (vehicle != null) {
            vehicle.setSpeed(0); // 停车
        }
        if (connectedDevice != null) {
            connectedDevice.disconnect();
            connectedDevice = null;
        }
        vehicle = null;
    }

    @Override
    public boolean isConnected() {
        return vehicle != null && vehicle.isConnected();
    }

    @Override
    public void setSpeed(int speed) {
        if (vehicle != null) {
            vehicle.setSpeed(speed);
        }
    }

    @Override
    public void stop() {
        setSpeed(0);
    }

    @Override
    public void changeLane(float offset) {
        if (vehicle != null) {
            vehicle.changeLane(offset);
        }
    }

    @Override
    public int getSpeed() {
        return vehicle != null ? vehicle.getSpeed() : 0;
    }

    @Override
    public int getCurrentLocation() {
        return currentLocation;
    }

    @Override
    public RoadPiece getCurrentRoadPiece() {
        return currentRoadPiece;
    }

    @Override
    public boolean isOnCharger() {
        return vehicle != null && vehicle.isOnCharger();
    }

    @Override
    public void startTrackMapping(int speed, TrackMappingListener listener) {
        if (vehicle == null) return;

        this.isTrackMapping = true;
        this.mappingListener = listener;
        vehicle.setSpeed(speed);
    }

    @Override
    public void stopTrackMapping() {
        this.isTrackMapping = false;
        this.mappingListener = null;
        if (vehicle != null) {
            vehicle.setSpeed(0);
        }
    }

    @Override
    public Map<Integer, RoadPiece> getTrackMap() {
        return new HashMap<>(trackMap);
    }

    @Override
    public void clearTrackMap() {
        trackMap.clear();
        currentLocation = -1;
        currentRoadPiece = null;
    }


}