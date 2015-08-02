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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import de.hu_berlin.informatik.spws2014.ImagePositionLocator.TriangleImagePositionLocator;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.GpsPoint;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.ILDMIOHandler;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.ImagePositionLocator;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.IPLSettingsContainer;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.LDMIOEmpty;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.LeastSquaresImagePositionLocator;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.LocationDataManager;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.Marker;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.NoGpsDataAvailableException;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.Point2D;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.PointNotInImageBoundsException;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.TrackDB;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.TrackDBEntry;
import de.hu_berlin.informatik.spws2014.mapever.BaseActivity;
import de.hu_berlin.informatik.spws2014.mapever.MapEverApp;
import de.hu_berlin.informatik.spws2014.mapever.R;
import de.hu_berlin.informatik.spws2014.mapever.Start;
import de.hu_berlin.informatik.spws2014.mapever.Thumbnail;

public class Navigation extends BaseActivity implements LocationListener {
	
	// ////// KEYS F�R ZU SPEICHERNDE DATEN IM SAVEDINSTANCESTATE-BUNDLE
	
	// Key f�r ID der geladenen Karte (long)
	private static final String SAVEDCURRENTMAPID = "savedCurrentMapID";
	
	// Key f�r UserPosition (Point2D)
	private static final String SAVEDUSERPOS = "savedUserPosition";
	
	// Key f�r den Zustand der Navigation (NavigationState)
	private static final String SAVEDSTATE = "savedState";
	
	// Key f�r den Zustand der Hilfe (boolean)
	private static final String SAVEDHELPSTATE = "savedHelpState";
	
	// Key f�r den Zustand der Positionszentrierungsfunktion (boolean)
	private static final String SAVEDTRACKPOSITION = "savedTrackPosition";
	
	
	// ////// STATIC VARIABLES AND CONSTANTS
	
	/**
	 * INTENT_LOADMAPID: ID der zu ladenden Map als long
	 */
	public static final String INTENT_LOADMAPID = "de.hu_berlin.informatik.spws2014.mapever.navigation.Navigation.LoadMapID";
	public static final String INTENT_POS = "de.hu_berlin.informatik.spws2014.mapever.navigation.Navigation.IntentPos";
	
	
	// ////// VIEWS
	
	// unsere Karte
	private MapView mapView;
	
	// der Button zum Setzen des Referenzpunkts
	private ImageButton setRefPointButton;
	// der Button zum Setzen des Referenzpunkts (Akzeptieren)
	private ImageButton acceptRefPointButton;
	// der Button zum Setzen des Referenzpunkts (Abbrechen)
	private ImageButton cancelRefPointButton;
	// der Button zum L�schen eines Referenzpunkts
	private ImageButton deleteRefPointButton;
	// Button um Position zu verfolgen
	private ImageButton trackPositionButton;
	
	// Liste aller ImageButtons
	private ArrayList<ImageButton> imageButtonList = new ArrayList<ImageButton>();
	
	
	// ////// KARTEN- UND NAVIGATIONSINFORMATIONEN
	
	// ID der aktuell geladenen Karte (zugleich Dateiname des Kartenbildes)
	private long currentMapID;
	
	// Position des Benutzers auf der Karte in Pixeln
	private Point2D userPosition = null;
	private double[] intentPos = null;
	
	// Soll die aktuelle Position verfolgt (= zentriert) werden?
	private boolean trackPosition = false;
	
	
	// ////// LOKALISIERUNG
	
	// GPS-Lokalisierung
	private LocationManager locationManager;
	
	// Lokalisierungsalgorithmus
	private LocationDataManager locationDataManager;
	
	// LocationDataManagerListener, der das Eintreffen neuer Positionen handled
	private LocationDataManagerListener locDatManListener;
	
	// Debug-GPS-Mocker
	private Toast mockStatusToast = null;
	private Location mockBaseLocation = null;
	
	// Umbenennen der Karte
	private String newMapName = "";
	
	// TODO bitte das hier drunter alles mal oben einsortieren
	
	// soll der Zustand gespeichert werden?
	private boolean saveState = true;
	
	// der aktuelle Zustand der Navigation, der Anfangszustand ist RUNNING
	public NavigationStates state = NavigationStates.RUNNING;
	
	// "SuperLayout", in welches die anderen Layouts eingebunden werden, erm�glicht einfache Umsetzung von Overlays
	private FrameLayout layoutFrame;
	
	// XXX besser L�sen per Zustands�bergang
	private boolean quickTutorial = false;
	
	// unser Men�
	private Menu menu;
	
	// die Anbindung an die Datenbank
	private ILDMIOHandler iLDMIOHandler;
	TrackDBEntry thisMap;
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// ACTIVITY LIFECYCLE UND INITIALISIERUNG
	// ////////////////////////////////////////////////////////////////////////
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Wenn die Activity in Folge einer Rotation neu erzeugt wird, gibt es einen gespeicherten Zustand in
		// savedInstanceState, sonst normal starten.
		Log.d("Navigation", "onCreate..." + (savedInstanceState != null ? " with savedInstanceState" : ""));
		
		// ////// LAYOUT UND VIEWS
		
		// Layout aufbauen
		layoutFrame = new FrameLayout(getBaseContext());
		setContentView(layoutFrame);
		getLayoutInflater().inflate(R.layout.activity_navigation, layoutFrame);
		
		// Initialisieren der ben�tigten Komponenten
		mapView = (MapView) findViewById(R.id.map);
		mapView.setFocusable(true);
		mapView.setFocusableInTouchMode(true);
		
		// Initialisieren der Buttons & Eintragen in die Liste
		setRefPointButton = (ImageButton) findViewById(R.id.set_refpoint);
		imageButtonList.add(setRefPointButton);
		
		acceptRefPointButton = (ImageButton) findViewById(R.id.accept_refpoint);
		imageButtonList.add(acceptRefPointButton);
		
		cancelRefPointButton = (ImageButton) findViewById(R.id.cancel_refpoint);
		imageButtonList.add(cancelRefPointButton);
		
		deleteRefPointButton = (ImageButton) findViewById(R.id.delete_refpoint);
		imageButtonList.add(deleteRefPointButton);
		
		trackPositionButton = (ImageButton) findViewById(R.id.track_position);
		// Nicht den trackPositionButton in der Liste speichern, da dieser unabh�ngig vom Zustand gezeigt werden soll
		// imageButtonList.add(trackPositionButton);
		
		// Im Zweifelsfall kennen wir den Kartennamen nicht (sonst wird der aus der Datenbank geladen)
		setTitle(getString(R.string.navigation_const_name_of_unnamed_maps));
		
		// ////// PARAMETER ERMITTELN UND ZUSTANDSVARIABLEN INITIALISIEREN (GGF. AUS STATE LADEN)
		
		intentPos = null;
		if (savedInstanceState == null) {
			// -- Frischer Start --
			
			Intent intent = getIntent();
			
			// ID der Karte (= Dateiname des Bildes)
			currentMapID = intent.getExtras().getLong(INTENT_LOADMAPID);
			
			// Initialisiere Startzustand der Navigation
			changeState(NavigationStates.RUNNING);
			saveState = true;
			
			// aktuelle Position des Nutzers auf der Karte, null: noch nicht bekannt
			userPosition = null;
			
			intentPos = intent.getDoubleArrayExtra(INTENT_POS);
		}
		else {
			// -- Gespeicherter Zustand --
			
			// ID der Karte (= Dateiname des Bildes)
			currentMapID = savedInstanceState.getLong(SAVEDCURRENTMAPID);
			
			// aktuelle Position des Nutzers auf der Karte
			userPosition = (Point2D) savedInstanceState.getSerializable(SAVEDUSERPOS);
			
			// ist das Kurztutorial aktiviert?
			quickTutorial = savedInstanceState.getBoolean(SAVEDHELPSTATE);
			
			// ist die Positionszentrierung aktiviert?
			trackPosition = savedInstanceState.getBoolean(SAVEDTRACKPOSITION);
			
			// Wiederherstellung des Zustands
			NavigationStates restoredState = NavigationStates.values()[savedInstanceState.getInt(SAVEDSTATE)];
			
			if (restoredState.isHelpState()) {
				// Sonderbehandlung beim Drehen im Bildschirm der Schnellhilfe
				// XXX besser machen
				state = restoredState;
				getLayoutInflater().inflate(R.layout.navigation_help_running, layoutFrame);
				endQuickHelp();
				startQuickHelp();
				
			}
			else {
				changeState(restoredState);
			}
		}
		
		// ////// KARTE LADEN UND KOMPONENTEN INITIALISIEREN
		
		// Lade Karte und erstelle gegebenenfalls einen neuen Eintrag
		Log.d("onCreate", "Loading map: " + currentMapID + (currentMapID == -1 ? " (new map!)" : ""));
		initLoadMap();
		
		// Initialisiere GPS-Modul
		initGPSModule();
		
		// Initialiales update(), damit alles korrekt dargestellt wird
		mapView.update();
		
		if (intentPos != null) {
			boolean prev = setSpeedFiltering(false);
			locationDataManager.addPoint(new GpsPoint(intentPos[0], intentPos[1], SystemClock.elapsedRealtime()));
			setSpeedFiltering(prev);

			// Change mode to set ref point
			changeState(NavigationStates.MARK_REFPOINT);
		}
		// Aktuelle Position zentrieren, falls tracking aktiviert
		if (trackPosition) {
			trackPosition(trackPositionButton);
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d("Navigation", "onDestroy...");
		
		// Stelle NOCH MAL sicher, dass LDM-IO-Handler letzte Daten geschrieben hat.
		// Passiert eigentlich schon in onResume(), aber um auf Nummer sicher zu gehen...
		// TODO hier vielleicht mit resetIOHandler(null)? @diedricm ?
		if (locationDataManager != null) {
			iLDMIOHandler.save();
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		Log.d("Navigation", "onStart...");
		
		//Prompt user to activate GPS
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			DialogInterface.OnClickListener gpsPromptListener = new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
			        if (which == DialogInterface.BUTTON_POSITIVE) {
			        	Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
						startActivity(intent);
			        }
			        dialog.dismiss();
			    }
			};

			AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
			alertBuilder.setMessage(R.string.navigation_gps_activation_popup_question)
				.setPositiveButton(android.R.string.yes, gpsPromptListener)
			    .setNegativeButton(android.R.string.no, gpsPromptListener)
			    .show();
		}
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		Log.d("Navigation", "onStop...");
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Log.d("Navigation", "onResume...");
		
		// Abonniere GPS Updates
		// TODO Genauigkeit (Parameter 2, 3)? default aus Tutorial (400, 1)
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200, (float) 0.2, this);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		Log.d("Navigation", "onPause...");
		
		// Deabonniere GPS Updates
		locationManager.removeUpdates(this);
		
		// Stelle sicher, dass LDM-IO-Handler letzte Daten geschrieben hat
		if (locationDataManager != null) {
			Log.d("onPause", "Schreibe letzte LDM-IO-Daten...");
			iLDMIOHandler.save();
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		Log.d("Navigation", "onSaveInstanceState...");
		
		if (saveState) {
			// ////// SAVE THE CURRENT STATE
			
			// ID der Karte (= Dateiname des Bildes)
			savedInstanceState.putLong(SAVEDCURRENTMAPID, currentMapID);
			
			// aktuelle Position des Nutzers auf der Karte als Point2D
			savedInstanceState.putSerializable(SAVEDUSERPOS, userPosition);
			
			// Zustand der Navigation speichern
			savedInstanceState.putInt(SAVEDSTATE, state.ordinal());
			
			// Zustand der Hilfe speichern
			savedInstanceState.putBoolean(SAVEDHELPSTATE, quickTutorial);
			
			// Zustand der Hilfe speichern
			savedInstanceState.putBoolean(SAVEDTRACKPOSITION, trackPosition);
			
			// neu erstellt werden:
			// LocationManager
			// LocationDataManager
			// LocationDataManagerListener
			// LocationDataManagerListenerExpertenWohnungsvermittlungFachangestellter
		}
		
		// Always call the superclass so it can save the view hierarchy state
		super.onSaveInstanceState(savedInstanceState);
	}
	
	/**
	 * Beim Bet�tigen der Zur�cktaste gelangen wir wieder zum Startbildschirm.
	 */
	@Override
	public void onBackPressed() {
		// Mit der Zur�ck-Taste kann das L�schen von Referenzpunkten abgebrochen werden
		if (state == NavigationStates.DELETE_REFPOINT) {
			refPointDeleteBack(null);
			return;
		}
		
		// ... genauso wie das Setzen von Referenzpunkten
		if (state == NavigationStates.MARK_REFPOINT || state == NavigationStates.ACCEPT_REFPOINT) {
			cancelReferencePoint(null);
			return;
		}
		
		// ... und die Schnellhilfe
		if (state.isHelpState()) {
			
			endQuickHelp();
			return;
		}
		
		// Wenn wir zur�ckgehen, muss der Zustand nicht gespeichert werden
		saveState = false;
		
		// Startbildschirm aufrufen und Activity finishen
		Intent intent = new Intent(getApplicationContext(), Start.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		if (intentPos != null) {
			intentPos = null;
			intent.putExtra(Start.INTENT_EXIT, true);
		}
		startActivity(intent);
		finish();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.navigation, menu);
		
		this.menu = menu;
		
		// Aktiviere Debug-Optionen, falls Debugmode aktiviert
		if (MapEverApp.isDebugModeEnabled(this)) {
			menu.findItem(R.id.action_debugmode_mockgps).setVisible(true);
		}

		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		
			case R.id.action_rename_map:
				changeState(NavigationStates.RENAME_MAP);
				return true;
				
			case R.id.action_quick_help:
				// Schnellhilfe-Button
				startQuickHelp();
				return true;
				
			case R.id.action_debugmode_mockgps:
				// DEBUGMODE: Mock GPS coordinates
				debug_mockGPS();
				
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	/**
	 * Gibt eine Referenz auf unser MapView zur�ck.
	 */
	public MapView getMapView() {
		return mapView;
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// INITIALISIERUNG VON KARTE UND GPS-MODUL
	// ////////////////////////////////////////////////////////////////////////
	
	// //////// KARTE LADEN / NEUE KARTE ERSTELLEN
	
	static final int LOAD_TEST_MAP = 0;
	static final int CREATE_NEW_MAP = -1;
	
	private void initLoadMap() {
		// ////// LDM-IO-HANDLER INITIALISIEREN
		
		Log.i("Nav" ,"currentMapID:" + currentMapID);
		
		if (currentMapID == LOAD_TEST_MAP) {
			// Spezialfall 0: Lade Testkarte
			// => Lade nichts aus der Datenbank, sondern benutze nichtpersistenten LDM. (Debugging)
			iLDMIOHandler = new LDMIOEmpty();
		} else {
			if (!TrackDB.loadDB(new File(MapEverApp.getAbsoluteFilePath("")))) {
				Log.e("Nav", "Could not load DB");
				finish();
			}
			
			// Haben wir eine neue Karte erstellt?
			if (currentMapID != CREATE_NEW_MAP) {
				// Lade Karte mit der gegebenen ID aus der Datenbank
				// Falls ID = -1, wird ein neuer Eintrag f�r eine neue Karte erstellt (ID per auto increment)
				// pr�fen, ob Karte mit der ID existiert, sonst Fehlermeldung
				thisMap = TrackDB.main.getMap(currentMapID);
				currentMapID = thisMap.getIdentifier();
			} else {
				// ID der neuen Karte anfragen und merken
				thisMap = TrackDB.main.createMap();
				currentMapID = thisMap.getIdentifier();
				
				Log.d("Navigation/initLoadMap", "Neu erstellte Karte mit ID: " + thisMap.getIdentifier());
				
				String targetFilename = MapEverApp.getAbsoluteFilePath(String.valueOf(thisMap.getIdentifier()));
				String targetFilenameThumb = targetFilename + MapEverApp.THUMB_EXT;
				
				// Bilddatei umbenennen
				File renameFrom = new File(MapEverApp.getAbsoluteFilePath(MapEverApp.TEMP_IMAGE_FILENAME));
				File renameTo = new File(targetFilename);
				renameFrom.renameTo(renameTo);
				
				// Thumbnail erstellen
				try {
					int thumbSize = Start.getThumbnailSize();
					Thumbnail.generate(targetFilename, targetFilenameThumb, thumbSize, thumbSize);
				} catch (IOException e) {
					Log.e("Navigation/initLoadMap", "Failed generating thumbnail for image '" + targetFilename + "'!");
					e.printStackTrace();
				}
			}
			
			try {
				iLDMIOHandler = TrackDB.main.getLDMIO(thisMap);
			} catch (IOException e) {
				// ResourceID der Fehlermeldung an den Startbildschirm geben, dieser zeigt Fehlermeldung an
				setResult(R.string.navigation_map_not_found);
				finish();
				return;
			}
			
			// wenn ein sinnvoller name vorhanden ist -> diesen anzeigen, sonst default Wert
			if (thisMap.getMapname().isEmpty())
				setTitle(R.string.navigation_const_name_of_unnamed_maps);
			else
				setTitle(thisMap.getMapname());
		}
		
		// ////// BILD IN DIE MAPVIEW LADEN
		
		// (bei currentMapID == 0 wird die Testkarte geladen)
		try {
			mapView.loadMap(currentMapID);
		} catch (FileNotFoundException e) {
			Log.e("Navigation/initLoadMap", "Konnte Karte " + currentMapID + " nicht laden!");

			// ResourceID der Fehlermeldung an den Startbildschirm geben, dieser zeigt Fehlermeldung an
			setResult(R.string.navigation_image_not_found);
			finish();
			return;
		}
		
		// Bilddimensionen m�ssen erkannt worden sein
		if (mapView.getImageWidth() == 0 || mapView.getImageHeight() == 0) {
			Log.e("Navigation/initLoadMap", "Bilddimensionen sind " + mapView.getImageWidth() + "x" + mapView.getImageHeight());
			
			// (Sollte eigentlich eh nie vorkommen, also gib "Error" aus...)
			setResult(R.string.general_error_title);
			finish();
			return;
		}
		
		// ////// LOCATIONDATAMANAGER INITIALISIEREN
		
		// Listener f�r neue Userkoordinaten erstellen
		locDatManListener = new LocationDataManagerListener(this);
		
		// LocationDataManager initialisieren
		Point2D imageSize = new Point2D(mapView.getImageWidth(), mapView.getImageHeight());
		ImagePositionLocator locator;
		if (de.hu_berlin.informatik.spws2014.mapever.Settings.getPreference_leastsquares(this)) {
			locator = new LeastSquaresImagePositionLocator();
		} else {
			locator = new TriangleImagePositionLocator(imageSize, IPLSettingsContainer.DefaultContainer);
		}
		locationDataManager = new LocationDataManager(locDatManListener, iLDMIOHandler,
				imageSize,
				locator);
		locationDataManager.refreshLastPosition();
		
		// ////// GELADENE REFERENZPUNKTE DARSTELLEN
		
		ArrayList<Marker> loadedMarkers = iLDMIOHandler.getAllMarkers();
		
		// TODO Check for corrupted maps! (LDM returns lots of nulls)
		
		if (loadedMarkers != null) {
			Log.d("Navigation/initLoadMap", "Lade " + loadedMarkers.size() + " Referenzpunkte...");
			
			// Erstelle alle geladenen Referenzpunkte
			for (Marker marker : loadedMarkers) {
				mapView.createLoadedReferencePoint(marker.imgpoint, marker.time);
			}
		}
	}
	
	
	// //////// GPS-MODUL INITIALISIEREN
	
	private void initGPSModule() {
		
		// Initialisiere GPS-Modul
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200, (float) 0.2, this);
		Log.d("initGPSModule", "Location provider: " + LocationManager.GPS_PROVIDER);
		
		// Wir machen nichts mit der lastKnownLocation, siehe #169
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// HANDLING VON USERINPUT
	// ////////////////////////////////////////////////////////////////////////
	
	// //////// HILFE
	
	/**
	 * Startet die Schnellhilfe, wenn nicht bereits aktiv.
	 */
	public void startQuickHelp() {
		// Bis RUNNING wieder erreicht ist, wird der Hilfebildschirm immer angezeigt
		quickTutorial = true;
		
		// Ansonsten zeigen wir abh�ngig vom aktuellen Zustand einen passendes Hilfs-Overlay an
		// und deaktivieren die entsprechenden Buttons
		switch (state) {
			case ACCEPT_REFPOINT:
				getLayoutInflater().inflate(R.layout.navigation_help_accept_refpoint, layoutFrame);
				changeState(NavigationStates.HELP_ACCEPT_REFPOINT);
				break;
			case DELETE_REFPOINT:
				getLayoutInflater().inflate(R.layout.navigation_help_delete_refpoint, layoutFrame);
				changeState(NavigationStates.HELP_DELETE_REFPOINT);
				break;
			case MARK_REFPOINT:
				getLayoutInflater().inflate(R.layout.navigation_help_mark_refpoint, layoutFrame);
				changeState(NavigationStates.HELP_MARK_REFPOINT);
				break;
			case RUNNING:
				getLayoutInflater().inflate(R.layout.navigation_help_running, layoutFrame);
				changeState(NavigationStates.HELP_RUNNING);
				break;
			
			// Wenn wir in der Schnellhilfe sind, beenden wir sie jetzt
			case HELP_ACCEPT_REFPOINT:
			case HELP_DELETE_REFPOINT:
			case HELP_MARK_REFPOINT:
			case HELP_RUNNING:
				endQuickHelp();
				break;
			default:
				Log.e("startQuickHelp", "Schnellhilfe f�r diesen Zustand fehlt noch!");
				return;
				
		}
		
	}
	
	/**
	 * Beendet die Schnellhilfe und stellt den vorherigen Zustand wieder her.
	 */
	public void endQuickHelp() {
		// alle Layer bis auf den untersten aus dem FrameLayout entfernen
		if (layoutFrame.getChildCount() > 1) {
			layoutFrame.removeViews(layoutFrame.getChildCount() - 1, layoutFrame.getChildCount() - 1);
		}
		
		// zur�ck zum vorherigen Zustand
		switch (state) {
			case HELP_ACCEPT_REFPOINT:
				changeState(NavigationStates.ACCEPT_REFPOINT);
				break;
			case HELP_DELETE_REFPOINT:
				changeState(NavigationStates.DELETE_REFPOINT);
				break;
			case HELP_MARK_REFPOINT:
				changeState(NavigationStates.MARK_REFPOINT);
				break;
			case HELP_RUNNING:
				changeState(NavigationStates.RUNNING);
				break;
			default:
				Log.e("endQuickHelp", "Diesen Text sollte man nie angezeigt bekommen! Zustand: " + state);
				break;
		
		}
	}
	
	// //////// BUTTONS
	
	/**
	 * Der User hat auf den "Referenzpunkt setzen" Button gedr�ckt.
	 * 
	 * @param view
	 */
	public void setRefPoint(View view) {
		if (state != NavigationStates.RUNNING) {
			Log.w("setRefPoint", "Inkonsistenter Zustand: state != RUNNING");
		}
		
		// Pr�fe, ob wir momentan einen Referenzpunkt setzen d�rfen (sind bereits GPS-Koordinaten bekannt?)
		if (!locationDataManager.isMarkerPlacingAllowed()) {
			// Zeige Nachricht an, dass auf GPS Ortung gewartet werden muss
			Toast.makeText(this, getString(R.string.navigation_toast_no_gpsfix_yet), Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Zustands�nderung zum "Referenzpunkt Setzen" Zustand
		changeState(NavigationStates.MARK_REFPOINT);
		
		// der bet�tigte Button wird verborgen
		setRefPointButton.setVisibility(View.INVISIBLE);
		setRefPointButton.setEnabled(false);
	}
	
	/**
	 * Der Nutzer will den neu gesetzten Referenzpunkt behalten.
	 * 
	 * @param view
	 */
	public void acceptReferencePoint(View view) {
		mapView.acceptReferencePoint();
		
		// Anschlie�end befinden wir uns wieder im Anfangszustand
		changeState(NavigationStates.RUNNING);
	}
	
	/**
	 * Der Nutzer will den neu gesetzten Referenzpunkt verwerfen.
	 * 
	 * @param view
	 */
	public void cancelReferencePoint(View view) {
		// kann damit auch von onBackPressed aufgerufen werden, wenn wir noch in MARK_REFPOINT sind
		if (state == NavigationStates.ACCEPT_REFPOINT) {
			mapView.cancelReferencePoint();
		}
		
		// Anschlie�end befinden wir uns wieder im Anfangszustand
		changeState(NavigationStates.RUNNING);
	}
	
	/**
	 * Der Button zum L�schen des Referenzpunkts wurde bet�tigt.
	 * 
	 * @param view
	 */
	public void deleteReferencePoint(View view) {
		mapView.deleteReferencePoint();
		
		// Anschlie�end befinden wir uns wieder im Anfangszustand
		changeState(NavigationStates.RUNNING);
	}
	
	/**
	 * Der ausgew�hlte Referenzpunkt soll doch nicht gel�scht werden.
	 * 
	 * @param view
	 */
	public void refPointDeleteBack(View view) {
		mapView.dontDeleteReferencePoint();
		
		// Anschlie�end befinden wir uns wieder im Anfangszustand
		changeState(NavigationStates.RUNNING);
	}
	
	/**
	 * Aktiviere Zentrierung des Locationmarkers.
	 * 
	 * @param view
	 */
	public void trackPosition(View view) {
		trackPosition = true;
		view.setVisibility(View.GONE);
		mapView.centerCurrentLocation();
	}
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// HILFSFUNKTIONEN
	// ////////////////////////////////////////////////////////////////////////
	
	// //////// ZUSTANDS�NDERUNGEN
	
	/**
	 * Simpler Zustands�bergang ohne gro�e Rafinesse.
	 * 
	 * @param nextState
	 */
	public void changeState(NavigationStates nextState) {
		
		boolean changeToHelp = false;
		boolean changeFromHelp = false;
		
		NavigationStates oldState = state;
		state = nextState;
		
		Log.d("changeState", "Zustands�bergang von " + oldState + " zu " + state);
		
		if (oldState.isHelpState()) {
			changeFromHelp = true;
		}
		else if (oldState == NavigationStates.RENAME_MAP) {
			
			// wenn ein neuer Name eingegeben wurde, so ist er in newMapName gespeichert
			if (newMapName != "") {
				this.getSupportActionBar().setTitle(newMapName);
				thisMap.setMapname(newMapName);
				
				newMapName = "";
			}
			
		}
		
		// erst mal alle Buttons deaktivieren
		for (ImageButton imageButton : imageButtonList) {
			imageButton.setEnabled(false);
			imageButton.setVisibility(View.INVISIBLE);
		}
		
		// alle Layer bis auf den untersten aus dem FrameLayout entfernen
		// TODO nee, oder? siehe Bug #231
		// if (layoutFrame.getChildCount() > 1) {
		// layoutFrame.removeViews(layoutFrame.getChildCount() - 1, layoutFrame.getChildCount() - 1);
		// }
		
		// Rename Men�option deaktivieren
		if (menu != null) {
			menu.findItem(R.id.action_rename_map).setVisible(false);
		}
		
		// und dann je nach Zustand die passenden Buttons reaktivieren
		switch (state) {
			case ACCEPT_REFPOINT:
				// Buttons zum Akzeptieren und Verwerfen des gesetzten Referenzpunkts m�ssen angezeigt werden
				acceptRefPointButton.setVisibility(View.VISIBLE);
				acceptRefPointButton.setEnabled(true);
				cancelRefPointButton.setVisibility(View.VISIBLE);
				cancelRefPointButton.setEnabled(true);
				break;
			
			case DELETE_REFPOINT:
				// Button zum L�schen des Referenzpunkts muss angezeigt werden
				deleteRefPointButton.setVisibility(View.VISIBLE);
				deleteRefPointButton.setEnabled(true);
				break;
			
			case HELP_ACCEPT_REFPOINT:
				// Buttons von Accept_Refpoint einblenden aber nicht aktivieren
				acceptRefPointButton.setVisibility(View.VISIBLE);
				cancelRefPointButton.setVisibility(View.VISIBLE);
				
				// Es handelt sich um einen Wechsel zur Schnellhilfe
				changeToHelp = true;
				break;
			
			case HELP_DELETE_REFPOINT:
				// Buttons von Delete_Refpoint einblenden aber nicht aktivieren
				deleteRefPointButton.setVisibility(View.VISIBLE);
				
				changeToHelp = true;
				break;
			
			case HELP_MARK_REFPOINT:
				// hier gibt es aktuell keine Buttons anzuzeigen
				
				changeToHelp = true;
				break;
			
			case HELP_RUNNING:
				// Buttons von RUNNING einblenden aber nicht aktivieren
				setRefPointButton.setVisibility(View.VISIBLE);
				
				changeToHelp = true;
				break;
			
			case MARK_REFPOINT:
				// hier gibt es aktuell keine Buttons anzuzeigen
				break;
			
			case RENAME_MAP:
				
				// TODO das wirkt irgendwie alles so umst�ndlich... xD
				
				// erstelle AlertDialog f�r die schickere Umbenennung
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				
				builder.setTitle(R.string.navigation_rename_map);
				
				// Dr�cken des Umbenennen-Buttons benennt die Karte um
				builder.setPositiveButton(R.string.navigation_rename_map_rename, new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						changeState(NavigationStates.RUNNING);
						
					}
				});
				
				// Dr�cken des Cancel-Buttons beendet die Umbenennung
				builder.setNegativeButton(R.string.navigation_rename_map_cancel, new OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// der Kartenname soll nicht ge�ndert werden!
						newMapName = "";
						changeState(NavigationStates.RUNNING);
						
						
					}
				});
				
				// TODO was ist hier die Alternative zu null?
				View renameDialogLayout = getLayoutInflater().inflate(R.layout.navigation_rename_map, null);
				
				// bindet das Layout navigation_rename_map ein
				builder.setView(renameDialogLayout);
				
				AlertDialog dialog = builder.create();
				
				// Behandelt alle Arten den AlertDialog abzubrechen, ohne die Kn�pfe zu verwenden
				dialog.setOnCancelListener(new OnCancelListener() {
					
					@Override
					public void onCancel(DialogInterface dialog) {
						// der Kartenname soll nicht ge�ndert werden!
						newMapName = "";
						changeState(NavigationStates.RUNNING);
					}
				});
				
				// Anzeigen des Dialog
				dialog.show();
				
				EditText input = (EditText) renameDialogLayout.findViewById(R.id.editTextToNameMap);
				
				// Wenn der Text ge�ndert wird, wird der String newMapName angepasst
				input.addTextChangedListener(new TextWatcher() {
					
					@Override
					public void onTextChanged(CharSequence s, int start, int before, int count) {
						
					}
					
					@Override
					public void beforeTextChanged(CharSequence s, int start, int count, int after) {
						
					}
					
					@Override
					public void afterTextChanged(Editable s) {
						newMapName = s.toString();
						
					}
				});
				
				// der Aktuelle Kartenname wird dem Benutzer angezeigt
				input.setText(getTitle());
				break;
			
			case RUNNING:
				// Button zum Setzen von Referenzpunkten einblenden
				setRefPointButton.setVisibility(View.VISIBLE);
				setRefPointButton.setEnabled(true);
				
				// Wenn wir keine aktuelle GPS Position haben, Button "ausgegraut" anzeigen und deaktivieren
				if (locationDataManager == null || !locationDataManager.isMarkerPlacingAllowed()) {
					disableSetRefPointButton(true);
				}
				else {
					// ansonsten Button wieder aktivieren
					disableSetRefPointButton(false);
				}
				
				if (isUserPositionKnown()) {
					// track position button anzeigen, ist default ausgeblendet um crashes zu verhinden
					trackPositionButton.setVisibility(View.VISIBLE);
				}
				
				// nur in RUNNING kann man die Karte umbenennen
				if (menu != null) {
					menu.findItem(R.id.action_rename_map).setVisible(true);
				}
				
				if (intentPos != null) {
					onBackPressed();
					return;
				}
				break;
			
			default:
				break;
		}
		
		// Das Kurztutorial soll nur angezeigt werden, wenn es
		// a) aktiviert ist
		// und b) wir nicht zu einem Hilfebildschirm wechseln oder von einem solchen kommen
		if (quickTutorial && !changeFromHelp && !changeToHelp) {
			// wenn wir wieder bei RUNNING angekommen sind, wird die Hilfe erst wieder auf Nutzerwunsch ausgel�st
			if (state == NavigationStates.RUNNING) {
				quickTutorial = false;
				return;
			}
			else {
				// Ansonsten zeigen wir den Hilfe-Bildschirm an
				startQuickHelp();
			}
		}
	}
	
	/**
	 * Deaktiviert den Referenzpunkt-Setzen-Button (d.h. er wird ausgegraut, bleibt aber enabled) oder aktiviert ihn.
	 * 
	 * @param disable false zum Reaktivieren des Buttons
	 */
	private void disableSetRefPointButton(boolean disable) {
		if (disable) {
			// Button "ausgegraut" anzeigen
			setRefPointButton.setColorFilter(Color.GRAY);
			setRefPointButton.getBackground().setAlpha(127);
		}
		else {
			// Button aktivieren und ColorFilter entfernen
			setRefPointButton.clearColorFilter();
			setRefPointButton.getBackground().setAlpha(255);
		}
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// LOKALISIERUNG
	// ////////////////////////////////////////////////////////////////////////
	
	// //////// GPS
	
	@Override
	public void onLocationChanged(Location location) {
		if (location == null || locationDataManager == null || intentPos != null)
			return;
		
		// Wenn wir in RUNNING sind und wir bisher keine aktuellen Koordinaten hatten, m�ssen wir den (ausgegrauten)
		// Referenzpunkt-Setzen-Button reaktivieren.
		if (state == NavigationStates.RUNNING && !locationDataManager.isMarkerPlacingAllowed()) {
			disableSetRefPointButton(false);
		}
		
		// DEBUG: GPS-Mocker: zuf�llige Koordinaten sollen um Startposition herum gestreut werden
		if (mockBaseLocation == null) {
			mockBaseLocation = location;
		}
		
		// Ermittle Koordinatenkomponenten
		double lng = location.getLongitude();
		double lat = location.getLatitude();
		
		Log.d("Navigation", "GPS location changed: " + lat + "� N / " + lng + "� E");
		
		// �bergebe der Lokalisierung den aktuellen GPS-Punkt
		GpsPoint gpsPoint = new GpsPoint(lat, lng, SystemClock.elapsedRealtime());
		
		locationDataManager.addPoint(gpsPoint);
		
		// Der LocationDataManagerListener wird spaeter onNewUserPosition()
		// aufrufen, was das LocationView entsprechend verschiebt.
	}
	
	// TODO bin mir nicht sicher, ob hier was gemacht werden muss...
	@Override
	public void onProviderEnabled(String provider) {
		Log.d("Navigation", "Provider enabled: " + provider);
	}
	
	@Override
	public void onProviderDisabled(String provider) {
		Log.d("Navigation", "Provider disabled: " + provider);
	}
	
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		Log.d("Navigation", "Status changed: " + provider + ", status = " + status);
	}
	
	
	// //////// POSITION TRACKING (AUTO CENTER)
	
	public boolean isPositionTracked() {
		return trackPosition;
	}
	
	public void stopTrackingPosition() {
		trackPosition = false;
		trackPositionButton.setVisibility(View.VISIBLE);
	}
	
	
	// //////// DEBUG MODE - GPS
	
	// Simuliert zuf�llige GPS-Koordinaten
	private void debug_mockGPS() {
		// TODO eventuell GPS-Provider deaktivieren? mit der Methode, die in onPause verwendet wird
		
		Random random = new Random();
		double mockRadius = 0.1;
		
		// mockBaseLocation ist der Punkt, der als Zentrum f�r die zuf�llige Verteilung gew�hlt wird (Startkoordinaten)
		if (mockBaseLocation == null) {
			// Verwende folgende Default-Koordinaten, wenn keine Startkoordinaten bekannt (das ist der Fernsehturm :) )
			mockBaseLocation = new Location("mock");
			mockBaseLocation.setLatitude(52.520818);
			mockBaseLocation.setLongitude(13.409403);
		}
		
		Location mockLoc = new Location(mockBaseLocation);
		double dLat, dLon;
		String toastText;
		
		// Falls wir im Referenzpunkt-Setz-Modus sind, simuliere Hilfspunkte statt zuf�llige!
		// D.h. je nach Anzahl der Referenzpunkte, biete einen Punkt oben mittig, links unten oder rechts unten an.
		if (state == NavigationStates.MARK_REFPOINT || state == NavigationStates.ACCEPT_REFPOINT) {
			switch (mapView.countReferencePoints()) {
				case 0:
					dLat = 0.5 * mockRadius;
					dLon = 0;
					toastText = "Mitte oben (.5, 0)";
					break;
				
				case 1:
					dLat = -0.5 * mockRadius;
					dLon = -0.5 * mockRadius;
					toastText = "Links unten (-.5, -.5)";
					break;
				
				case 2:
					dLat = -0.5 * mockRadius;
					dLon = 0.5 * mockRadius;
					toastText = "Rechts unten (-.5, .5)";
					break;
				
				default:
					dLat = dLon = 0;
					toastText = "Mittelpunkt (0, 0)";
			}
		}
		else {
			dLat = (random.nextDouble() - 0.5) * 2 * mockRadius;
			dLon = (random.nextDouble() - 0.5) * 2 * mockRadius;
			toastText = "dLat " + dLat / mockRadius + "\ndLon " + dLon / mockRadius + "\n(relative to mock radius)";
		}
		
		mockLoc.setLatitude(mockLoc.getLatitude() + dLat);
		mockLoc.setLongitude(mockLoc.getLongitude() + dLon);
		
		// Koordinaten einspei�en
		Log.d("debug_mockGPS", "Set GPS coordinates to dLat = " + dLat + ", dLon = " + dLon + " (relative to starting point)");
		onLocationChanged(mockLoc);
		
		// Koordinatendifferenz in Toast anzeigen
		if (mockStatusToast != null) {
			mockStatusToast.cancel();
		}
		mockStatusToast = Toast.makeText(this, toastText, Toast.LENGTH_SHORT);
		mockStatusToast.show();
	}
	
	
	// //////// BILD-LOKALISIERUNG
	
	/**
	 * Wird vom LocationDataManagerListener beim Erhalten neuer Userposition aufgerufen.
	 */
	public void onNewUserPosition() {
		// Aktuellste Position vom LocationDataManager holen und setzen
		Point2D userPosition = locationDataManager.getLastImagePoint();
		
		if (userPosition == null || (userPosition.x == 0 && userPosition.y == 0)) {
			// Sollte eigentlich nicht mehr auftreten... wenn doch, return.
			Log.w("onNewUserPosition", "getLastImagePoint() returned " + (userPosition == null ? "null" : "(0,0"));
			return;
		}
		
		Log.d("Navigation", "Image position changed: " + userPosition.x + "px / " + userPosition.y + "px");
		
		// Verschiebe den LocationView-Marker
		setUserPosition(userPosition);
	}
	
	/**
	 * Position des Users gemessen in Pixeln relativ zum Bild setzen.
	 * Aktualisiert die Darstellung der LocationView.
	 * 
	 * @param newPos Koordinaten als Point2D
	 */
	public void setUserPosition(Point2D newPos) {
		userPosition = newPos;
		
		// Locationmarker aktualisieren
		mapView.updateLocationIcon();
		
		// Aktuelle Position zentrieren, falls tracking aktiviert
		if (isPositionTracked()) {
			mapView.centerCurrentLocation();
		}
	}
	
	/**
	 * Position des Users gemessen in Pixeln relativ zum Bild als Point2D.
	 */
	public Point2D getUserPosition() {
		return userPosition;
	}
	
	/**
	 * Gibt true zur�ck, wenn die aktuelle Benutzerposition bekannt ist, sonst false.
	 */
	public boolean isUserPositionKnown() {
		return userPosition != null;
	}
	
	/**
	 * Tr�gt einen neuen Referenzpunkt an der �bergebenen Position und mit dem
	 * �bergebenen timestamp beim LocationDataManager ein
	 * 
	 * @param position
	 * @param timestamp
	 */
	public boolean registerReferencePoint(Point2D position, long timestamp) {
		Log.d("registerReferencePoint", "Position: " + position + ", time: " + timestamp);
		
		try {
			locationDataManager.addMarker(position, timestamp);
		}
		catch (NoGpsDataAvailableException e) {
			// Keine (neuen) GPS-Daten sind verf�gbar -> f�r diese GPS-Koordinaten existiert bereits ein Referenzpunkt
			Log.w("registerReferencePoint", "addMarker failed because of NoGpsDataAvailableException: " + e.getMessage());
			
			// Fehlermeldung per Toast anzeigen
			int errorMsgID = e.getMessage() == "Point already known!"
					? R.string.navigation_toast_refpoint_already_set_for_this_position
					: R.string.navigation_toast_no_gpsfix_yet;
			
			Toast.makeText(this, getString(errorMsgID), Toast.LENGTH_SHORT).show();
			
			// aufrufende Funktion muss z.B. den unakzeptierten Referenzpunkt aufr�umen
			return false;
		}
		catch (PointNotInImageBoundsException e) {
			// Gew�nschter Referenzpunkt befindet sich au�erhalb der Bildgrenzen! (nicht m�glich)
			Log.w("registerReferencePoint", "addMarker failed because of PointNotInImageBoundsException: " + e.getMessage());
			
			// Fehlermeldung per Toast anzeigen
			Toast.makeText(this, getString(R.string.navigation_toast_refpoint_out_of_boundaries), Toast.LENGTH_SHORT).show();
			
			// aufrufende Funktion muss z.B. den unakzeptierten Referenzpunkt aufr�umen
			return false;
		}

		// Wieviele Referenzpunkte muss der Nutzer noch setzen?
		int refPointsLeftToSet = locationDataManager.remainingUserMarkerInputs();
		
		// Wenn der Nutzer noch nicht genug Referenzpunkte gesetzt hat, wird er darauf hingewiesen
		if (refPointsLeftToSet > 0) {
			Toast.makeText(this, 
					getString(R.string.navigation_toast_set_refpoint_prompt, refPointsLeftToSet),
					Toast.LENGTH_SHORT
			).show();
		}
		
		return true;
	}
	
	/**
	 * Weist den LocationDataManager an, einen Referenzpunkt zu l�schen.
	 * 
	 * @param position
	 */
	public boolean unregisterReferencePoint(Point2D position) {
		Log.d("unregisterReferencePoint", "Position: " + position);
		
		boolean result = iLDMIOHandler.removeMarker(position); 
		locationDataManager.refreshLastPosition();
		
		return result;
	}
	
}
