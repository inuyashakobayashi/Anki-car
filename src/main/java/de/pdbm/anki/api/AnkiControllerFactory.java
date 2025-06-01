package de.pdbm.anki.api;
import de.pdbm.anki.impl.AnkiControllerImpl;
public class AnkiControllerFactory {
    /**
     * 创建AnkiController实例
     * @return AnkiController实例
     */
    public static AnkiController create() {
        return new AnkiControllerImpl();
    }
}