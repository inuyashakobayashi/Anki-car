package de.pdbm.janki;


/**
 * Enumeration to abstract from arbitrary road piece IDs assigned by Anki.
 *
 * @author bernd
 *
 */
public enum RoadPiece {

	START(33), FINISH(34), STRAIGHT(36, 39, 40, 48, 51), CORNER(17, 18, 20, 23, 24, 27), INTERSECTION(10);
	
	@SuppressWarnings("unused")
	private int[] ids;

	private RoadPiece(int... ids) {
		this.ids = ids;
	}
	
	public static RoadPiece valueOf(int id) {
        return switch (id) {
            case 33 -> START;
            case 34 -> FINISH;
            case 36, 39, 40, 48, 51 -> STRAIGHT;
            case 17, 18, 20, 23, 24, 27 -> CORNER;
            case 10 -> INTERSECTION;
            default -> throw new IllegalArgumentException("unknown road piece id");
        };
    }
	
}
