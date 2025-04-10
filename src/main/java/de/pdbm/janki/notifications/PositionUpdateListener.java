package de.pdbm.janki.notifications;

public interface PositionUpdateListener extends NotificationListener {
	
	void onPositionUpdate(PositionUpdate positionUpdate);
	
}