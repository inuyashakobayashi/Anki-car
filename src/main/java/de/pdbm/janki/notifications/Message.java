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
	private static final byte CHANGE_LANE = 0x25;
	private static final byte CANCEL_LANE_CHANGE = 0x26;  // 取消变道
	private static final byte SET_OFFSET_FROM_ROAD_CENTER = 0x2c;
	private static final byte DISCONNECT = 0x0d;
	private static final byte LIGHTS_PATTERN = 0x33;  // 灯光图案

    // 1. 定义指令 ID (参考 protocol.h)
    private static final byte BATTERY_LEVEL_REQUEST = 0x1a;

    // --- Ping 相关常量 ---
    private static final byte PING_REQUEST = 0x16;  // 22

    // --- 版本查询相关常量 ---
    private static final byte VERSION_REQUEST = 0x18;  // 24
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

    // 灯光通道定义 (用于 SET_LIGHTS 简单控制)
    public static final byte LIGHT_HEADLIGHTS  = 0; // 前大灯
    public static final byte LIGHT_BRAKELIGHTS = 1; // 刹车灯
    public static final byte LIGHT_FRONTLIGHTS = 2; // 前信号灯
    public static final byte LIGHT_ENGINE      = 3; // 引擎灯

    // --- 灯光图案 (LIGHTS_PATTERN) 相关常量 ---
    // RGB 灯光通道定义
    public static final byte LIGHT_CHANNEL_RED    = 0;  // 红色 LED
    public static final byte LIGHT_CHANNEL_TAIL   = 1;  // 尾灯
    public static final byte LIGHT_CHANNEL_BLUE   = 2;  // 蓝色 LED
    public static final byte LIGHT_CHANNEL_GREEN  = 3;  // 绿色 LED
    public static final byte LIGHT_CHANNEL_FRONT_L = 4; // 左前灯
    public static final byte LIGHT_CHANNEL_FRONT_R = 5; // 右前灯

    // 灯光效果类型
    public static final byte EFFECT_STEADY  = 0;  // 稳定亮度
    public static final byte EFFECT_FADE    = 1;  // 渐变 (从 start 到 end)
    public static final byte EFFECT_THROB   = 2;  // 呼吸灯 (start -> end -> start)
    public static final byte EFFECT_FLASH   = 3;  // 闪烁
    public static final byte EFFECT_RANDOM  = 4;  // 随机闪烁

    // 最大亮度值
    public static final byte MAX_LIGHT_INTENSITY = 14;
    public static final byte MAX_LIGHT_TIME = 11;

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

    // 2. 添加这个静态方法，用来生成"查询电量"的指令数据
    public static byte[] batteryLevelRequest() {
        // 格式: { 长度, ID }
        return new byte[] { 1, BATTERY_LEVEL_REQUEST };
    }

    /**
     * 构建 Ping 请求指令
     * 用于检测车辆连接状态，车辆会返回 PING_RESPONSE (0x17)
     */
    public static byte[] pingRequest() {
        return new byte[] { 1, PING_REQUEST };
    }

    /**
     * 构建版本查询指令
     * 车辆会返回 VERSION_RESPONSE (0x19)，包含固件版本号
     */
    public static byte[] versionRequest() {
        return new byte[] { 1, VERSION_REQUEST };
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

	// ==================== 新增功能 ====================

	/**
	 * 构建取消变道指令
	 * 用于中断正在进行的变道动作
	 */
	public static byte[] cancelLaneChangeMessage() {
		return new byte[] { 1, CANCEL_LANE_CHANGE };
	}

	/**
	 * 构建单通道灯光图案指令
	 *
	 * @param channel 灯光通道 (LIGHT_CHANNEL_RED, LIGHT_CHANNEL_BLUE, etc.)
	 * @param effect 效果类型 (EFFECT_STEADY, EFFECT_FADE, EFFECT_THROB, EFFECT_FLASH, EFFECT_RANDOM)
	 * @param start 起始亮度 (0-14)
	 * @param end 结束亮度 (0-14)
	 * @param cyclesPerMin 每分钟循环次数 (用于动态效果)
	 */
	public static byte[] lightsPatternMessage(byte channel, byte effect, byte start, byte end, int cyclesPerMin) {
		/*
		 * from protocol.h:
		 * typedef struct anki_vehicle_light_config {
		 *     uint8_t channel;
		 *     uint8_t effect;
		 *     uint8_t start;
		 *     uint8_t end;
		 *     uint8_t cycles_per_10_sec;
		 * }
		 *
		 * typedef struct anki_vehicle_msg_lights_pattern {
		 *     uint8_t size;
		 *     uint8_t msg_id;
		 *     uint8_t channel_count;
		 *     anki_vehicle_light_config_t channel_config[3];
		 * }
		 */
		// 将每分钟转换为每10秒
		byte cyclesPer10Sec = (byte) Math.min(255, (cyclesPerMin * 10) / 60);

		return new byte[] {
			7,                    // size: 1(msg_id) + 1(channel_count) + 5(config)
			LIGHTS_PATTERN,       // msg_id
			1,                    // channel_count
			channel,              // channel
			effect,               // effect
			start,                // start intensity
			end,                  // end intensity
			cyclesPer10Sec        // cycles_per_10_sec
		};
	}

	/**
	 * 构建多通道灯光图案指令 (最多3个通道)
	 *
	 * @param configs 灯光配置数组，每个配置包含 {channel, effect, start, end, cyclesPerMin}
	 */
	public static byte[] lightsPatternMessage(int[][] configs) {
		if (configs == null || configs.length == 0 || configs.length > 3) {
			throw new IllegalArgumentException("Must provide 1-3 light configs");
		}

		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			int size = 2 + (configs.length * 5); // msg_id + channel_count + configs
			os.write(new byte[] { (byte) size, LIGHTS_PATTERN, (byte) configs.length });

			for (int[] config : configs) {
				byte channel = (byte) config[0];
				byte effect = (byte) config[1];
				byte start = (byte) config[2];
				byte end = (byte) config[3];
				byte cyclesPer10Sec = (byte) Math.min(255, (config[4] * 10) / 60);

				os.write(new byte[] { channel, effect, start, end, cyclesPer10Sec });
			}

			return os.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	// ==================== 便捷灯光方法 ====================

	/**
	 * 红色呼吸灯效果
	 */
	public static byte[] lightsPatternRedThrob(int cyclesPerMin) {
		return lightsPatternMessage(LIGHT_CHANNEL_RED, EFFECT_THROB, (byte) 0, MAX_LIGHT_INTENSITY, cyclesPerMin);
	}

	/**
	 * 蓝色呼吸灯效果
	 */
	public static byte[] lightsPatternBlueThrob(int cyclesPerMin) {
		return lightsPatternMessage(LIGHT_CHANNEL_BLUE, EFFECT_THROB, (byte) 0, MAX_LIGHT_INTENSITY, cyclesPerMin);
	}

	/**
	 * 绿色呼吸灯效果
	 */
	public static byte[] lightsPatternGreenThrob(int cyclesPerMin) {
		return lightsPatternMessage(LIGHT_CHANNEL_GREEN, EFFECT_THROB, (byte) 0, MAX_LIGHT_INTENSITY, cyclesPerMin);
	}

	/**
	 * 警示闪烁效果 (红色快闪)
	 */
	public static byte[] lightsPatternWarningFlash() {
		return lightsPatternMessage(LIGHT_CHANNEL_RED, EFFECT_FLASH, (byte) 0, MAX_LIGHT_INTENSITY, 120);
	}

	/**
	 * 警车效果 (红蓝交替)
	 */
	public static byte[] lightsPatternPolice() {
		return lightsPatternMessage(new int[][] {
			{ LIGHT_CHANNEL_RED, EFFECT_FLASH, 0, MAX_LIGHT_INTENSITY, 60 },
			{ LIGHT_CHANNEL_BLUE, EFFECT_FLASH, MAX_LIGHT_INTENSITY, 0, 60 }
		});
	}

	/**
	 * 彩虹效果 (RGB 渐变)
	 */
	public static byte[] lightsPatternRainbow() {
		return lightsPatternMessage(new int[][] {
			{ LIGHT_CHANNEL_RED, EFFECT_THROB, 0, MAX_LIGHT_INTENSITY, 20 },
			{ LIGHT_CHANNEL_GREEN, EFFECT_THROB, 5, MAX_LIGHT_INTENSITY, 15 },
			{ LIGHT_CHANNEL_BLUE, EFFECT_THROB, 10, MAX_LIGHT_INTENSITY, 25 }
		});
	}

	/**
	 * 关闭所有 RGB 灯光图案
	 */
	public static byte[] lightsPatternOff() {
		return lightsPatternMessage(new int[][] {
			{ LIGHT_CHANNEL_RED, EFFECT_STEADY, 0, 0, 0 },
			{ LIGHT_CHANNEL_GREEN, EFFECT_STEADY, 0, 0, 0 },
			{ LIGHT_CHANNEL_BLUE, EFFECT_STEADY, 0, 0, 0 }
		});
	}
}
