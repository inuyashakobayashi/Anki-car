package de.pdbm.anki.api;

import de.pdbm.janki.RoadPiece;
import java.util.Map;
import java.util.List;

/**
 * Haupt-API-Interface für die Kontrolle von Anki Overdrive Fahrzeugen.
 *
 * Diese Schnittstelle bietet eine vereinfachte, benutzerfreundliche API
 * für die grundlegenden Fahrzeugoperationen und Streckenerfassung.
 *
 *
 */
public interface AnkiController {

    // === Verbindungsmanagement ===

    /**
     * Scannt nach verfügbaren Anki Overdrive Fahrzeugen.
     *
     * @return Liste der MAC-Adressen gefundener Anki-Geräte
     */
    List<String> scanDevices();

    /**
     * Stellt eine Verbindung zu einem spezifischen Fahrzeug her.
     *
     * @param deviceAddress MAC-Adresse des Zielfahrzeugs
     * @return true wenn die Verbindung erfolgreich hergestellt wurde, false andernfalls
     */
    boolean connect(String deviceAddress);

    /**
     * Trennt die Verbindung zum aktuell verbundenen Fahrzeug.
     */
    void disconnect();

    /**
     * Überprüft den aktuellen Verbindungsstatus.
     *
     * @return true wenn ein Fahrzeug verbunden ist, false andernfalls
     */
    boolean isConnected();

    // === Grundlegende Fahrzeugsteuerung ===

    /**
     * Setzt die Geschwindigkeit des Fahrzeugs.
     *
     * @param speed Geschwindigkeitswert (0-1000, wobei 0 = Stopp, 1000 = Maximalgeschwindigkeit)
     */
    void setSpeed(int speed);

    /**
     * Stoppt das Fahrzeug (entspricht setSpeed(0)).
     */
    void stop();

    /**
     * Führt einen Spurwechsel durch.
     *
     * @param offset Spurversatz (-1.0 = ganz links, 0.0 = Mitte, 1.0 = ganz rechts)
     */
    void changeLane(float offset);

    // === Statusabfragen ===

    /**
     * Gibt die aktuelle Geschwindigkeit des Fahrzeugs zurück.
     *
     * @return Aktuelle Geschwindigkeit (0-1000)
     */
    int getSpeed();

    /**
     * Gibt die aktuelle Positions-ID auf der Strecke zurück.
     *
     * @return Aktuelle Positions-ID oder -1 wenn unbekannt
     */
    int getCurrentLocation();

    /**
     * Gibt den Typ des aktuellen Streckensegments zurück.
     *
     * @return Aktueller Streckensegment-Typ oder null wenn unbekannt
     */
    RoadPiece getCurrentRoadPiece();

    /**
     * Überprüft, ob sich das Fahrzeug auf der Ladestation befindet.
     *
     * @return true wenn auf Ladestation, false andernfalls
     */
    boolean isOnCharger();

    // === Streckenkartierung ===

    /**
     * Startet den automatischen Streckenkartierungs-Modus.
     *
     * Das Fahrzeug fährt mit der angegebenen Geschwindigkeit und sammelt
     * Informationen über die Streckensegmente.
     *
     * @param speed Geschwindigkeit während der Kartierung (empfohlen: 300-500)
     * @param listener Callback-Interface für Streckenereignisse
     */
    void startTrackMapping(int speed, TrackMappingListener listener);

    /**
     * Stoppt den Streckenkartierungs-Modus.
     */
    void stopTrackMapping();

    /**
     * Gibt die gesammelte Streckenkarte zurück.
     *
     * @return Map mit Positions-ID als Schlüssel und Streckensegment-Typ als Wert
     */
    Map<Integer, RoadPiece> getTrackMap();

    /**
     * Löscht die aktuell gespeicherte Streckenkarte.
     */
    void clearTrackMap();
}
