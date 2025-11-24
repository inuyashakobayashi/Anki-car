package de.pdbm.janki.notifications;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


/**
 * BLE-Nachrichtenformat für Anki als Byte-Array.
 * Erstes Byte: Anzahl der Bytes
 * Zweites Byte: Identifier
 * Rest: Payload
 * 
 * <p>
 * Originaldokumentation:
 * <a href="https://github.com/anki/drive-sdk/blob/master/include/ankidrive/protocol.h">protocol.h</a>
 * </p>
 * 
 * @author bernd
 *
 */
public class Message {

	private static final byte SET_SPEED = 0x24;
	private static final byte CHANGE_LANE= 0x25;
	private static final byte SET_OFFSET_FROM_ROAD_CENTER = 0x2c;
	private static final byte DISCONNECT = 0x0d;

    // 1. 定义指令 ID (参考 protocol.h)
    private static final byte BATTERY_LEVEL_REQUEST = 0x1a;
    // --- 新增掉头相关常量 ---
    private static final byte TURN = 0x32; // 50

    public static final byte TURN_LEFT = 1;
    public static final byte TURN_RIGHT = 2;
    public static final byte TURN_UTURN = 3;      // 普通掉头
    public static final byte TURN_UTURN_JUMP = 4; // 弹射掉头 (更猛)

    public static final byte TRIGGER_IMMEDIATE = 0;    // 立即执行
    public static final byte TRIGGER_INTERSECTION = 1; // 在路口执行

    // --- 车灯相关常量 ---
    private static final byte SET_LIGHTS = 0x1d; // 29

    // 灯光通道定义
    public static final byte LIGHT_HEADLIGHTS  = 0; // 前大灯
    public static final byte LIGHT_BRAKELIGHTS = 1; // 刹车灯
    public static final byte LIGHT_FRONTLIGHTS = 2; // 前信号灯
    public static final byte LIGHT_ENGINE      = 3; // 引擎灯

    /**
     * 构建车灯控制指令
     * * Anki 的灯光掩码逻辑：
     * 低4位 (0-3) 表示“是否修改该灯” (Valid 位)
     * 高4位 (4-7) 表示“该灯的新状态” (1=开, 0=关)
     * * @param lightId 灯的ID (0-3)
     * @param on true=开, false=关
     */
    public static byte[] setLightsMessage(byte lightId, boolean on) {
        // 构造掩码
        int validBit = 1 << lightId;           // 比如前灯是 0001
        int valueBit = (on ? 1 : 0) << (lightId + 4); // 前灯开是 0001 0000

        // 组合起来
        byte mask = (byte) (validBit | valueBit);

        // 格式: { 长度(2), ID(0x1d), 掩码 }
        return new byte[] { 2, SET_LIGHTS, mask };
    }

    /**
     * 一次性控制所有灯
     */
    public static byte[] setAllLightsMessage(boolean on) {
        // Valid: 1111 (0x0F) - 修改所有灯
        // Value: 1111 (0xF0) 或 0000 (0x00)
        byte mask = (byte) (0x0F | (on ? 0xF0 : 0x00));
        return new byte[] { 2, SET_LIGHTS, mask };
    }
    /**
     * 构建掉头指令
     * 结构: { 长度(3), ID(0x32), 类型, 触发时机 }
     */
    public static byte[] turnMessage(byte type, byte trigger) {
        return new byte[] { 3, TURN, type, trigger };
    }

    // 2. 添加这个静态方法，用来生成“查询电量”的指令数据
    public static byte[] batteryLevelRequest() {
        // 格式: { 长度, ID }
        return new byte[] { 1, BATTERY_LEVEL_REQUEST };
    }
	/**
	 * Returns message representing 'set sdk mode to 1'.
	 * This message must be send after connecting a device.
	 * 
	 * @return message representing 'set sdk mode to 1'
	 * 
	 */
	public static byte[] getSdkMode() {
		return new byte[] {3, -112, 1, 1};
	}
	

	/**
	 * Disconnects device with ANKI message instead of bluetooth disconnect
	 * 
	 * @return message representing the disconnect message
	 */
	public static byte[] disconnectMessage() {
		return new byte[]{1, DISCONNECT};
	}
	

	/**
	 * 
	 * @param speed the speed
	 * @return message representing a speed message using the given speed
	 */
	public static byte[] speedMessage(short speed) {
		return speedMessage(speed, (short) 10000);
	}
	
	/**
	 * Returns a speed message using the given speed and acceleration.
	 * 
	 * @param speed the speed 
	 * @param acceleration the acceleration
	 * @return message representing a speed message using the given speed and acceleration
	 * 
	 */
	public static byte[] speedMessage(short speed, short acceleration) {
		/* from protocol.h:
		typedef struct anki_vehicle_msg_set_speed {
		    uint8_t     size;
		    uint8_t     msg_id;
		    int16_t     speed_mm_per_sec;  // mm/sec
		    int16_t     accel_mm_per_sec2; // mm/sec^2
		    uint8_t     respect_road_piece_speed_limit;
		}
		*/
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream( );
			os.write(new byte[] {6, SET_SPEED});
			os.write(shortToBytes(speed));
			os.write(shortToBytes(acceleration));
			os.write(new byte[] {0});
			return os.toByteArray();
		} catch (IOException e) {

			return null;
		}
	}
	
	
	public static byte[] changeLaneMessage(short speed, short acceleration, float offset)  {
		/* from protocol.h: 
		typedef struct anki_vehicle_msg_change_lane {
	    uint8_t     size;
	    uint8_t     msg_id;
	    uint16_t    horizontal_speed_mm_per_sec;
	    uint16_t    horizontal_accel_mm_per_sec2; 
	    float       offset_from_road_center_mm;
	    uint8_t     hop_intent;
	    uint8_t     tag;
		}
		*/
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream( );
			os.write(new byte[] {11, CHANGE_LANE});
			os.write(shortToBytes(speed));
			os.write(shortToBytes(acceleration));
			os.write(floatToBytes(offset));
			os.write(new byte[] {0});
			os.write(new byte[] {0});
			return os.toByteArray();
		} catch (IOException e) {

			return null;
		}
	}
	
	
	/**
	 * Must be used before a change lane message to calibrate.
	 * 
	 * 
	 * @return the offset from road center message
	 */
	public static byte[] setOffsetFromRoadCenter() {
		/* from protocol.h:
		typedef struct anki_vehicle_msg_set_offset_from_road_center {
		    uint8_t     size;
		    uint8_t     msg_id;
		    float       offset_mm;
		}
		*/
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream( );
			os.write(new byte[] {5, SET_OFFSET_FROM_ROAD_CENTER});
			os.write(floatToBytes(0.0f));
			return os.toByteArray();
		} catch (IOException e) {

			return null;
		}
	}

	private static byte[] shortToBytes(short value) {
		byte[] bytes = new byte[2];
		bytes[0] = (byte)(value & 0xff);
		bytes[1] = (byte)((value >> 8) & 0xff);
		return bytes;
	}
	
	
	private static byte[] floatToBytes(float value) {
		int bits = Float.floatToIntBits(value);
		byte[] bytes = new byte[4];
		bytes[0] = (byte)(bits & 0xff);
		bytes[1] = (byte)((bits >> 8) & 0xff);
		bytes[2] = (byte)((bits >> 16) & 0xff);
		bytes[3] = (byte)((bits >> 24) & 0xff);
		return bytes;
	}
}
