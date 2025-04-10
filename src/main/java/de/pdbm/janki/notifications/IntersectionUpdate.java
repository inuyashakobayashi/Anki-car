package de.pdbm.janki.notifications;

import de.pdbm.janki.Vehicle;

public class IntersectionUpdate extends Notification {

    private final IntersectionCode intersectionCode;

    protected IntersectionUpdate(Vehicle vehicle, IntersectionCode intersectionCode, boolean isExisting) {
        super(vehicle);
        this.intersectionCode = intersectionCode;
    }

    @Override
    public String toString() {
        return "Intersection of vehicle " + getVehicle().getMacAddress() +
                " intersectionCode: " + intersectionCode;
    }

    // Getter and Setter

    public IntersectionCode getIntersectionCode() {
        return intersectionCode;
    }
}
