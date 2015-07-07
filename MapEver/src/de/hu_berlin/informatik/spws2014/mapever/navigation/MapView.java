/* Copyright (C) 2014,2015  Björn Stelter, Hagen Sparka
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.util.HashSet;

import de.hu_berlin.informatik.spws2014.ImagePositionLocator.Point2D;
import de.hu_berlin.informatik.spws2014.mapever.MapEverApp;
import de.hu_berlin.informatik.spws2014.mapever.R;
import de.hu_berlin.informatik.spws2014.mapever.largeimageview.LargeImageView;

public class MapView extends LargeImageView {
	
	// ////// KEYS FÜR ZU SPEICHERNDE DATEN IM SAVEDINSTANCESTATE-BUNDLE
	
	// Keys für unakzeptierten Referenzpunkt oder Löschkandidaten
	private static final String SAVEDUNACCEPTEDPOS = "savedUnacceptedRefPointPosition";
	private static final String SAVEDTODELETEPOS = "savedToDeleteRefPointPosition";
	
	// Konstante, die Resource ID der Testkarte angibt
	private static final int TESTMAP_RESOURCE = R.drawable.debug_testmap;
	
	// ////// NAVIGATION ACTIVITY CONTEXT
	
	private Navigation navigation;
	
	
	// ////// MAP VIEW UND DATEN
	
	// Wurde das Bild bereits geladen?
	private boolean isMapLoaded = false;
	
	
	// ////// MARKER FÜR DIE USERPOSITION
	
	// Marker für die Position des Users
	private LocationIcon locationIcon;
	
	
	// ////// BEHANDLUNG VON REFERENZPUNKTEN
	
	// Liste der gesetzten Referenzpunkte
	private HashSet<ReferencePointIcon> refPointIcons = new HashSet<ReferencePointIcon>();
	
	// neu erstellter, aber unbestätigter Referenzpunkt
	private ReferencePointIcon unacceptedRefPointIcon = null;
	
	// Referenzpunkt, der zum Löschen ausgewählt wurde
	private ReferencePointIcon toDeleteRefPointIcon = null;
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// CONSTRUCTORS AND INITIALIZATION
	// ////////////////////////////////////////////////////////////////////////
	
	public MapView(Context context) {
		super(context);
		init();
	}
	
	public MapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public MapView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	/**
	 * Initialisiert das MapView-Objekt (wird vom Konstruktor aufgerufen).
	 */
	private void init() {
		// Speichere Activity Context
		// (Der try-catch-Block ist nur notwendig, damit der Layouteditor von Eclipse nicht meckert... -.-)
		try {
			navigation = (Navigation) getContext();
		}
		catch (Exception e) {
		}
	}
	
	@Override
	protected Parcelable onSaveInstanceState() {
		// LargeImageView gibt uns ein Bundle, in dem z.B. die Pan-Daten stecken. Verwende dieses als Basis.
		Bundle bundle = (Bundle) super.onSaveInstanceState();
		
		// Falls ein neuer Referenzpunkt erstellt werden sollte, speichere seine Position (als Point2D).
		// (Timestamp nicht, der ist sowieso 0, solange der Punkt noch nicht akzeptiert wurde.)
		bundle.putSerializable(SAVEDUNACCEPTEDPOS,
				unacceptedRefPointIcon != null ? unacceptedRefPointIcon.getPosition() : null);
		
		// Falls ein Referenzpunkt gelöscht werden sollte, speichere seine Position (als Point2D).
		bundle.putSerializable(SAVEDTODELETEPOS,
				toDeleteRefPointIcon != null ? toDeleteRefPointIcon.getPosition() : null);
		
		return bundle;
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle bundle = (Bundle) state;
		
		// Falls ein neuer Referenzpunkt erstellt werden sollte, stelle diesen wieder her.
		Point2D unacceptedPos = (Point2D) bundle.getSerializable(SAVEDUNACCEPTEDPOS);
		
		if (unacceptedPos != null) {
			// Referenzpunkt erstellen
			unacceptedRefPointIcon = null;
			createUnacceptedReferencePoint(unacceptedPos);
		}
		
		// Falls ein Referenzpunkt gelöscht werden sollte, stelle die Auswahl wieder her.
		Point2D deleteCandidatePos = (Point2D) bundle.getSerializable(SAVEDTODELETEPOS);
		
		if (deleteCandidatePos != null) {
			// Finde den zu löschenden Referenzpunkt
			for (ReferencePointIcon refPoint : refPointIcons) {
				if (refPoint.getPosition().equals(deleteCandidatePos)) {
					// dieser Punkt war der Löschkandidat
					registerAsDeletionCandidate(refPoint);
					break;
				}
			}
			
			if (toDeleteRefPointIcon == null) {
				Log.w("MapView/onRestoreInstanceState", "Tried to restore delete candidate but didn't find it: " + deleteCandidatePos);
			}
		}
		
		// Im Bundle stecken noch Informationen von LargeImageView, z.B. Pan-Daten. Reiche das Bundle also weiter.
		super.onRestoreInstanceState(bundle);
		
		// Darstellung aktualisieren
		update();
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// USER INPUT HANDLING
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Behandlung von Touchevents.
	 */
	// Lint-Warnung "MapView overrides onTouchEvent but not performClick", obwohl sich onTouchEvent[_clickDetection]()
	// um Aufruf von performClick() kümmern.
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Dadurch, dass wir Klicks separat behandeln, können wir hier problemlos in den meisten Zuständen
		// das Panning und Zooming ausführen. Betrachte also nur Fälle, in denen wir KEIN Panning und Zooming
		// (oder zusätzlich etwas anderes) wollen.
		
		// Sind wir in einem Hilfezustand?
		if (navigation.state.isHelpState()) {
			// Hier kein Pan/Zoom, aber Klicks sollen die Hilfe beenden.
			onTouchEvent_clickDetection(event);
			return true;
		}
		
		// Führe Defaulthandler aus (der Klicks, Pan und Zoom behandelt)
		return super.onTouchEvent(event);
	}
	
	@Override
	public boolean onClickPosition(float clickX, float clickY) {
		// Fallunterscheidung nach aktuellem Zustand
		NavigationStates navState = navigation.state;
		
		if (navState == NavigationStates.MARK_REFPOINT || navState == NavigationStates.ACCEPT_REFPOINT) {
			// ZUSTAND: Referenzpunkt setzen ODER akzeptieren.
			// Klicken bewirkt das Erstellen eines neuen unakzeptierten Referenzpunktes an der geklickten Position
			return onClickPosition_setRefPoint(clickX, clickY);
		}
		else if (navState == NavigationStates.DELETE_REFPOINT) {
			// ZUSTAND: Löschen eines ausgewählten Referenzpunktes
			// Klicken bricht Löschaktion ab.
			navigation.refPointDeleteBack(null);
			return true;
		}
		else if (navState.isHelpState()) {
			// ZUSTAND: Zustand X mit Schnellhilfe
			// Klicken bewirkt Ende der Schnellhilfe
			navigation.endQuickHelp();
			return true;
		}
		
		// Kein Klickevent behandelt
		return false;
	}
	
	/**
	 * Behandlung des "Referenzpunkt setzen"-Modus:
	 * Erstelle einen neuen (unakzeptierten) Referenzpunkt an der angeklickten Stelle.
	 */
	private boolean onClickPosition_setRefPoint(float clickX, float clickY) {
		// Bildposition berechnen, die angeklickt wurde
		PointF imagePos = screenToImagePosition(clickX, clickY);
		
		int xCoord = (int) imagePos.x;
		int yCoord = (int) imagePos.y;
		
		// Erstelle neuen unakzeptierten Referenzpunkt an dieser Stelle
		// (Sanitycheck der Koordinaten passiert dort)
		createUnacceptedReferencePoint(new Point2D(xCoord, yCoord));
		
		// Falls unakzeptierter Referenzpunkt erfolgreich gesetzt wurde, wechsel Zustand
		if (unacceptedRefPointIcon != null) {
			// nächster Zustand: Bestätigen des gesetzten Referenzpunkts
			navigation.changeState(NavigationStates.ACCEPT_REFPOINT);
		}
		
		return true;
	}
	
	@Override
	public void update() {
		// Super-Methode aufrufen (wichtig)
		super.update();
		
		if (isMapLoaded) {
			// Locationmarker aktualisieren
			updateLocationIcon();
		}
	}
	
	@Override
	public void onTouchPanZoomChange() {
		// Panning/Zooming wurde durch Touch-Event verändert.
		
		// aufhören, den Standort zu fokusieren
		if (navigation.isPositionTracked()) {
			navigation.stopTrackingPosition();
		}
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// DARSTELLUNG
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Aktualisiert die Position des LocationIcons.
	 */
	public void updateLocationIcon() {
		if (locationIcon == null)
			return;
		
		if (navigation.isUserPositionKnown()) {
			// Wenn die aktuelle Benutzerposition bekannt ist, zeige Position an.
			// (Extra prüfen, ob es bereits sichtbar ist, bevor wir setVisibility aufrufen, ist etwas unnütz.)
			locationIcon.show();
			
			// Aktuelle Userposition abfragen
			Point2D pos = navigation.getUserPosition();
			
			// Locationmarker updaten
			locationIcon.setPosition(pos);
		}
		else {
			// Locationmarker verstecken, bis wieder neue Koordinaten bekannt sind
			locationIcon.hide();
		}
		
	}
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// LADEN/VERWALTUNG VON BILD UND USERPOSITION
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Lade Bild der Karte in die View.
	 * 
	 * @param mapID ID des Karte, gleichzeitig der Dateiname des Bildes (oder 0 für Testkarte)
	 * @throws FileNotFoundException 
	 */
	public void loadMap(long mapID) throws FileNotFoundException {
		// Karte laden, während bereits Karte geladen ist, macht keinen Sinn.
		if (isMapLoaded)
			return;
		
		// Bild in die View laden mittels LargeImageView-Funktionalität
		if (mapID != 0) {
			// absoluten Pfad der Bilddatei ermitteln
			String filename = MapEverApp.getAbsoluteFilePath(String.valueOf(mapID));
			
			try {
				// Bild der LargeImageView auf diese Datei setzen
				setImageFilename(filename);
			}
			catch (FileNotFoundException e) {
				Log.e("MapView/loadMap", "Konnte InputStream zu " + mapID + " nicht öffnen!");
				e.printStackTrace();
				
				// Exception an Navigation weiterreichen
				throw e;
			}
		}
		
		if (mapID == 0) {
			// Ohne Parameter oder im Fehlerfall wird Testkarte angezeigt
			setImageResource(TESTMAP_RESOURCE);
		}
		
		Log.d("MapView/loadMap", "Loading map #" + mapID + (mapID == 0 ? " [test_karte]" : "")
				+ " (image size " + getImageWidth() + "x" + getImageHeight() + ")");
		
		isMapLoaded = true;
		
		// Erstelle LocationIcon und verstecke es zunächst.
		// (Erst anzeigen, sobald erste Koordinaten von der Lokalisierung eingetroffen sind.)
		locationIcon = new LocationIcon(this);
		locationIcon.hide();
		
		// Darstellung aktualisieren
		update();
	}
	
	/**
	 * Zentriert die Ansicht auf die aktuelle Benutzerposition.
	 */
	public void centerCurrentLocation() {
		// Benutzerposition abfragen
		Point2D location = navigation.getUserPosition();
		
		// Pan-Zentrum auf die Position verschieben (ruft update() auf).
		setPanCenter(location.x, location.y);
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// REFERENZPUNKTE
	// ////////////////////////////////////////////////////////////////////////
	
	// ////// LADEN / VERWALTEN
	
	/**
	 * Gibt die Anzahl aktuell gesetzter Referenzpunkte zurück.
	 */
	public int countReferencePoints() {
		return refPointIcons.size();
	}
	
	/**
	 * Erstellt ein ReferencePointIcon zu einem geladenen Referenzpunkt.
	 * 
	 * @param pos Position des Referenzpunktes als Point2D
	 * @param time Zeitstempel des Punktes
	 */
	public void createLoadedReferencePoint(Point2D pos, long time) {
		// Erstelle Referenzpunkt-Icon (repräsentiert zugleich den Referenzpunkt selbst)
		ReferencePointIcon newRefPointIcon = new ReferencePointIcon(this, pos, time, true);
		
		// Füge Referenzpunkt in Liste ein
		refPointIcons.add(newRefPointIcon);
		
		// Wir registrieren den Punkt NICHT beim LDM, weil wir davon ausgehen, dass wir
		// ihn von dort bekommen haben...
		
		// Darstellung aktualisieren
		update();
	}
	
	
	// ////// ERSTELLEN / AKZEPTIEREN
	
	/**
	 * Erstellt einen neuen unakzeptierten Referenzpunkt an der angegebenen Bildposition.
	 * 
	 * @param pos Position des Referenzpunktes im Bild als Point2D
	 */
	public void createUnacceptedReferencePoint(Point2D pos) {
		// Prüfe die Position auf Sinnhaftigkeit (vermeide Referenzpunkte außerhalb der Bildgrenzen)
		if (pos.x < 0 || pos.y < 0 || pos.x >= getImageWidth() || pos.y >= getImageHeight()) {
			// Fehlermeldung als Toast ausgeben
			Toast.makeText(getContext(),
					getContext().getString(R.string.navigation_toast_refpoint_out_of_boundaries),
					Toast.LENGTH_SHORT).show();
			
			return;
		}
		
		// Falls der Nutzer bereits vorher einen Referenzpunkt gesetzt und noch nicht bestätigt hat...
		if (unacceptedRefPointIcon != null) {
			// -> ... cancelt das Setzen eines neuen Referenzpunkts den alten Punkt.
			cancelReferencePoint();
		}
		
		// Erstelle Referenzpunkt-Icon (repräsentiert zugleich den Referenzpunkt selbst)
		// (Wir übergeben 0 als Zeit, weil der Timestamp erst beim Akzeptieren feststeht.)
		unacceptedRefPointIcon = new ReferencePointIcon(this, pos, 0, false);
		
		// Darstellung aktualisieren
		update();
	}
	
	/**
	 * Der noch unbestätigte Referenzpunkt unacceptedRefPointIcon wurde von der GUI bestätigt.
	 */
	public void acceptReferencePoint() {
		// Nichts tun, falls kein unbestätigter Referenzpunkt vorhanden (sollte nicht passieren)
		if (unacceptedRefPointIcon == null)
			return;
		
		// Aktuellen Timestamp setzen, weil die GPS-Koordinaten jünger als der unakzeptierte Punkt sein könnten.
		unacceptedRefPointIcon.setTimestamp(SystemClock.elapsedRealtime());
		
		// Referenzpunkt bei Lokalisierung eintragen
		boolean registerSuccessful = navigation.registerReferencePoint(unacceptedRefPointIcon.getPosition(),
				unacceptedRefPointIcon.getTimestamp());
		
		if (registerSuccessful) {
			// Füge Referenzpunkt in Liste ein
			refPointIcons.add(unacceptedRefPointIcon);
			
			// Beginne das Fading des RefPunkts
			unacceptedRefPointIcon.fadeOut();
		}
		else {
			// Referenzpunkt konnte nicht registriert werden -> ungültige/unsinnige Koordinaten?
			// Fehlermeldung anzeigen und Referenzpunkt löschen.
			Log.w("MapView/acceptReferencePoint", "addMarker for point " + unacceptedRefPointIcon.getPosition()
					+ " at time " + unacceptedRefPointIcon.getTimestamp() + " returned false");
			Toast.makeText(getContext(), getContext().getString(R.string.navigation_toast_refpoint_already_set_for_this_position), Toast.LENGTH_SHORT).show();
			
			// unakzeptierten Punkt verwerfen
			cancelReferencePoint();
		}
		
		// Referenz auf den Referenzpunkt freigeben
		unacceptedRefPointIcon = null;
	}
	
	/**
	 * Der noch unbestätigte Referenzpunkt soll verworfen werden.
	 */
	public void cancelReferencePoint() {
		// Nichts tun, falls kein unbestätigter Referenzpunkt vorhanden (sollte nicht passieren)
		if (unacceptedRefPointIcon == null)
			return;
		
		// unakzeptierten Referenzpunkt aus der Anzeige löschen
		unacceptedRefPointIcon.detach();
		
		// Referenz auf den Referenzpunkt freigeben (Objekt wird dem überlassen)
		unacceptedRefPointIcon = null;
	}
	
	
	// ////// LÖSCHEN
	
	/**
	 * Wird aufgerufen, wenn ein Referenzpunkt angeklickt wird, und setzt (wenn im richtigen Zustand) diesen Punkt
	 * als Kandidat zum Löschen.
	 */
	public boolean registerAsDeletionCandidate(ReferencePointIcon deletionCandidate) {
		// Prüfe, ob wir im RUNNING-Zustand sind oder bereits einen anderen Löschkandidaten ausgewählt haben.
		if (navigation.state != NavigationStates.RUNNING && navigation.state != NavigationStates.DELETE_REFPOINT) {
			// Wenn nicht, tu nichts! Die aufrufende onClick-Funktion soll das Event als nicht behandelt weitergeben.
			return false;
		}
		
		// der Zustand ist nun "Referenzpunkt löschen"
		navigation.changeState(NavigationStates.DELETE_REFPOINT);
		
		// Wenn bereits einer zum Löschen ausgewählt ist, ändere die Auswahl und lass den alten wieder ausblenden
		if (toDeleteRefPointIcon != null) {
			toDeleteRefPointIcon.fadeOut();
		}
		
		// Setze diesen Referenzpunkt als Löschkandidaten
		toDeleteRefPointIcon = deletionCandidate;
		
		// Blende Referenzpunkt zur Visualisierung ein.
		toDeleteRefPointIcon.fadeIn();
		
		// return true: Löschkandidat wurde ausgewählt (aufrufende onClick-Funktion gibt ebenfalls true zurück)
		return true;
		
	}
	
	/**
	 * Der ausgewählte Referenzpunkt soll gelöscht werden.
	 */
	public void deleteReferencePoint() {
		// Nichts tun, falls kein Löschkandidat vorhanden (sollte nicht passieren)
		if (toDeleteRefPointIcon == null)
			return;
		
		// Referenzpunkt aus der Lokalisierung löschen.
		navigation.unregisterReferencePoint(toDeleteRefPointIcon.getPosition());
		
		// Entferne Referenzpunkt aus der Liste und von der Darstellung
		refPointIcons.remove(toDeleteRefPointIcon);
		toDeleteRefPointIcon.detach();
		
		// Referenz auf den Referenzpunkt freigeben (Objekt wird dem überlassen)
		toDeleteRefPointIcon = null;
	}
	
	/**
	 * Der ausgewählte Referenzpunkt soll nicht gelöscht werden.
	 */
	public void dontDeleteReferencePoint() {
		// Nichts tun, falls kein Löschkandidat vorhanden (sollte nicht passieren)
		if (toDeleteRefPointIcon == null)
			return;
		
		// Der Punkt wird wieder ausgeblendet und toDeleteRefPointView freigegeben
		toDeleteRefPointIcon.fadeOut();
		toDeleteRefPointIcon = null;
	}
	
}
