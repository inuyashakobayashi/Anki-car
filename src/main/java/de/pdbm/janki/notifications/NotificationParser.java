package de.pdbm.janki.notifications;

import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.Vehicle;

/**
 * Parser for BLE messages.
 * <p>
 * 
 * Based on C data types of Anki's SDK:
 * <a href="https://github.com/anki/drive-sdk/blob/master/include/ankidrive/protocol.h">protocol.h</a>
 * 
 * @author bernd
 *
 */
public class NotificationParser {

	// TODO nachschauen, ob's da noch was interessantes gibt:
	// https://github.com/IBM-Bluemix/node-mqtt-for-anki-overdrive/blob/master/receivedMessages.js
	
	static final byte POSITION_UPDATE   = 0x27; // decimal 39
	static final byte TRANSITION_UPDATE = 0x29; // decimal 41
	static final byte CHARGER_INFO      = 0x3f; // decimal 63
	static final byte INTERSECTION_UPDATE = 0x2a; // 42
    static final byte BATTERY_LEVEL_RESPONSE = 0x1b; // 27

    // --- 新增消息类型 ---
    static final byte PING_RESPONSE = 0x17;     // 23 - Ping 响应
    static final byte VERSION_RESPONSE = 0x19;  // 25 - 版本响应
    static final byte VEHICLE_DELOCALIZED = 0x2b; // 43 - 车辆脱轨
    static final byte OFFSET_FROM_ROAD_CENTER_UPDATE = 0x2d; // 45 - 偏移更新
	// am Ende immer {7, 54, ...} 0x36 und {3, 77, ...} 0x4D
	
	
	private NotificationParser() {
	}

	/**
	 * Parses the Anki BLE message for some vehicle and returns it as a sub type of {@link Notification}.
	 * 
	 * @param vehicle the source of this notification
	 * @param bytes the Anki BLE message
	 * @return the Notification sub type
	 * 
	 */
	public static Notification parse(Vehicle vehicle, byte[] bytes) {
		switch (bytes[1]) {
		
		case TRANSITION_UPDATE: {

			/* from protocol.h:
			typedef struct anki_vehicle_msg_localization_transition_update {
			    uint8_t     size;
			    uint8_t     msg_id;
			    uint8_t     road_piece_idx;
			    uint8_t     road_piece_idx_prev;
			    float       offset_from_road_center_mm;
			    uint8_t     driving_direction;
			    uint8_t     last_recv_lane_change_id;
			    uint8_t     last_exec_lane_change_id;
			    uint16_t    last_desired_horizontal_speed_mm_per_sec;
			    uint16_t    last_desired_speed_mm_per_sec;
			    uint8_t     uphill_counter; 
			    uint8_t     downhill_counter; 
			    uint8_t     left_wheel_dist_cm;
			    uint8_t     right_wheel_dist_cm;
			}
			*/
			
			/* 
			 * offset_from_road_center_mm (bytes[4], bytes[5], bytes[6], bytes[7])
			 * enthält immer identische Werte, nämlich 16 -65 31 73 
			 */
			
			// bytes[2] and bytes[3]) always 0, check this and return dummy
			if (bytes[2] != 0 || bytes[3] != 0) {
				// bei Thermo- und Nuke-Nachkauf bis Firmware-Update tatsächlich passiert
				throw new RuntimeException("Firmware has changed. Update 'NotificationParser.parse().");
			}
			return new TransitionUpdate(vehicle, 0, null);
		}
		
						
		case POSITION_UPDATE: {
			
			/* from protocol.h:
			typedef struct anki_vehicle_msg_localization_position_update {
			    uint8_t     size;
			    uint8_t     msg_id;
			    uint8_t     location_id;
			    uint8_t     road_piece_id;
			    float       offset_from_road_center_mm;
			    uint16_t    speed_mm_per_sec;
			    uint8_t     parsing_flags;
			    uint8_t     last_recv_lane_change_cmd_id;
			    uint8_t     last_exec_lane_change_cmd_id;
			    uint16_t    last_desired_horizontal_speed_mm_per_sec;
			    uint16_t    last_desired_speed_mm_per_sec;
			}
			*/
			
			/* 
			 * offset_from_road_center_mm (bytes[4], bytes[5], bytes[6], bytes[7])
			 * enthält immer identische Werte, nämlich 16 -65 31 73 
			 */
			
			int roadPieceId = bytes[3] & 0xFF; // Convert signed byte to unsigned int
			RoadPiece roadPiece = RoadPiece.valueOf(roadPieceId);
			return new PositionUpdate(vehicle, bytes[2], roadPieceId, roadPiece, ! ((bytes[10] & 0x40) == 0x40));
		}

		/*
			typedef struct anki_vehicle_msg_localization_intersection_update {
				uint8_t     size;
				uint8_t     msg_id;
				int8_t      road_piece_idx;
				float       offset_from_road_center_mm;

				uint8_t     intersection_code;
				uint8_t     is_exiting;
				uint16_t    mm_since_last_transition_bar;
				uint16_t    mm_since_last_intersection_code;
			}
		*/
		case INTERSECTION_UPDATE: {
			IntersectionCode intersectionCode = IntersectionCode.valueOf(bytes[7]);
			boolean isExisting = bytes[8] != 1;
			return new IntersectionUpdate(vehicle, intersectionCode, isExisting);
		}
		
		case CHARGER_INFO: {
			/* no Anki documentation found */
			return new ChargerInfoNotification(vehicle,
                    bytes[2] != 0, bytes[3] != 0,
                    bytes[4] != 0, bytes[5] != 0);
		}

		case BATTERY_LEVEL_RESPONSE: {
			int level = (bytes[2] & 0xFF) | ((bytes[3] & 0xFF) << 8);
			return new BatteryNotification(vehicle, level);
		}

		// --- 新增: Ping 响应 ---
		case PING_RESPONSE: {
			return new PingResponse(vehicle);
		}

		// --- 新增: 版本响应 ---
		case VERSION_RESPONSE: {
			// version 是 uint16_t，小端序
			int version = (bytes[2] & 0xFF) | ((bytes[3] & 0xFF) << 8);
			return new VersionResponse(vehicle, version);
		}

		// --- 新增: 车辆脱轨 ---
		case VEHICLE_DELOCALIZED: {
			return new DelocalizedNotification(vehicle);
		}

		// --- 新增: 偏移更新 ---
		case OFFSET_FROM_ROAD_CENTER_UPDATE: {
			/*
			 * from protocol.h:
			 * typedef struct anki_vehicle_msg_offset_from_road_center_update {
			 *     uint8_t     size;
			 *     uint8_t     msg_id;
			 *     float       offset_from_road_center_mm;
			 *     uint8_t     lane_change_id;
			 * }
			 */
			float offset = bytesToFloat(bytes[2], bytes[3], bytes[4], bytes[5]);
			int laneChangeId = bytes[6] & 0xFF;
			return new OffsetFromRoadCenterUpdate(vehicle, offset, laneChangeId);
		}

		default:
			return new DefaultNotification(vehicle, bytes);
		}
	}
	
	
	private static float bytesToFloat(byte byte0, byte byte1, byte byte2, byte byte3) {
		int asInt = (byte0 & 0xFF) | ((byte1 & 0xFF) << 8) | ((byte2 & 0xFF) << 16) | ((byte3 & 0xFF) << 24);
		return Float.intBitsToFloat(asInt);	
	}
	
}
