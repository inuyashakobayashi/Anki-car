package de.pdbm.janki;

import de.ostfalia.ble.BluetoothDevice;
import de.ostfalia.ble.BluetoothGattCharacteristic;
import de.ostfalia.ble.BluetoothGattService;
import de.ostfalia.ble.BluetoothManager;
import de.pdbm.janki.notifications.*;
import de.pdbm.janki.notifications.ConnectedNotificationListener;
import de.pdbm.janki.notifications.IntersectionUpdate;
import de.pdbm.janki.notifications.IntersectionUpdateListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Class Vehicle represent a Anki Overdrive vehicle.
 * 
 * @author bernd
 *
 */
public class Vehicle {

	public static final Map<Vehicle, Long> vehicles = new ConcurrentHashMap<>();

	private final BluetoothDevice bluetoothDevice; // device representing this vehicle

	private BluetoothGattCharacteristic readCharacteristic;

	private BluetoothGattCharacteristic writeCharacteristic;

	private final Collection<NotificationListener> listeners;

	private int speed;

	private boolean connected;

	private boolean onCharger;
	// 在 Vehicle 类中添加一个方法来强制初始化特性
	public boolean initializeCharacteristics() {
		boolean success = false;



		try {
			// 确保设备已连接
			if (!this.bluetoothDevice.getConnected()) {
				this.bluetoothDevice.connect();
				Thread.sleep(1000); // 给连接一些时间
			}

			// 初始化写特性
			if (this.writeCharacteristic == null) {
				this.writeCharacteristic = AnkiBle.writeCharacteristicFor(this.bluetoothDevice);
				if (this.writeCharacteristic != null) {
					this.writeCharacteristic.writeValue(Message.getSdkMode());
					Thread.sleep(500); // 给SDK模式命令一些处理时间
				} else {
				}
			} else {
			}

			// 初始化读特性
			if (this.readCharacteristic == null) {
				this.readCharacteristic = AnkiBle.readCharacteristicFor(this.bluetoothDevice);
				if (this.readCharacteristic != null) {
					this.readCharacteristic.enableValueNotifications(this::onValueNotification);
				} else {
					System.out.println("读特性初始化失败！");
				}
			} else {
				System.out.println("Characteristic already exists: " + this.readCharacteristic.getUUID());
			}

			// 设置返回值
			success = (this.writeCharacteristic != null && this.readCharacteristic != null);
			System.out.println("read Characteristic initialization state: " + (success ? "successful" : "fail"));

			// 如果初始化成功，再发送一次SDK模式命令确保激活
			if (success) {
				System.out.println("Send the SDK mode command again...");
				this.writeCharacteristic.writeValue(Message.getSdkMode());
			}

		} catch (Exception e) {
			System.out.println("初始化特性过程中出错: " + e.getMessage());
			e.printStackTrace();
		}

		return success;
	}


	public Vehicle(BluetoothDevice bluetoothDevice) {
		this.listeners = new ConcurrentLinkedDeque<>();
		this.bluetoothDevice = bluetoothDevice;
		this.addNotificationListener(new DefaultConnectedNotificationListener());
		this.addNotificationListener(new DefaultChargerInfoNotificationListener());


	}




	/**
	 * Returns a list of all known vehicles.
	 * Eventually not all vehicles are connected to the BLE device.
	 *
	 * @return list of known vehicles
	 */
	public static List<Vehicle> getAllVehicles() {
		return new ArrayList<>(vehicles.keySet());
	}

	/**
	 * Returns the vehicle for this MAC address.
	 *
	 * @param mac vehicle's MAC address
	 * @return the vehicle
	 */
	public static Vehicle get(String mac) {
		Set<Vehicle> tmp = vehicles.keySet();
		Optional<Vehicle> any = tmp.stream().filter(v -> v.getMacAddress().equals(mac)).findAny();
		return any.get();
	}

	/**
	 * Sets the speed of this vehicle.
	 *
	 * @param speed, usually between 0 and 1000
	 */
	public void setSpeed(int speed) {
		if (bluetoothDevice.getConnected()) {
			writeCharacteristic.writeValue(Message.speedMessage((short) speed));
			this.speed = speed;
		} else {
			System.out.println("not connected");
		}
	}

	public int getSpeed() {
		return speed;
	}

	public void changeLane(float offset) {
		if (bluetoothDevice.getConnected()) {
			writeCharacteristic.writeValue(Message.setOffsetFromRoadCenter()); // kalibrieren
			writeCharacteristic.writeValue(Message.changeLaneMessage((short) 1000, (short) 1000, offset));
		} else {
			System.out.println("not connected");
		}
	}

	/**
	 * Returns true, if and only if, the vehicle is connected.
	 *
	 * This method now checks the actual Bluetooth connection state
	 * rather than relying solely on notification-based status updates.
	 *
	 * @return true, if vehicle is connected, otherwise false
	 */
	public boolean isConnected() {
		// 检查蓝牙设备是否连接且特性已初始化
		boolean bluetoothConnected = bluetoothDevice != null && bluetoothDevice.getConnected();
		boolean characteristicsReady = writeCharacteristic != null && readCharacteristic != null;

		// 如果蓝牙连接且特性就绪，则认为已连接
		// 这样可以避免依赖可能延迟的通知机制
		return bluetoothConnected && characteristicsReady;
	}


	public boolean isOnCharger() {
		return onCharger;
	}


	/**
	 * A vehicle is ready to start, if it
	 * <ul>
	 *   <li>is connected</li>
	 *   <li>and not on charger</li>
	 *   <li>and write characteristic is set</li>
	 *   <li>and read characteristic is set</li>
	 * </ul>
	 *
	 * @return true, if vehicle ready to start, false otherwise
	 */
	public boolean isReadyToStart() {
		return connected && !onCharger && writeCharacteristic != null && readCharacteristic != null;
	}


	/**
	 * Disconnect the vehicle.
	 * Disconnect is done by first send the ANKI disconnect message and then disconnect
	 * the bluetooth device.
	 *
	 */
	public void disconnect() {
		if (bluetoothDevice.getConnected()) {
			bluetoothDevice.disableConnectedNotifications();
			writeCharacteristic.writeValue(Message.disconnectMessage());
			bluetoothDevice.disconnect();
		}
	}


	/**
	 * Add a {@link NotificationListener}.
	 *
	 * @param listener the listener to add
	 */
	public void addNotificationListener(NotificationListener listener) {
		listeners.add(listener);
	}

	/**
	 * Remove a {@link NotificationListener}.
	 *
	 * @param listener the listener to remove
	 */
	public void removeNotificationListener(NotificationListener listener) {
		listeners.remove(listener);
	}

	public String getMacAddress() {
		return bluetoothDevice.getAddress();
	}

	@Override
	public int hashCode() {
		return bluetoothDevice.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof Vehicle && ((Vehicle) obj).bluetoothDevice.equals(this.bluetoothDevice);
	}

	/**
	 * Method called by BLE system for value notifications.
	 *
	 * @param bytes The BLE message bytes
	 */
	public void onValueNotification(byte[] bytes) {


		try {
			Notification notification = NotificationParser.parse(this, bytes);
            switch (notification) {
                case PositionUpdate pu -> {
                    for (NotificationListener notificationListener : listeners) {
                        if (notificationListener instanceof PositionUpdateListener pul) {
                            pul.onPositionUpdate(pu);
                        }
                    }
                }
                case TransitionUpdate tu -> {
                    for (NotificationListener notificationListener : listeners) {
                        if (notificationListener instanceof TransitionUpdateListener tul) {
                            tul.onTransitionUpdate(tu);
                        }
                    }
                }
				case IntersectionUpdate iu -> {
					for (NotificationListener notificationListener : listeners) {
						if (notificationListener instanceof IntersectionUpdateListener iul) {
							iul.onIntersectionUpdate(iu);
						}
					}
				}
                case ChargerInfoNotification cin -> {
                    for (NotificationListener notificationListener : listeners) {
                        if (notificationListener instanceof ChargerInfoNotificationListener cnl) {
                            cnl.onChargerInfoNotification(cin);
                        }
                    }
                }
                default ->  {}
            }
		} catch (Exception e) {
			// try-catch to prevent swallowing thrown exception by TinyB, which calls this method
			e.printStackTrace();
		}
	}

	/**
	 * Method called by BLE system for connected notifications.
	 *
	 * @param flag the connection value
	 */

	public void onConnectedNotification(boolean flag) {
		try {
			ConnectedNotification cn = new ConnectedNotification(this, flag);
			for (NotificationListener notificationListener : listeners) {
				if (notificationListener instanceof ConnectedNotificationListener) {
					((ConnectedNotificationListener) notificationListener).onConnectedNotification(cn);
				}
			}
		} catch (Exception e) {
			// try-catch to prevent swallowing thrown exception by TinyB, which calls this method
			e.printStackTrace();
		}
	}

	public static String upperCaseChars(String str) {
		StringBuilder sb = new StringBuilder();
		str.codePoints().filter(Character::isUpperCase).forEach(sb::appendCodePoint);
		return sb.toString();
	}




	/**
	 * Class hiding the BLE stuff.
	 * <p>
	 *
	 * Genuine documentation of the Anki C implementation is
	 * <a href="https://anki.github.io/drive-sdk/docs/programming-guide">Ankis Programming Guide</a>.
	 * As locale PDF: <a href="./doc-files/anki-programming-guide.pdf">Ankis Programming Guide</a>
	 * <p>
	 *
	 * TinyB documentation:
	 * <a href="http://iotdk.intel.com/docs/master/tinyb/java/annotated.html">TinyB Class List</a>
	 * <p>
	 *
	 * device.getBluetoothType() gibt Fehler:
	 * https://github.com/intel-iot-devkit/tinyb/issues/69
	 *
	 *
	 * @author bernd
	 *
	 */
	private static class AnkiBle {

		private static final String ANKI_SERVICE_UUID = "BE15BEEF-6186-407E-8381-0BD89C4D8DF4";
		private static final String ANKI_READ_CHARACTERISTIC_UUID = "BE15BEE0-6186-407E-8381-0BD89C4D8DF4";
		private static final String ANKI_WRITE_CHARACTERISTIC_UUID = "BE15BEE1-6186-407E-8381-0BD89C4D8DF4";


		private static void init() {
			System.out.println("Initializing JAnki. Please wait ...");
			Runtime.getRuntime().addShutdownHook(new Thread(AnkiBle::disconnectAll));
			AnkiBle.discoverDevices();
			AnkiBle.initializeDevices();
			System.out.println("Initializing JAnki finished");
		}

		/**
		 * Returns the write characteristics of a hardware vehicle.
		 *
		 * @param device device to get write characteristics for
		 * @return the write characteristics of the device
		 */
		static BluetoothGattCharacteristic writeCharacteristicFor(BluetoothDevice device) {
			return characteristicFor(ANKI_WRITE_CHARACTERISTIC_UUID, device);
		}

		/**
		 * Returns the read characteristics of a hardware vehicle.
		 *
		 * @param device device to get read characteristics for
		 * @return the read characteristics of the device
		 */
		static BluetoothGattCharacteristic readCharacteristicFor(BluetoothDevice device) {
			return characteristicFor(ANKI_READ_CHARACTERISTIC_UUID, device);
		}

		/**
		 * Returns the read or write characteristics of a hardware vehicle.
		 *
		 * @param characteristicUUID UUID of read or write characteristic
		 * @param device the device for which the characteristic is set
		 * @return the characteristic
		 */
		private static BluetoothGattCharacteristic characteristicFor(String characteristicUUID, BluetoothDevice device) {

			BluetoothGattCharacteristic readOrWriteCharacteristic = null;

			// 尝试多次连接和查找特性
			for (int i = 0; i < 10; i++) {

				boolean connected = device.getConnected();
				if (!connected) {
					connected = device.connect();
				}

				if (connected) {
					List<BluetoothGattService> services = device.getServices();

					for (BluetoothGattService service : services) {
						List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

						for (BluetoothGattCharacteristic characteristic : characteristics) {
							if (characteristic.getUUID().toUpperCase().equals(characteristicUUID)) {
								readOrWriteCharacteristic = characteristic;
							}
						}
					}

					if (readOrWriteCharacteristic != null) {
						return readOrWriteCharacteristic;
					} else {
					}
				} else {
				}

				// 在尝试之间等待
				sleep(500);
			}

			if (readOrWriteCharacteristic == null) {
				System.out.println("在10次尝试后仍未找到特性: " + characteristicUUID);
			}

			return readOrWriteCharacteristic;
		}

		private static void sleep(long millis) {
			try {
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Disconnect all Bluetooth devices.
		 *
		 */
		private static void disconnectAll() {
			System.out.println("Disconnecting all devices...");
			Vehicle.vehicles.entrySet().parallelStream().forEach(entry -> entry.getKey().disconnect());
		}

		/**
		 * Discover Anki devices.
		 *
		 */
		public static void discoverDevices() {
			BluetoothManager manager = BluetoothManager.getBluetoothManager();

			try {
				List<BluetoothDevice> list = manager.getDevices();
				for (BluetoothDevice device : list) {
					if (device.getUUIDs().contains(ANKI_SERVICE_UUID.toLowerCase())) {
						if (Vehicle.getAllVehicles().stream().map(v -> v.bluetoothDevice.getAddress()).anyMatch(mac -> mac.equals(device.getAddress()))) {
							Vehicle.vehicles.replace(Vehicle.get(device.getAddress()), System.nanoTime());
						} else {
							Vehicle vehicle = new Vehicle(device);
							Vehicle.vehicles.put(vehicle, System.nanoTime());
							vehicle.bluetoothDevice.enableConnectedNotifications(vehicle::onConnectedNotification);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * Initialize all devices - at least try to.
		 *
		 * <p>
		 * This incldes
		 * <ul>
		 * 	<li> set read characteristic</li>
		 * 	<li> set write characteristicsetzen </li>
		 *  <li> set SDK modesetzen </li>
		 * 	<li> register device for value notifications</li>
		 * </ul>
		 *
		 */
		public static void initializeDevices() {
			try {
				Set<Map.Entry<Vehicle, Long>> vehicles = Vehicle.vehicles.entrySet();
				for (Map.Entry<Vehicle, Long> entry : vehicles) {
					Vehicle vehicle = entry.getKey();
					if (!vehicle.connected) {
						vehicle.bluetoothDevice.connect();
					}
					if (vehicle.writeCharacteristic == null) {
						vehicle.writeCharacteristic = writeCharacteristicFor(vehicle.bluetoothDevice);
						if (vehicle.writeCharacteristic != null) {
							vehicle.writeCharacteristic.writeValue(Message.getSdkMode());
						}
					}
					if (vehicle.readCharacteristic == null) {
						vehicle.readCharacteristic = readCharacteristicFor(vehicle.bluetoothDevice);
						if (vehicle.readCharacteristic != null) {
							vehicle.readCharacteristic.enableValueNotifications(vehicle::onValueNotification);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	private class DefaultConnectedNotificationListener implements ConnectedNotificationListener {

		@Override
		public void onConnectedNotification(ConnectedNotification connectedNotification) {
			Vehicle.this.connected = connectedNotification.isConnected();

		}

	}

	private class DefaultChargerInfoNotificationListener implements ChargerInfoNotificationListener {

		@Override
		public void onChargerInfoNotification(ChargerInfoNotification chargerInfoNotification) {
			Vehicle.this.onCharger = chargerInfoNotification.isOnCharger();
		}

	}

}
