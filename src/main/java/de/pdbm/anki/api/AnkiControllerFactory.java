package de.pdbm.anki.api;

import de.pdbm.anki.impl.AnkiControllerImpl;

/**
 * Factory-Klasse für die Erstellung von AnkiController-Instanzen.
 *
 * Diese Klasse implementiert das Factory-Pattern und kapselt die Instanziierung
 * der konkreten AnkiController-Implementierung.
 */
public class AnkiControllerFactory {

    /**
     * Erstellt eine neue Instanz des AnkiControllers.
     *
     * @return Eine neue AnkiController-Implementierung
     */
    public static AnkiController create() {
        return new AnkiControllerImpl();
    }
}