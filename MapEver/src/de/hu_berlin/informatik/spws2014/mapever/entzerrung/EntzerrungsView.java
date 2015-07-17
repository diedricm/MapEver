/* Copyright (C) 2014,2015 Jan Müller, Björn Stelter
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageButton;

import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

import de.hu_berlin.informatik.spws2014.mapever.R;
import de.hu_berlin.informatik.spws2014.mapever.largeimageview.LargeImageView;

public class EntzerrungsView extends LargeImageView {
	
	// ////// KEYS FÜR ZU SPEICHERNDE DATEN IM SAVEDINSTANCESTATE-BUNDLE
	
	private static final String SAVEDSHOWCORNERS = "SAVEDSHOWCORNERS";
	private static final String SAVEDCORNERS = "SAVEDCORNERS";
	private static final String SAVEDIMAGETYPESUPPORT = "SAVEDIMAGETYPESUPPORT";
	
	
	// ////// OTHER CONSTANTS
	private static final int PICTURE_TRANSPARENT = 200;
	private static final int PICTURE_OPAQUE = 255;
	
	// Count of rectangle corners
	private static final int CORNERS_COUNT = 4;
	
	// Maximum size for bitmap scaled for corner detection algorithm
	private static final int CDALG_MAX_WIDTH = 300;
	private static final int CDALG_MAX_HEIGHT = 300;
	
	// ////// PRIVATE MEMBERS
	
	// Activity context
	private Entzerren entzerren;
	
	// InputStream zum Bild
	private File imageFile;
	private boolean imageTypeSupportsDeskew = true;
	private boolean openCVLoadError = true;
	
	// Eckpunkte als OverlayIcons
	private CornerIcon[] corners = new CornerIcon[CORNERS_COUNT];
	
	// Zustandsvariablen
	private boolean show_corners = true;
	private boolean punkte_gesetzt = false;
	
	// Some objects for onDraw
	private Paint white = new Paint();
	private Path wallpath = new Path();
	
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// CONSTRUCTORS AND INITIALIZATION
	// ////////////////////////////////////////////////////////////////////////
	
	public EntzerrungsView(Context context) {
		super(context);
		init();
	}
	
	public EntzerrungsView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public EntzerrungsView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	/**
	 * Initializes EntzerrungsView (called by constructor), sets background color, image transparency and creates
	 * corner icons (each at 0,0).
	 */
	private void init() {
		// nichts weiter tun, wenn die View in Eclipse's GUI-Editor angezeigt wird
		if (this.isInEditMode())
			return;
		
		entzerren = (Entzerren) this.getContext();
		
		// Set background of view to black
		this.setBackgroundColor(Color.BLACK);
		
		// Set transparency of LIV image
		setForegroundAlpha(PICTURE_TRANSPARENT);
		
		// Paint for background: white square to highlight selected part of the map
		white.setColor(Color.WHITE);
		white.setStyle(Style.FILL);
		
		// Initialize corner icons
		corners[0] = new CornerIcon(this, new Point(0, 0));
		corners[1] = new CornerIcon(this, new Point(0, 0));
		corners[2] = new CornerIcon(this, new Point(0, 0));
		corners[3] = new CornerIcon(this, new Point(0, 0));
	}
	
	@Override
	protected Parcelable onSaveInstanceState() {
		// LargeImageView gibt uns ein Bundle, in dem z.B. die Pan-Daten stecken. Verwende dieses als Basis.
		Bundle bundle = (Bundle) super.onSaveInstanceState();
		
		// Speichere: "sollen die Ecken angezeigt (= das Bild entzerrt) werden?"
		bundle.putBoolean(SAVEDSHOWCORNERS, show_corners);
		
		// Speichere Positionen der 4 Eckpunkte
		Point[] cornerPoints = new Point[CORNERS_COUNT];
		for (int i = 0; i < CORNERS_COUNT; i++) {
			cornerPoints[i] = corners[i].getPosition();
		}
		
		bundle.putSerializable(SAVEDCORNERS, cornerPoints);
		
		// Speichere, ob Dateityp von den Algorithmen unterstützt wird (GIF z.B. nicht)
		bundle.putBoolean(SAVEDIMAGETYPESUPPORT, imageTypeSupportsDeskew);
		
		return bundle;
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle bundle = (Bundle) state;
		
		// Lade: "sollen die Ecken angezeigt (= das Bild entzerrt) werden?"
		boolean _show_corners = bundle.getBoolean(SAVEDSHOWCORNERS);
		showCorners(_show_corners);
		
		// Lade Positionen der 4 Eckpunkte
		Point[] cornerPoints = (Point[]) bundle.getSerializable(SAVEDCORNERS);
		for (int i = 0; i < CORNERS_COUNT; i++) {
			corners[i].setPosition(cornerPoints[i]);
		}
		punkte_gesetzt = true;
		
		// Lade, ob Dateityp von den Algorithmen unterstützt wird (GIF z.B. nicht)
		imageTypeSupportsDeskew = bundle.getBoolean(SAVEDIMAGETYPESUPPORT);
		
		// Im Bundle stecken noch Informationen von LargeImageView, z.B. Pan-Daten. Reiche das Bundle also weiter.
		super.onRestoreInstanceState(bundle); // calls update()
	}
	
	/**
	 * Lädt Bild in die EntzerrungsView. (Benutze dies anstelle von setImage...().)
	 */
	public void loadImage(File _imageFile) throws FileNotFoundException {
		// Image-File merken
		imageFile = _imageFile;
		
		// Bild laden
		setImageFilename(imageFile.getAbsolutePath());
	}
	
	/**
	 * Returns true, if image type is (hopefully?) supported by the deskewing algorithm (not for GIF, for example).
	 */
	public boolean isImageTypeSupported() {
		return imageTypeSupportsDeskew;
	}

	/**
	 * Returns true, if we failed to load OpenCV
	 */
	public boolean isOpenCVLoadError() {
		return openCVLoadError;
	}
	
	/**
	 * Erzeugt eine Bitmap aus dem geladenen Bild mit einer angegebenen SampleSize.
	 */
	public Bitmap getSampledBitmap(int sampleSize) {
		if (imageFile == null) {
			Log.w("EntzerrungsView/getSampledBitmap", "imageStream == null");
			return null;
		}
		
		// Minimum SampleSize 1
		sampleSize = Math.max(1, sampleSize);
		
		// Stream erzeugen
		InputStream imageStream;
		try {
			// Sollte eigentlich nie schiefgehen...
			imageStream = new FileInputStream(imageFile);
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
		
		// sampled bitmap dekodieren
		BitmapFactory.Options options = new Options();
		options.inSampleSize = sampleSize;
		
		Log.d("EntzerrungsView/getSampledBitmap", "Decoding stream with sample size " + sampleSize + "...");
		
		return BitmapFactory.decodeStream(imageStream, null, options);
	}
	
	/**
	 * Erzeugt eine runterskalierte Version des geladenen Bildes, das sich für den Corner Detection-Algorithmus eignet.
	 */
	private Bitmap getCDScaledBitmap() {
		if (imageFile == null) {
			Log.w("EntzerrungsView/getCDScaledBitmap", "imageStream == null");
			return null;
		}
		
		// Bestimmte optimale Auflösung... Vorerst: fixes Maximum für Höhe und Breite
		// TODO sinnvollere Lösung? bessere Konstanten? -> #230
		int scaledWidth = Math.min(getImageWidth(), CDALG_MAX_WIDTH);
		int scaledHeight = Math.min(getImageHeight(), CDALG_MAX_HEIGHT);
		
		// SampleSize berechnen, maximales Verhältnis zwischen originaler und optimaler Auflösung
		int sampleSize = Math.max(getImageWidth() / scaledWidth, getImageHeight() / scaledHeight);
		
		try {
			// Skaliertes Bitmap erzeugen
			Bitmap result = getSampledBitmap(sampleSize);
			
			if (result == null) {
				Log.e("EntzerrungsView/getCDScaledBitmap", "Decoding resulted in null...");
			}
			return result;
		}
		catch (OutOfMemoryError e) {
			Log.e("EntzerrungsView/getCDScaledBitmap", "Couldn't decode stream, out of memory!");
			
			return null;
		}
	}
	
	/**
	 * Nach dem Laden des Bildes werden die Ecken per Corner Detection ermittelt.
	 */
	@Override
	protected void onPostLoadImage(boolean calledByOnSizeChanged) {
		// LIV: Calculate zoom scale limits and stuff
		super.onPostLoadImage(calledByOnSizeChanged);
		
		if (getWidth() != 0 && getHeight() != 0) {
			// Find corners with Corner Detection Algorithm
			if (!punkte_gesetzt) {
				calcCornersWithDetector();
			}
		}
	}
	
	/**
	 * Zeichnet das Bild mittels LargeImageView und stellt das helle Entzerrungsrechteck dar.
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		// nichts weiter tun, wenn die View in Eclipse's GUI-Editor angezeigt wird
		if (this.isInEditMode()) {
			return;
		}
		
		// check if we are ready to draw (getWidth and getHeight return non-zero values etc.)
		if (!isReadyToDraw()) {
			return;
		}
		
		if (show_corners) {
			wallpath.reset();
			
			// Bildschirmkoordinaten der Punkte ermitteln
			// (Ecken sind bereits sortiert)
			PointF[] canvasPoints = new PointF[CORNERS_COUNT];
			
			boolean somethingIsNull = false;
			
			for (int i = 0; i <= 3; i++) {
				canvasPoints[i] = corners[i].getScreenPosition();
				
				// Sollte eigentlich nie passieren...?
				if (canvasPoints[i] == null) {
					somethingIsNull = true;
					Log.w("EntzerrungsView/onDraw", "the " + i + "-th corner screen positions is null!");
					break;
				}
			}
			
			if (!somethingIsNull) {
				wallpath.moveTo(canvasPoints[0].x, canvasPoints[0].y);
				wallpath.lineTo(canvasPoints[1].x, canvasPoints[1].y);
				wallpath.lineTo(canvasPoints[2].x, canvasPoints[2].y);
				wallpath.lineTo(canvasPoints[3].x, canvasPoints[3].y);
				
				canvas.drawPath(wallpath, white);
			}
		}
		
		// Bild per LargeImageView anzeigen
		super.onDraw(canvas);
	}
	
	/**
	 * Behandlung von Touchevents.
	 */
	// Lint-Warnung "EntzerrungsView overrides onTouchEvent but not performClick", obwohl sich super.onTouchEvent()
	// um Aufruf von performClick() kümmert.
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		
		if (entzerren.isLoadingActive())
			return false;
		
		// Zeigen wir die Schnellhilfe an?
		if (entzerren.isInQuickHelp()) {
			// Hier kein Pan/Zoom, aber Klicks sollen die Hilfe beenden.
			onTouchEvent_clickDetection(event);
			return true;
		}
		
		// Alle weiteren Touch-Events werden von der LargeImageView sowie anderen EventHandlers behandelt.
		// (Siehe z.B. CornerIcon für das Verschieben der Eckpunkte.)
		
		// Führe Defaulthandler aus (der Klicks, Pan und Zoom behandelt)
		return super.onTouchEvent(event);
	}
	
	@Override
	public boolean onClickPosition(float clickX, float clickY) {
		// Schnellhilfe deaktivieren, falls aktiv
		if (entzerren.isInQuickHelp()) {
			entzerren.endQuickHelp();
			return true;
		}
		
		// Kein Klickevent behandelt
		return false;
	}
	
	/**
	 * Calculates default coordinates for corners (20%/80% of screen size).
	 */
	public void calcCornerDefaults() {
		// Retrieve image dimensions
		int bitmap_breite = getImageWidth();
		int bitmap_hoehe = getImageHeight();
		
		// // Retrieve view dimensions
		// int view_breite = this.getWidth();
		// int view_hoehe = this.getHeight();
		//
		// // Choose the smaller one of each
		// int breite = Math.min(bitmap_breite, view_breite);
		// int hoehe = Math.min(bitmap_hoehe, view_hoehe);
		
		Log.d("EntzerrungsView/calcCornerDefaults", "breite/hoehe: " + bitmap_breite + ", " + bitmap_hoehe);
		
		// Take 20% of it
		int breite_scaled = (int) (0.2 * bitmap_breite);
		int hoehe_scaled = (int) (0.2 * bitmap_hoehe);
		
		// Set corner positions to 20% / 80% of the image or view size
		corners[0].setPosition(new Point(breite_scaled, hoehe_scaled));
		corners[1].setPosition(new Point((bitmap_breite - breite_scaled), hoehe_scaled));
		corners[2].setPosition(new Point((bitmap_breite - breite_scaled), (bitmap_hoehe - hoehe_scaled)));
		corners[3].setPosition(new Point(breite_scaled, (bitmap_hoehe - hoehe_scaled)));
		
		Log.d("EntzerrungsView/calcCornerDefaults", "Using default, breite/hoehe_scaled: " + breite_scaled + ", " + hoehe_scaled);
		
		punkte_gesetzt = true;
	}
	
	/**
	 * Use corner detection algorithm to find and set corners automatically.
	 */
	public void calcCornersWithDetector() {
		// Bitmap berechnen, die für CD-Algorithmus runterskaliert wurde
		Bitmap bmp32 = getCDScaledBitmap();
		
		if (bmp32 == null || getImageWidth() <= 0) {
			Log.e("EntzerrungsView/calcCornersWithDetector", bmp32 == null
					? "getCDScaledBitmap() returned null!"
					: "getImageWidth() is nonpositive!");
			calcCornerDefaults();
			return;
		}
		
		float sampleSize = getImageWidth() / bmp32.getWidth();
		
		org.opencv.core.Point[] corner_points;
		
		try {
			Mat imgMat = new Mat();
			Utils.bitmapToMat(bmp32, imgMat);
			Mat greyMat = new Mat();
			Imgproc.cvtColor(imgMat, greyMat, Imgproc.COLOR_RGB2GRAY);
			
			corner_points = CornerDetector.guess_corners(greyMat);
		}
		catch (CvException e) {
			Log.w("EntzerrungsView/calcCornersWithDetector", "Corner detection failed with CvException");
			e.printStackTrace();
			
			// it seems that the image type is not supported by the corner detection algorithm (GIF?)
			// it won't be deskewable either, so deactivate that feature
			showCorners(false);
			imageTypeSupportsDeskew = false;
			
			calcCornerDefaults();
			return;
		}
		catch (UnsatisfiedLinkError e) {
			Log.w("EntzerrungsView/calcCornersWithDetector", "OpenCV not available");
			openCVLoadError = true;
			calcCornerDefaults();
			return;
		}

		
		// Im Fehlerfall Standardecken verwenden
		if (corner_points == null) {
			Log.w("EntzerrungsView/calcCornersWithDetector", "Corner detection returned null");
			calcCornerDefaults();
			return;
		}
		
		// Koordinaten auf ursprüngliche Bildgröße hochrechnen
		for (int i = 0; i < corner_points.length; i++) {
			corner_points[i].x *= sampleSize;
			corner_points[i].y *= sampleSize;
		}
		
		Log.d("Corner points", "0: " + corner_points[0] + " 1: " + corner_points[1] + " 2: " + corner_points[2] + " 3: " + corner_points[3]);
		
		// Algorithmusergebnis als Eckpunkte verwenden
		corners[0].setPosition(corner_points[0]);
		corners[1].setPosition(corner_points[1]);
		corners[2].setPosition(corner_points[2]);
		corners[3].setPosition(corner_points[3]);
		
		// Sortieren (obwohl sie eigentlich sortiert sein sollten...?)
		sortCorners();
		
		punkte_gesetzt = true;
	}
	
	/**
	 * Shows or hides corners. (Image shall not be rectified if corners are hidden.)
	 * 
	 * @param show
	 */
	public void showCorners(boolean show) {
		ImageButton my_button = (ImageButton) entzerren.findViewById(R.id.entzerrung_ok_button);
		
		// OverlayIcons verstecken, falls keine Ecken angezeigt werden sollen
		for (int i = 0; i <= 3; i++) {
			corners[i].setVisibility(show);
		}
		
		// Bild des Buttons abhängig von der Aktion machen (mit (Crop) oder ohne (Done) Entzerrung?)
		// und Transparenz des Bildes setzen (Visualisierung des Entzerrungsvierecks)
		if (show) {
			my_button.setImageResource(R.drawable.ic_action_crop);
			setForegroundAlpha(PICTURE_TRANSPARENT);
		}
		else {
			my_button.setImageResource(R.drawable.ic_action_done);
			setForegroundAlpha(PICTURE_OPAQUE);
		}
		
		show_corners = show;
		update();
	}
	
	/**
	 * Returns true if corners are shown. (Image shall not be rectified if corners are hidden.)
	 */
	public boolean isShowingCorners() {
		return show_corners;
	}
	
	
	/**
	 * Eckpunkte sortieren, um "sinnvolles" Rechteck anzuzeigen. (Wird von CornerIcon.onDragMove() aufgerufen.)
	 */
	public void sortCorners() {
		// Sortiere Ecken erstmal nach y-Koordinate, d.h. Ecke 1 und 2 sind die beiden mit höchsten y-Koordinaten
		Arrays.sort(corners, new Comparator<CornerIcon>() {
			@Override
			public int compare(CornerIcon lhs, CornerIcon rhs) {
				// return <0 for lhs<rhs, =0 for lhs=rhs, >0 for lhs>rhs
				return lhs.getPosition().y - rhs.getPosition().y;
			}
		});
		
		// -- Ecke 1 (links oben): die linkeste Ecke der zwei obersten Ecken
		// -- Ecke 2 (rechts oben): die andere Ecke der zwei obersten Ecken
		if (corners[0].getPosition().x > corners[1].getPosition().x) {
			// Swap linkere obere und rechte obere Ecke
			CornerIcon tmp = corners[0];
			corners[0] = corners[1];
			corners[1] = tmp;
		}
		
		// -- Ecke 3 (rechts unten): die rechte Ecke der verbleibenden
		// -- Ecke 4 (links unten): die verbleibende Ecke
		if (corners[2].getPosition().x < corners[3].getPosition().x) {
			// Swap rechte untere und linke untere Ecke
			CornerIcon tmp = corners[2];
			corners[2] = corners[3];
			corners[3] = tmp;
		}
	}
	
	/**
	 * Gibt ein float[8] zurück mit x- und y-Koordinaten der Eckpunkte in Reihe (x1, y1, ..., x4, y4).
	 * 
	 * @param sampleSize Koordinaten werden angepasst (durch sampleSize dividiert), mindestens 1.
	 * @return float[8] {x1, y1, x2, y2, x3, y3, x4, y4}
	 */
	public float[] getPointOffsets(int sampleSize) {
		sampleSize = Math.max(1, sampleSize);
		
		// Array für Koordinaten erzeugen
		float[] my_f = new float[2 * CORNERS_COUNT];
		
		for (int i = 0; i < CORNERS_COUNT; i++) {
			my_f[i * 2] = corners[i].getImagePositionX() / sampleSize;
			my_f[i * 2 + 1] = corners[i].getImagePositionY() / sampleSize;
		}
		
		return my_f;
	}
	
}
