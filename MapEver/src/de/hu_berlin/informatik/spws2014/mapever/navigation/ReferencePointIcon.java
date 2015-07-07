/* Copyright (C) 2014,2015  Björn Stelter
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hu_berlin.informatik.spws2014.mapever.navigation;

import android.graphics.Rect;

import de.hu_berlin.informatik.spws2014.ImagePositionLocator.Point2D;
import de.hu_berlin.informatik.spws2014.mapever.R;
import de.hu_berlin.informatik.spws2014.mapever.largeimageview.LargeImageView;
import de.hu_berlin.informatik.spws2014.mapever.largeimageview.OverlayIcon;

public class ReferencePointIcon extends OverlayIcon {
	
	// Resource des zu verwendenden Bildes
	private static int refPointImageResource = R.drawable.ref_punkt;
	
	// Dauer des Verblassens und letztendlich zu erreichender Alpha-Wert
	private static float hiddenAlpha = 0.4f;
	private static long fadingTimeOut = 2000;
	private static long fadingTimeIn = 200;
	
	
	// Bildkoordinaten des Referenzpunktes
	private Point2D refPointPosition;
	
	// Zeitpunkt, zu dem der Referenzpunkt erstellt wurde
	private long timeStamp = 0;
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// CONSTRUCTORS
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Erstelle Referenzpunkt an bestimmter Position. Verwende isFadedOut=false für neue Punkte, und true für das Laden
	 * bestehender Punkte. Bitte darauf achten, time nur dann zu setzen, wenn der Punkt akzeptiert wurde (ggf. später
	 * mit setTimestamp()).
	 * 
	 * @param parentMapView die MapView
	 * @param position Bildkoordinaten des Punktes
	 * @param time Zeitpunkt der Erstellung des Punktes
	 * @param isFadedOut Punkt ist bereits transparent
	 */
	public ReferencePointIcon(MapView parentMapView, Point2D position, long time, boolean isFadedOut) {
		// Superkonstruktor, registriert Icon bei der LIV
		super((LargeImageView) parentMapView);
		
		// Appresource als Bild setzen
		setDrawable(parentMapView.getResources().getDrawable(refPointImageResource));
		
		// Setze Position
		setPosition(position);
		setTimestamp(time);
		
		// Soll der Punkt zu Beginn voll sichtbar oder transparent sein?
		if (isFadedOut) {
			// Alpha-Wert setzen um Punkt transparent zu machen.
			// (NICHT setAlpha() verwenden, da die Animationen nicht mit dem dadurch gesetzten Alpha-Wert sondern
			// mit irgendeinem anderen internen Wert arbeiten. Beides wird dann akkumuliert, sodass fadeIn() den Wert
			// nicht auf 1 ändern lässt, sondern auf 1*currentAlpha... Total bekloppt.)
			startFading(hiddenAlpha, hiddenAlpha, 0);
		}
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// OVERLAYICON PROPERTY OVERRIDES
	// ////////////////////////////////////////////////////////////////////////
	
	@Override
	protected int getImagePositionX() {
		return refPointPosition.x;
	}
	
	@Override
	protected int getImagePositionY() {
		return refPointPosition.y;
	}
	
	@Override
	protected int getImageOffsetX() {
		return -getWidth() / 2;
	}
	
	@Override
	protected int getImageOffsetY() {
		return -getHeight() / 2;
	}
	
	@Override
	public Rect getTouchHitbox() {
		// Die Hitbox ist doppelt so groß wie das Bild, damit man die kleinen Referenzpunkte besser anklicken kann.
		// (Ist sinnvoll.)
		return new Rect(
				2 * getImageOffsetX(),
				2 * getImageOffsetY(),
				2 * (getWidth() + getImageOffsetX()),
				2 * (getHeight() + getImageOffsetY()));
	}
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// REFERENCEPOINT PROPERTIES
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Gibt Bildkoordinaten des Referenzpunkts relativ zum Koordinatenursprung der Karte als Point2D zurück.
	 */
	public Point2D getPosition() {
		return refPointPosition;
	}
	
	/**
	 * Setze Bildkoordinaten des Referenzpunktes relativ zum Koordinatenursprung der Karte
	 * 
	 * @param position neue Position
	 */
	public void setPosition(Point2D position) {
		refPointPosition = position;
		
		// Darstellung aktualisieren
		update();
	}
	
	/**
	 * Gibt den Zeitpunkt der Erzeugung (Akzeptanz) des Referenzpunkts zurück.
	 */
	public long getTimestamp() {
		return timeStamp;
	}
	
	/**
	 * Setzt den Zeitpunkt der Erzeugung des Referenzpunkts.
	 */
	public void setTimestamp(long time) {
		timeStamp = time;
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// EVENT HANDLERS
	// ////////////////////////////////////////////////////////////////////////
	
	@Override
	public boolean onClick(float screenX, float screenY) {
		MapView mapView = (MapView) getParentLIV();
		
		// Registriere den zugehörigen Referenzpunkt als potentiellen Löschungskandidaten bei der MapView.
		// Falls dies im aktuellen Zustand nicht möglich ist, gibt die Funktion false zurück. Behandel das Event
		// dann als nicht behandelt, sodass es zur MapView weitergereicht wird.
		return mapView.registerAsDeletionCandidate(this);
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// DARSTELLUNG
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Starte Verblassungsanimation des Referenzpunktes.
	 */
	public void fadeOut() {
		// von komplett sichtbar bis hiddenAlpha
		startFading(1, hiddenAlpha, fadingTimeOut);
	}
	
	/**
	 * Starte Animation, die den Referenzpunkt wieder sichtbar macht.
	 */
	public void fadeIn() {
		// von hiddenAlpha bis komplett sichtbar
		startFading(hiddenAlpha, 1, fadingTimeIn);
	}
	
}
