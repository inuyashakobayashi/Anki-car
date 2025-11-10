package de.pdbm.janki.notifications;

import de.pdbm.janki.RoadPiece;
import de.pdbm.janki.Vehicle;

/**
 * Represents information about a position update of a vehicle.
 * 
 * @author bernd
 *
 */
public final class PositionUpdate extends Notification {

	private final int location;

	private final RoadPiece roadPiece;

	private final int roadPieceId;

	private final boolean ascendingLocations;

	public PositionUpdate(Vehicle vehicle, int location, int roadPieceId, RoadPiece roadPiece, boolean ascendingLocations) {
		super(vehicle);
		this.location = location;
		this.roadPieceId = roadPieceId;
		this.roadPiece = roadPiece;
		this.ascendingLocations = ascendingLocations;
	}
	
	@Override
	public String toString() {
		return "PositionUpdate(" + String.format("%1$2s", "" + location) + ", " + roadPiece + ", " + ascendingLocations + ")";
	}
	

	/**
	 * Returns the location ID.
	 * 
	 * @return location ID
	 * 
	 */
	public int getLocation() {
		return location;
	}

	/**
	 * Returns the road piece type (enum).
	 *
	 * @return road piece type
	 */
	public RoadPiece getRoadPiece() {
		return roadPiece;
	}

	/**
	 * Returns the specific road piece ID (unique identifier).
	 * This is the actual Anki road piece ID (e.g., 36, 39, 40 for different STRAIGHT pieces).
	 *
	 * @return road piece ID
	 */
	public int getRoadPieceId() {
		return roadPieceId;
	}

	/**
	 * Returns true, if and only if, location IDs are passed in ascending order. 
	 * 
	 * @return true, if location IDs are passed in ascending order, false otherwise
	 * 
	 */
	public boolean isAscendingLocations() {
		return ascendingLocations;
	}
	
}
