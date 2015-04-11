/* Copyright (C) 2014,2015 Björn Stelter, Jan Müller
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

package de.hu_berlin.informatik.spws2014.mapever.entzerrung;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.Log;

import de.hu_berlin.informatik.spws2014.mapever.R;
import de.hu_berlin.informatik.spws2014.mapever.Settings;
import de.hu_berlin.informatik.spws2014.mapever.largeimageview.LargeImageView;
import de.hu_berlin.informatik.spws2014.mapever.largeimageview.OverlayIcon;

public class CornerIcon extends OverlayIcon {
	
	// Resource des zu verwendenden Bildes
	private static int cornerImageResource = R.drawable.entzerrung_corner;
	
	// Context der Activity
	private Context context;
	
	// Bildkoordinaten des Eckpunktes
	private Point cornerPosition;
	
	// Bildkoordinaten vor einem Drag-Vorgang (zwecks Drag-Abbruch)
	private Point cornerPosition_preDrag = null;
	
	// Offset des Angriffspunkt für Drag-Event (an welcher Stelle des Icons wird es gezogen?)
	private float dragOriginOffsetX = 0;
	private float dragOriginOffsetY = 0;
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// CONSTRUCTORS
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Erstelle Eckpunkt an bestimmter Position.
	 * 
	 * @param parentEView die EntzerrungsView
	 * @param position Bildkoordinaten des Punktes
	 */
	public CornerIcon(EntzerrungsView parentEView, Point position) {
		// Superkonstruktor, registriert Icon bei der LIV
		super((LargeImageView) parentEView);
		
		// Save activity context for later...
		context = parentEView.getContext();
		
		// Appresource als Bild setzen
		setDrawable(parentEView.getResources().getDrawable(cornerImageResource));
		
		// Setze Position
		setPosition(position);
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// OVERLAYICON PROPERTY OVERRIDES
	// ////////////////////////////////////////////////////////////////////////
	
	@Override
	protected int getImagePositionX() {
		return cornerPosition == null ? 0 : cornerPosition.x;
	}
	
	@Override
	protected int getImagePositionY() {
		return cornerPosition == null ? 0 : cornerPosition.y;
	}
	
	@Override
	protected int getImageOffsetX() {
		return -getWidth() / 2;
	}
	
	@Override
	protected int getImageOffsetY() {
		return -getHeight() / 2;
	}
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// PROPERTIES
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Gibt Bildkoordinaten des Eckpunktes als Point zurück.
	 */
	public Point getPosition() {
		return cornerPosition;
	}
	
	/**
	 * Setze Bildkoordinaten des Eckpunktes.
	 * 
	 * @param position neue Position
	 */
	public void setPosition(Point position) {
		cornerPosition = position;
		
		// Koordinaten auf Bildgröße beschränken
		if (cornerPosition.x < 0)
			cornerPosition.x = 0;
		else if (cornerPosition.x >= getParentLIV().getImageWidth())
			cornerPosition.x = getParentLIV().getImageWidth() - 1;
		
		if (cornerPosition.y < 0)
			cornerPosition.y = 0;
		else if (cornerPosition.y >= getParentLIV().getImageHeight())
			cornerPosition.y = getParentLIV().getImageHeight() - 1;
		
		// Darstellung aktualisieren
		update();
	}
	
	/**
	 * Setze Bildkoordinaten des Eckpunktes.
	 * 
	 * @param position neue Position als OpenCV-Point (double-basiert)
	 */
	public void setPosition(org.opencv.core.Point position) {
		setPosition(new Point((int) position.x, (int) position.y));
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// EVENT HANDLERS
	// ////////////////////////////////////////////////////////////////////////
	
	// ////// DRAG AND DROP
	
	@Override
	public boolean onDragDown(int pointerID, float screenX, float screenY) {
		Log.d("CornerIcon/onDragDown", "[" + cornerPosition + "] start drag pointerID " + pointerID + ", screen pos " + screenX + "/" + screenY);
		
		// Falls Multitouch nicht aktiviert ist und bereits ein Icon gedraggt wird, keinen weiteren Drag-Vorgang
		// starten und alle laufenden abbrechen.
		if (!Settings.getPreference_livMultitouch(context) && getParentLIV().isCurrentlyDragging()) {
			getParentLIV().cancelAllDragging();
			return true;
		}
		
		// Bildschirmkoordinaten in Bildkoordinaten umwandeln
		PointF imagePos = getParentLIV().screenToImagePosition(screenX, screenY);
		
		// Merke den Angriffspunkt des Drags als Offset (wenn man den Eckpunkt nicht mittig sondern an der Seite
		// anfässt, dann zieht man den Eckpunkt auch an der Seite, statt dass er automatisch auf die Fingerposition
		// zentriert wird).
		dragOriginOffsetX = imagePos.x - getImagePositionX();
		dragOriginOffsetY = imagePos.y - getImagePositionY();
		
		// Alte Position des Eckpunkts merken, um sie im Falle eines Drag-Abbruchs zurückzusetzen
		cornerPosition_preDrag = cornerPosition;
		
		// Starte Drag-Vorgang (merke Pointer-ID, um onDragMove's zu erhalten)
		startDrag(pointerID);
		
		// Event wurde behandelt.
		return true;
	}
	
	@Override
	public boolean onDragMove(float screenX, float screenY) {
		Log.d("CornerIcon/onDragMove", "[" + cornerPosition + "] dragging (pointer " + getDragPointerID() + ") on screen pos " + screenX + "/" + screenY);
		
		// Bildschirmkoordinaten in Bildkoordinaten umwandeln
		PointF imagePos = getParentLIV().screenToImagePosition(screenX, screenY);
		
		// Neue Position des Eckpunktes setzen
		setPosition(new Point(
				(int) (imagePos.x - dragOriginOffsetX),
				(int) (imagePos.y - dragOriginOffsetY)));
		
		// Eckpunkte sortieren lassen
		((EntzerrungsView) getParentLIV()).sortCorners();
		
		// Event wurde behandelt.
		return true;
	}
	
	@Override
	public void onDragUp(float screenX, float screenY) {
		Log.d("CornerIcon/onDragUp", "[" + cornerPosition + "] stop dragging (pointer " + getDragPointerID() + ") on screen pos " + screenX + "/" + screenY);
		
		// Stoppe Drag-Vorgang (um keine onDragMove's mehr zu erhalten)
		stopDrag();
		
		// Im Falle eines Drag-Abbruchs (screenX = screenY = Float.NaN), Iconposition zurücksetzen
		if (Float.isNaN(screenX) || Float.isNaN(screenY)) {
			setPosition(cornerPosition_preDrag);
			cornerPosition_preDrag = null;
		}
		
		// Angriffspunkt des Drags zurücksetzen
		dragOriginOffsetX = dragOriginOffsetY = 0;
	}
	
}
