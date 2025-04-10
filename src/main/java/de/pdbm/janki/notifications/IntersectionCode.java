package de.pdbm.janki.notifications;

public enum IntersectionCode {

    NONE(0), ENTRY_FIRST(1), EXIT_FIRST(2), ENTRY_SECOND(3), EXIT_SECOND(4), UNKNOWN(5);

    @SuppressWarnings("unused")
    private int id;

    private IntersectionCode(int id) {
        this.id = id;
    }

    public static IntersectionCode valueOf(int id) {
        return switch (id) {
            case 0 -> NONE;
            case 1 -> ENTRY_FIRST;
            case 2 -> EXIT_FIRST;
            case 3 -> ENTRY_SECOND;
            case 4 -> EXIT_SECOND;
            default -> UNKNOWN;
        };
    }
}
