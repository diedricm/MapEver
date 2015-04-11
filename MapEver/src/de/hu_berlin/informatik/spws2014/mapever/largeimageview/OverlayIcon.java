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

package de.hu_berlin.informatik.spws2014.mapever.largeimageview;

import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public abstract class OverlayIcon {
	
	// LargeImageView, an die das Overlay gebunden ist
	private LargeImageView parentLIV;
	
	// das Bild des Icons als Drawable
	private Drawable drawable;
	
	// ist das Icon gerade sichtbar?
	private boolean visible = true;
	
	// Transparenz: Alpha-Wert
	private int overlayAlpha = 255;
	
	// Drag and Drop: ID des Pointers (Fingers), mit dem das Icon gedraggt wird. -1 wenn nicht gedraggt.
	private int dragPointerID = -1;
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// CONSTRUCTORS AND LAYOUT STUFF
	// ////////////////////////////////////////////////////////////////////////
	
	public OverlayIcon(LargeImageView liv) {
		// Referenz auf die LargeImageView merken, zu der das Icon gehört
		this.parentLIV = liv;
		
		// Icon bei der LIV registrieren
		liv.attachOverlayIcon(this);
	}
	
	
	// ////// LAYOUT STUFF
	
	/**
	 * Gibt die LargeImageView zurück, zu der das Icon gehört.
	 */
	public LargeImageView getParentLIV() {
		return parentLIV;
	}
	
	/**
	 * Deregistriert das Icon von der LargeImageView (löscht es aus der OverlayIconList). Sollte nach dem Löschen eines
	 * Icons aufgerufen werden.
	 */
	public void detach() {
		parentLIV.detachOverlayIcon(this);
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// PROPERTIES
	// ////////////////////////////////////////////////////////////////////////
	
	// ////// DIMENSIONEN
	
	/**
	 * Gibt die Breite des Icons zurück.
	 */
	public int getWidth() {
		return getDrawable() == null ? 0 : getDrawable().getIntrinsicWidth();
	}
	
	/**
	 * Gibt die Höhe des Icons zurück.
	 */
	public int getHeight() {
		return getDrawable() == null ? 0 : getDrawable().getIntrinsicHeight();
	}
	
	
	// ////// POSITION AND OFFSET
	
	/**
	 * X-Koordinate der Bildposition. Muss von Subklassen überschrieben werden, um die Position zu setzen.
	 * 
	 * @return 0, wenn nicht überschrieben.
	 */
	protected int getImagePositionX() {
		return 0;
	}
	
	/**
	 * Y-Koordinate der Bildposition. Muss von Subklassen überschrieben werden, um die Position zu setzen.
	 * 
	 * @return 0, wenn nicht überschrieben.
	 */
	protected int getImagePositionY() {
		return 0;
	}
	
	/**
	 * Bildoffset in X-Richtung. Muss von Subklassen überschrieben werden, wenn ein Offset erwünscht ist.
	 * 
	 * @return 0, wenn nicht überschrieben.
	 */
	protected int getImageOffsetX() {
		return 0;
	}
	
	/**
	 * Bildoffset in Y-Richtung. Muss von Subklassen überschrieben werden, wenn ein Offset erwünscht ist.
	 * 
	 * @return 0, wenn nicht überschrieben.
	 */
	protected int getImageOffsetY() {
		return 0;
	}
	
	/**
	 * Gibt ein Rechteck zurück, das relativ zur Bildposition angibt, welcher Bereich des Icons anklickbar ist.
	 * Default-Implementation gibt die Dimensionen des Bildes verschoben um den ImageOffset zurück.
	 * Kann überschrieben werden und darf null zurückgeben. null wird als "Icon ist nicht klickbar" interpretiert.
	 */
	public Rect getTouchHitbox() {
		return new Rect(
				getImageOffsetX(),
				getImageOffsetY(),
				getWidth() + getImageOffsetX(),
				getHeight() + getImageOffsetY());
	}
	
	/**
	 * Gibt mittels LargeImageView.imageToScreenPosition() die momentane Bildschirmposition (statt Bildposition) des
	 * Icons zurück.
	 */
	public PointF getScreenPosition() {
		return parentLIV.imageToScreenPosition(getImagePositionX(), getImagePositionY());
	}
	
	// ////// ICON DRAWABLE
	
	/**
	 * Setze das Iconbild in Form eines Drawables.
	 */
	public void setDrawable(Drawable _drawable) {
		drawable = _drawable;
		
		// Boundaries/Bildgröße setzen
		drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
	}
	
	/**
	 * Gibt das Iconbild-Drawable zurück.
	 */
	public Drawable getDrawable() {
		return drawable;
	}
	
	
	// ////// APPEARANCE
	
	/**
	 * True, wenn Icon gerade angezeigt wird (siehe {@link #hide()} und {@link #show()}).
	 */
	public boolean isVisible() {
		return visible;
	}
	
	/**
	 * Setzt Sichtbarkeit des Icons.
	 */
	public void setVisibility(boolean vis) {
		visible = vis;
		update();
	}
	
	/**
	 * Versteckt das Icon. Kann mit {@link #show()} wieder angezeigt werden.
	 */
	public void hide() {
		setVisibility(false);
	}
	
	/**
	 * Zeigt ein vorher mit {@link #hide()} verstecktes Icon wieder an.
	 */
	public void show() {
		setVisibility(true);
	}
	
	/**
	 * Setze Transparenz des Icons.
	 * 
	 * @param newAlpha Wert von 0 (vollkommen transparent) bis 255 (undurchsichtig).
	 */
	public void setOverlayAlpha(int newAlpha) {
		overlayAlpha = newAlpha;
		
		// Darstellung aktualisieren
		update();
	}
	
	/**
	 * Gibt Transparenz des Icons zurück.
	 * 
	 * @return Wert von 0 (vollkommen transparent) bis 255 (undurchsichtig).
	 */
	public int getOverlayAlpha() {
		return overlayAlpha;
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// EVENT HANDLERS
	// ////////////////////////////////////////////////////////////////////////
	
	// ////// CLICKS
	
	/**
	 * Führt einen Klick auf das Icon aus. Macht normalerweise nichts, kann überschrieben werden. Wurde das Event
	 * behandelt muss false zurückgegeben werden, damit keine andere View das Event erhält.
	 * 
	 * @param screenX X-Koordinate des Klicks relativ zum Bildschirm
	 * @param screenY Y-Koordinate des Klicks relativ zum Bildschirm
	 * @return Immer false, falls nicht überschrieben.
	 */
	public boolean onClick(float screenX, float screenY) {
		// return false: Event wurde nicht behandelt.
		return false;
	}
	
	
	// ////// DRAG AND DROP
	
	/**
	 * Markiere Icon als "wird jetzt mit Pointer pointerID gedraggt". Muss von onDragDown() aufgerufen werden, damit
	 * onDragMove() getriggert wird. getDragPointerID() gibt dann (bis stopDrag()) pointerID zurück.
	 * 
	 * @param pointerID Pointer-ID von 0 bis n
	 */
	protected void startDrag(int pointerID) {
		dragPointerID = pointerID;
	}
	
	/**
	 * Beende Drag-Aktion. Muss von onDragUp() aufgerufen werden, damit onDragMove() nicht mehr getriggert wird.
	 */
	protected void stopDrag() {
		dragPointerID = -1;
	}
	
	/**
	 * Wenn das Icon gerade gedraggt wird, dann gibt diese Funktion die PointerID des draggenden Fingers zurück.
	 * Ansonsten -1, wenn das Icon nicht gedraggt wird.
	 */
	public int getDragPointerID() {
		return dragPointerID;
	}
	
	/**
	 * Wird von LargeImageView.onTouchEvent_dragAndDrop() getriggert, wenn ein Finger das Icon berührt.
	 * Soll kein Drag and Drop implementiert werden, reicht die Standard-Implementierung, die false zurückgibt.
	 * Damit nun auch onDragMove() getriggert wird, muss startDrag(pointerID) aufgerufen werden.
	 * Wurde das Event behandelt, sollte true zurückgegeben werden, damit das Event nicht mehr von anderen Objekten
	 * behandelt wird (und z.B. das Panning auslöst).
	 * 
	 * @param pointerID ID des Pointers (Fingers), der das Icon berührt
	 * @param screenX X-Koordinate des Klicks relativ zum Bildschirm
	 * @param screenY Y-Koordinate des Klicks relativ zum Bildschirm
	 * @return Immer false, falls nicht überschrieben.
	 */
	public boolean onDragDown(int pointerID, float screenX, float screenY) {
		// return false: Event wurde nicht behandelt.
		return false;
	}
	
	/**
	 * Wird von LargeImageView.onTouchEvent_dragAndDrop() getriggert, wenn getDragPointerID() einen Pointer > -1
	 * zurückgibt und eben dieser Pointer für ein ACTION_MOVE gesorgt hat.
	 * Wurde das Event behandelt, sollte true zurückgegeben werden, damit das Event nicht mehr von anderen Objekten
	 * behandelt wird (und z.B. das Panning auslöst).
	 * 
	 * @param screenX X-Koordinate des Klicks relativ zum Bildschirm
	 * @param screenY Y-Koordinate des Klicks relativ zum Bildschirm
	 * @return Immer false, falls nicht überschrieben.
	 */
	public boolean onDragMove(float screenX, float screenY) {
		// return false: Event wurde nicht behandelt.
		return false;
	}
	
	/**
	 * Wird von LargeImageView.onTouchEvent_dragAndDrop() getriggert, wenn getDragPointerID() einen Pointer > -1
	 * zurückgibt und eben dieser Pointer für ein ACTION_(POINTER_)UP gesorgt hat.
	 * Muss stopDrag() aufrufen, damit keine weiteren onDragMove()s mehr getriggert werden!
	 * Werden Float.NaN als Parameter übergeben, gilt der Dragvorgang als "abgebrochen" (Handler kann sich z.B. um
	 * das Zurücksetzen einer Drag-Verschiebung kümmern.)
	 * 
	 * @param screenX X-Koordinate des Klicks relativ zum Bildschirm
	 * @param screenY Y-Koordinate des Klicks relativ zum Bildschirm
	 * @return Immer false, falls nicht überschrieben.
	 */
	public void onDragUp(float screenX, float screenY) {
		// (Ein Rückgabewert macht hier wenig Sinn. Wenn das Icon gedraggt wurde und der Dragvorgang beendet werden
		// soll, MUSS dieses Event behandelt werden. Andernfalls wird es gar nicht getriggert.)
		stopDrag();
		return;
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// DARSTELLUNG
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Zeichnet das Drawable (mit Alpha-Wert, und nur falls nicht hidden) auf einem (vorher translatierten) Canvas.
	 */
	public void draw(Canvas canvas) {
		// Canvas wurde schon entsprechend verschoben, sodass (0,0) nun der Iconposition (inkl. Offset) entspricht
		
		if (getDrawable() != null && visible) {
			// Transparenz anwenden und Drawable zeichnen
			getDrawable().setAlpha(overlayAlpha);
			getDrawable().draw(canvas);
		}
	}
	
	/**
	 * Aktualisiert die Darstellung des Icons (ruft invalidate() auf).
	 * Sollte immer aufgerufen werden, wenn z.B. die Position oder die Transparenz verändert wurde.
	 */
	public void update() {
		parentLIV.invalidate();
	}
	
	
	// ////// ANIMATIONS
	
	/**
	 * Starte Fade-Animation.
	 * 
	 * @param from Start-Alphawert
	 * @param to End-Alphawert
	 * @param duration Dauer der Animation
	 */
	protected void startFading(float from, float to, long duration) {
		// Erstelle Animation... selbsterklärende Parameter
		// Animation fadeAnim = new AlphaAnimation(from, to);
		// fadeAnim.setInterpolator(new AccelerateInterpolator());
		// fadeAnim.setDuration(duration);
		// fadeAnim.setFillEnabled(true);
		// fadeAnim.setFillAfter(true);
		//
		// this.startAnimation(fadeAnim);
		
		// TODO Animationen reimplementieren!
		
		setOverlayAlpha((int) (to * 255));
	}
	
}
