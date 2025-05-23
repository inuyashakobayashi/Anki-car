package de.pdbm.janki.notifications;

import de.pdbm.janki.Vehicle;

/**
 * The notification to be used if no convenient real subtype of Notification exists.
 * 
 * @author bernd
 *
 */
public class DefaultNotification extends Notification {
	
	private final byte[] notification;

	public DefaultNotification(Vehicle vehicle, byte[] bytes) {
		super(vehicle);
		this.notification = bytes;
	}

	public byte[] getNotification() {
		return notification;
	}

}
