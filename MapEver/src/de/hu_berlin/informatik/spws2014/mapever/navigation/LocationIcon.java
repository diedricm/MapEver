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

public class LocationIcon extends OverlayIcon {
	
	// Resource des zu verwendenden Bildes
	private static int locationImageResource = R.drawable.current_position;
	
	// Bildkoordinaten der Benutzerposition
	private Point2D locationPosition = new Point2D(0, 0);
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// CONSTRUCTORS
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Erstellt ein Icon für die Anzeige der Benutzerposition.
	 * 
	 * @param parentMapView die MapView
	 */
	public LocationIcon(MapView parentMapView) {
		// Superkonstruktor, registriert Icon bei der LIV
		super((LargeImageView) parentMapView);
		
		// Appresource als Bild setzen
		setDrawable(parentMapView.getResources().getDrawable(locationImageResource));
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// OVERLAYICON PROPERTY OVERRIDES
	// ////////////////////////////////////////////////////////////////////////
	
	@Override
	protected int getImagePositionX() {
		return locationPosition.x;
	}
	
	@Override
	protected int getImagePositionY() {
		return locationPosition.y;
	}
	
	// ImageOffset: das Icon ist ein Punkt, die Position liegt also exakt in der Mitte des Icons
	
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
		// Die LocationView als nicht klickbar markieren (nicht notwendig, aber slightly effizienter).
		return null;
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// REFERENCEPOINT PROPERTIES
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Gibt Bildkoordinaten der Benutzerposition relativ zum Koordinatenursprung der Karte als Point2D zurück.
	 */
	public Point2D getPosition() {
		return locationPosition;
	}
	
	/**
	 * Setze Bildkoordinaten der Benutzerposition relativ zum Koordinatenursprung der Karte.
	 */
	public void setPosition(Point2D position) {
		locationPosition = position;
		
		// Darstellung aktualisieren
		update();
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// EVENT HANDLERS
	// ////////////////////////////////////////////////////////////////////////
	
	// kein onClick
	// TODO vielleicht könnte man hier aber trotzdem was interessantes machen (per Toast die GPS-Koordinaten
	// einblenden...?)
	
	// @Override
	// public boolean onClick(float screenX, float screenY) {
	// // return false: Event wurde nicht behandelt, wird an MapView weitergereicht
	// return false;
	// }
	
}
