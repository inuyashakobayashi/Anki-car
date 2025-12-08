package de.pdbm.janki.notifications;

import de.pdbm.janki.Vehicle;

/**
 * 版本响应通知
 * 当车辆收到 VERSION_REQUEST (0x18) 后会返回此响应 (0x19)
 * 包含车辆固件版本号
 */
public class VersionResponse extends Notification {

    private final int version;

    public VersionResponse(Vehicle vehicle, int version) {
        super(vehicle);
        this.version = version;
    }

    /**
     * 获取固件版本号 (原始值)
     */
    public int getVersion() {
        return version;
    }

    /**
     * 获取格式化的版本字符串
     * 例如: 版本 0x2A30 -> "42.48" 或类似格式
     */
    public String getVersionString() {
        int major = (version >> 8) & 0xFF;
        int minor = version & 0xFF;
        return major + "." + minor;
    }

    @Override
    public String toString() {
        return "VersionResponse{version=" + version + " (0x" + Integer.toHexString(version) + "), formatted=" + getVersionString() + "}";
    }
}
