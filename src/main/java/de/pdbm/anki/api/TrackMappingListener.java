// 2. TrackMappingListener.java - Callback-Interface für Streckenereignisse
package de.pdbm.anki.api;

import de.pdbm.janki.RoadPiece;

/**
 * Callback-Interface für Ereignisse während der Streckenkartierung.
 *
 * Implementierungen dieses Interfaces erhalten Benachrichtigungen über
 * neu entdeckte Streckensegmente und Positionsupdates während der Fahrt.
 */
public interface TrackMappingListener {

    /**
     * Wird aufgerufen, wenn ein neues Streckensegment entdeckt wird.
     *
     * @param locationId Eindeutige ID der Position auf der Strecke
     * @param roadPieceId Spezifische Anki road piece ID (z.B. 36, 39, 40 für verschiedene STRAIGHT Teile)
     * @param roadPiece Typ des Streckensegments (Gerade, Kurve, Start, etc.)
     */
    void onTrackPieceDiscovered(int locationId, int roadPieceId, RoadPiece roadPiece);

    /**
     * Wird bei jeder Positionsaktualisierung aufgerufen.
     *
     * @param locationId Aktuelle Positions-ID
     * @param ascending true wenn das Fahrzeug in aufsteigender Richtung fährt,
     *                  false für absteigende Richtung
     */
    void onLocationUpdate(int locationId, boolean ascending);
}