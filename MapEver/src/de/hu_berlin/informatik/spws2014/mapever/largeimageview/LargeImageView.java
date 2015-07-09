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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import de.hu_berlin.informatik.spws2014.mapever.largeimageview.CachedImage.CacheMissResolvedCallback;

public class LargeImageView extends ImageView {
	
	// ////// KEYS FÜR ZU SPEICHERNDE DATEN IM SAVEDINSTANCESTATE-BUNDLE
	
	// Key für das Parcelable, das super.onSaveInstanceState() zurückgibt
	private static final String SAVEDVIEWPARCEL = "savedViewParcel";
	
	// Keys für Pan- und Zoomdaten
	private static final String SAVEDPANX = "savedPanCenterX";
	private static final String SAVEDPANY = "savedPanCenterY";
	private static final String SAVEDZOOM = "savedZoomScale";
	
	
	// ////// CONSTANTS
	
	// Toleranz für Abweichung vom Startpunkt beim Klicken in px
	private static final int TOUCH_CLICK_TOLERANCE = 6;
	
	
	// ////// BITMAP, TILE AND CACHE STUFF
	
	// Statische Bitmap, wird angezeigt, falls kein RegionDecoder initialisiert wurde
	private Bitmap staticBitmap = null;
	
	// Last-Recently-Used Cache für Tiles
	private CachedImage cachedImage;
	
	// Bildgröße, falls bekannt, sonst -1
	private int imageWidth = -1;
	private int imageHeight = -1;
	
	// Paint-Objekt, das dazu da ist, das Hintergrundbild transparent zu machen (nur falls not null)
	private Paint bgAlphaPaint;
	
	
	// ////// DISPLAY, PAN- UND ZOOMWERTE
	
	// Aktuelle Pan-Center-Koordinaten und Zoom-Scale
	// NOTE: panPos wird jetzt andersherum betrachtet: positiver Wert = die Karte ist nach links/rechts verschoben
	// Die Pan-Center-Position gibt jetzt den Punkt an, der im Mittelpunkt des Sichtfeldes liegt.
	private float panCenterX = Float.NaN;
	private float panCenterY = Float.NaN;
	private float zoomScale = 1f;
	
	// Sample-Stufe, wird automatisch aus zoomScale berechnet
	// (sampleSize: größer = geringere Auflösung; zoomScale: kleiner = weiter weg vom Bild)
	private int sampleSize = 1;
	
	// Minimales und maximales Zoom-Level (Defaultwerte, werden pro Bild neu berechnet)
	private float minZoomScale = 0.1f;
	private float maxZoomScale = 5.0f;
	
	
	// ////// TOUCH EVENTS
	
	// Startkoordinaten bei einem Touch-Down-Event um Klicks zu erkennen
	private boolean touchCouldBeClick = false;
	private float touchStartX;
	private float touchStartY;
	
	// SGD behandelt die Zoom-Gesten
	private ScaleGestureDetector SGD;
	
	// Hilfsinformationen für Panning und Zooming
	private boolean panActive = false;
	private int panActivePointerId;
	private float panLastTouchX;
	private float panLastTouchY;
	private boolean panLastTouchIsScaleFocus = false;
	
	// Findet gerade ein Drag-Vorgang statt?
	private boolean currentlyDragging = false;
	
	
	// ////// OVERLAY ICONS
	private ArrayList<OverlayIcon> overlayIconList = new ArrayList<OverlayIcon>();
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// CONSTRUCTORS AND INITIALIZATION
	// ////////////////////////////////////////////////////////////////////////
	
	public LargeImageView(Context context) {
		super(context);
		init();
	}
	
	public LargeImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public LargeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}
	
	/**
	 * Initialisiert die LargeImageView (wird vom Konstruktor aufgerufen).
	 */
	private void init() {
		// Erstelle SGD, der fürs Zooming zuständig ist
		SGD = new ScaleGestureDetector(getContext(), new ScaleListener());
	}
	
	@Override
	protected Parcelable onSaveInstanceState() {
		// Ich weiß nicht, was in dem Parcelable von View drinsteckt, aber ich wills auch nicht einfach wegwerfen...
		Parcelable parcel = super.onSaveInstanceState();
		
		Bundle bundle = new Bundle();
		bundle.putParcelable(SAVEDVIEWPARCEL, parcel);
		
		// Speichere Pan- und Zoom-Werte im State.
		bundle.putFloat(SAVEDPANX, panCenterX);
		bundle.putFloat(SAVEDPANY, panCenterY);
		bundle.putFloat(SAVEDZOOM, zoomScale);
		
		// TODO Können wir irgendwie das Bild/den Stream oder eine Referenz darauf speichern? Und viel
		// wichtiger, den Cache? Aktuell muss das Bild beim Drehen immer neu aufgebaut werden... -> #194
		
		return bundle;
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle bundle = (Bundle) state;
		
		// Ich weiß nicht, was in dem Parcelable von View drinsteckt, aber ich wills auch nicht einfach wegwerfen...
		super.onRestoreInstanceState(bundle.getParcelable(SAVEDVIEWPARCEL));
		
		// // Pan und Zoom wiederherstellen
		float panX = bundle.getFloat(SAVEDPANX);
		float panY = bundle.getFloat(SAVEDPANY);
		float zoom = bundle.getFloat(SAVEDZOOM);
		setPanZoom(panX, panY, zoom); // calls update()
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		Log.d("LIV/onSizeChanged", "w, h, oldw, oldh: " + w + ", " + h + ", " + oldw + ", " + oldh);
		
		if (getWidth() == 0 || getHeight() == 0) {
			Log.e("LIV/onSizeChanged", "getWidth() or getHeight() is still zero! (w " + getWidth() + ", h " + getHeight() + ")");
			return;
		}
		
		// If we have already loaded an image...
		if (cachedImage != null || staticBitmap != null) {
			onPostLoadImage(true);
		}
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// LADEN VON BILDERN
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Setzt einen InputStream als Bildquelle. Hierbei wird nach Möglichkeit über CachedImage ein BitmapRegionDecoder
	 * instanziiert, der Bildteile nach Bedarf lädt, statt das gesamte Bild in eine Bitmap zu laden.
	 * 
	 * Da der BitmapRegionDecoder nur JPEG und PNG unterstützt, wird bei anderen Formaten (z.B. GIF) sowie im
	 * Fehlerfall eine IOException geworfen. (Es kann danach versucht werden, das Bild per setImageBitmap statisch
	 * zu laden.)
	 * 
	 * @param inputStream
	 * @throws IOException
	 */
	public void setImageStream(InputStream inputStream) throws IOException {
		// reset image references
		cachedImage = null;
		staticBitmap = null;
		
		// reset pan
		panCenterX = panCenterY = Float.NaN;
		
		// Instanziiere ein CachedImage über den gegebenen InputStream.
		// Wirft eine IOException, falls das Bild kein JPEG oder PNG ist, oder ein unerwarteter IO-Fehler auftrat.
		cachedImage = new CachedImage(inputStream, new CacheMissResolvedCallback() {
			@Override
			public void onCacheMissResolved() {
				// Wenn nach einem Cache-Miss ein gesuchtes Tile generiert wurde, aktualisiere Ansicht
				update();
			}
		});
		
		// Breite und Höhe des Bildes zwischenspeichern
		imageWidth = cachedImage.getWidth();
		imageHeight = cachedImage.getHeight();
		
		// Berechnet unter anderem Zoom-Limits
		onPostLoadImage(false);
	}
	
	/**
	 * Lädt statisch eine Bitmap als Bildquelle. Statisch bedeutet in diesem Fall, dass es nicht als large image
	 * von CachedImage behandelt wird, sondern als ganzes Bitmap in die View geladen wird.
	 */
	@Override
	public void setImageBitmap(Bitmap bitmap) {
		// Verwende statisch die Bitmap zum Darstellen
		cachedImage = null;
		staticBitmap = bitmap;
		
		// reset pan
		panCenterX = panCenterY = Float.NaN;
		
		if (bitmap != null) {
			imageWidth = staticBitmap.getWidth();
			imageHeight = staticBitmap.getHeight();
			
			// Berechnet unter anderem Zoom-Limits
			onPostLoadImage(false);
		}
	}
	
	/**
	 * Gibt eine statisch geladene Bitmap (d.h. nicht gecachtes oder zerteiltes Bild) zurück, wenn vorhanden.
	 */
	public Bitmap getStaticBitmap() {
		return staticBitmap;
	}
	
	/**
	 * Lädt eine Resource als Bildquelle. Hierbei wird nach Möglichkeit über CachedImage ein BitmapRegionDecoder
	 * instanziiert, indem ein InputStream is erzeugt und setImageStream(is) aufgerufen wird.
	 */
	@Override
	public void setImageResource(int resId) {
		try {
			// Lade die Resource per Stream
			InputStream stream = getResources().openRawResource(resId);
			setImageStream(stream);
		}
		catch (IOException e) {
			// Vermutlich schlägt dies fehl, weil die Resource weder JPEG noch PNG ist...
			Log.w("LIV/setImageStream", "Can't instantiate CachedImage:");
			Log.w("LIV/setImageStream", e.toString());
			
			// Fallback: Lade das Bild statisch als Bitmap (Stream muss neu geöffnet werden)
			InputStream stream = getResources().openRawResource(resId);
			setImageBitmap(BitmapFactory.decodeStream(stream));
		}
	}
	
	/**
	 * Lädt eine Bilddatei per Dateinamen als Bildquelle. Hierbei wird nach Möglichkeit über CachedImage ein
	 * BitmapRegionDecoder instanziiert, indem ein InputStream is erzeugt und setImageStream(is) aufgerufen wird.
	 */
	public void setImageFilename(String filename) throws FileNotFoundException {
		try {
			// Lade das Bild per Stream
			InputStream stream = new FileInputStream(filename);
			setImageStream(stream);
		}
		catch (IOException e) {
			// Vermutlich schlägt dies fehl, weil die Resource weder JPEG noch PNG ist...
			Log.w("LIV/setImageStream", "Can't instantiate CachedImage:");
			Log.w("LIV/setImageStream", e.toString());
			
			// Fallback: Lade das Bild statisch als Bitmap (Stream muss neu geöffnet werden)
			InputStream stream = new FileInputStream(filename);
			setImageBitmap(BitmapFactory.decodeStream(stream));
		}
	}
	
	/**
	 * Do not use! Won't be implemented.
	 */
	@Deprecated
	@Override
	public void setImageURI(Uri uri) {
		// Not implemented because not needed... but we override it to avoid errors if someone does use it.
		
		// Reset stuff
		cachedImage = null;
		staticBitmap = null;
		panCenterX = panCenterY = Float.NaN;
		
		Log.e("LIV/setImageURI", "setImageURI not implemented!");
	}
	
	/**
	 * Wird aufgerufen, nachdem ein Bild geladen wurde. Wenn überschrieben, dann unbedingt super.onPostLoadImage()
	 * aufrufen, da diese Implementierung z.B. noch Zoom-Limits berechnet und das Bild zentriert.
	 */
	protected void onPostLoadImage(boolean calledByOnSizeChanged) {
		// If called before onLayout() we don't know width and height yet... so we have to call this method later
		// in onSizeChanged again.
		if (getWidth() != 0 && getHeight() != 0) {
			// (re-)calculate MIN_ and MAX_ZOOM_SCALE
			calculateZoomScaleLimits();
			
			// If no pan has been set yet (just loaded): center image and zoom out until whole image is visible
			if (Float.isNaN(panCenterX) || Float.isNaN(panCenterY)) {
				setPanZoomFitImage();
			}
		}
		else {
			Log.d("LIV/onPostLoadImage", "Couldn't execute onPostLoadImage yet, no width/height known!");
		}
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// IMAGE PROPERTIES
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Gibt die Breite des Bildes zurück. (Tatsächliche Bildgröße, auch wenn nur kleinere Teile geladen sind.)
	 */
	public int getImageWidth() {
		return imageWidth;
	}
	
	/**
	 * Gibt die Höhe des Bildes zurück. (Tatsächliche Bildgröße, auch wenn nur kleinere Teile geladen sind.)
	 */
	public int getImageHeight() {
		return imageHeight;
	}
	
	/**
	 * Gibt Transparenz des Bildes zurück ("Background" um Verwechslung mit setImageAlpha() zu vermeiden,
	 * im Gegensatz zu Foreground, was dann die OverlayIcons wären).
	 * 
	 * @return Wert von 0 (vollkommen transparent) bis 255 (undurchsichtig).
	 */
	public int getBackgroundAlpha() {
		return bgAlphaPaint == null ? 255 : bgAlphaPaint.getAlpha();
	}
	
	/**
	 * Setze Transparenz des angezeigten Bildes ("Foreground" um Verwechslung mit setImageAlpha() zu vermeiden,
	 * nicht Background, um Verwechslung mit setBackgroundColor() zu vermeiden... alles sehr verwirrend).
	 * 
	 * @param newAlpha Wert von 0 (vollkommen transparent) bis 255 (undurchsichtig).
	 */
	public void setForegroundAlpha(int newAlpha) {
		bgAlphaPaint = new Paint();
		bgAlphaPaint.setAlpha(newAlpha);
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// PANNING UND ZOOMING
	// ////////////////////////////////////////////////////////////////////////
	
	// ////// GETTERS AND SETTERS
	
	/** Gibt aktuelle Pan-Center-X-Koordinate (Bildpunkt, der im Sichtfeld zentriert wird) zurück. */
	public float getPanCenterX() {
		return panCenterX;
	}
	
	/** Gibt aktuelle Pan-Center-Y-Koordinate (Bildpunkt, der im Sichtfeld zentriert wird) zurück. */
	public float getPanCenterY() {
		return panCenterY;
	}
	
	/** Gibt aktuelle Pan-Center-Koordinaten (Bildpunkt, der im Sichtfeld zentriert wird) als PointF zurück. */
	public PointF getPanCenter() {
		return new PointF(panCenterX, panCenterY);
	}
	
	/** Setzt neue Pan-Center-Koordinaten (Bildpunkt, der im Sichtfeld zentriert wird). */
	public void setPanCenter(float newX, float newY) {
		panCenterX = newX;
		panCenterY = newY;
		update();
	}
	
	/** Setzt neue Pan-Center-Koordinaten (Bildpunkt, der im Sichtfeld zentriert wird). */
	public void setPanCenter(PointF point) {
		if (point == null) {
			panCenterX = panCenterY = 0;
		}
		else {
			panCenterX = (float) point.x;
			panCenterY = (float) point.y;
		}
		update();
	}
	
	/** Gibt aktuelles Zoom-Level zurück. (Je kleiner, desto weiter weg ist das Bild.) */
	public float getZoomScale() {
		return zoomScale;
	}
	
	/** Gibt aktuelle Sample-Stufe zurück. (Je größer, desto geringer ist die Auflösung des Bildes.) */
	public int getSampleSize() {
		return sampleSize;
	}
	
	/** Setzt neues Zoom-Level und berechnet Sample-Stufe neu. */
	public void setZoomScale(float newZoomScale) {
		zoomScale = newZoomScale;
		
		// Zoom-Level darf Minimum und Maximum nicht unter-/überschreiten
		if (zoomScale < minZoomScale || zoomScale > maxZoomScale) {
			zoomScale = Math.max(minZoomScale, Math.min(zoomScale, maxZoomScale));
		}
		
		// SampleSize neuberechnen
		sampleSize = calculateSampleSize(zoomScale);
		update();
	}
	
	/**
	 * Setzt neue Pan-Center-Koordinaten (Bildpunkt, der im Sichtfeld zentriert wird) und neues Zoom-Level
	 * (und berechnet Sample-Stufe neu).
	 */
	public void setPanZoom(float newX, float newY, float newZoomScale) {
		panCenterX = newX;
		panCenterY = newY;
		setZoomScale(newZoomScale); // calls update()
	}
	
	/** Zentriert den Bildmittelpunkt, indem das Pan-Center auf diesen gesetzt wird. */
	public void setPanCenterToImageCenter() {
		float centerX = imageWidth / 2;
		float centerY = imageHeight / 2;
		setPanCenter(centerX, centerY); // calls update()
	}
	
	/**
	 * Zentriert das Bild und setzt die Zoomstufe so, dass das ganze Bild sichtbar ist. Es wird also herausgezoomt,
	 * bis kein Teil des Bildes mehr abgeschnitten wird, eventuell mit Letterbox/Pillarbox, aber es wird nicht
	 * herangezoomt.
	 */
	public void setPanZoomFitImage() {
		if (getWidth() == 0 || getHeight() == 0 || imageWidth <= 0 || imageHeight <= 0) {
			Log.w("LIV/setPanZoomFitImage", "Some dimensions are still unknown: getWidth/getHeight: " + getWidth() +
					"/" + getHeight() + ", imageWidth/imageHeight: " + imageWidth + "/" + imageHeight);
			return;
		}
		
		// Bild zentrieren und so weit rauszoomen, dass das ganze Bild sichtbar ist (nicht jedoch das Bild abschneiden
		// oder heranzoomen)
		float centerX = imageWidth / 2;
		float centerY = imageHeight / 2;
		float fitZoomScale = Math.min((float) getWidth() / imageWidth, (float) getHeight() / imageHeight);
		fitZoomScale = Math.min(1, fitZoomScale);
		
		setPanZoom(centerX, centerY, fitZoomScale); // calls update()
	}
	
	
	// ////// SAMPLESIZE UND MAX/MIN ZOOM SCALE BERECHNUNG
	
	/**
	 * Berechnet die Sample-Stufe zu einer Zoom-Stufe. Dies ist dabei die größte Zweierpotenz, die <= 1/scale ist.
	 * 
	 * @param scale Zoom-Stufe
	 * @return Sample-Stufe
	 */
	public static int calculateSampleSize(float scale) {
		int sample = 1;
		
		// bilde ganzzahligen Kehrwert von scale (kann für scale < 1 null werden)
		int x = (int) (1.0 / scale);
		
		// Das Sampling Level ist die größte Zweierpotenz, die <= 1/scale ist.
		// Wir finden diese, indem wir x durch 2 teilen und samplingLevel verdoppeln, bis x = 0 ist.
		// z.B. x=9:
		// x=9, s=1 --> x=4, s=2 --> x=2, s=4 --> x=1, s=8 --> x=0, s=8.
		
		while ((x /= 2) > 0) {
			sample *= 2;
		}
		
		// Begrenze Samplesize auf 32 (sollte ausreichen)
		if (sample > 32) {
			sample = 32;
		}
		
		return sample;
	}
	
	/**
	 * Berechnet optimale Zoom-Grenzen.
	 */
	private void calculateZoomScaleLimits() {
		if (getWidth() == 0 || getHeight() == 0 || imageWidth <= 0 || imageHeight <= 0) {
			Log.w("LIV/calcZoomScaleLimits", "Some dimensions are still unknown: getWidth/getHeight: " + getWidth() +
					"/" + getHeight() + ", imageWidth/imageHeight: " + imageWidth + "/" + imageHeight);
			return;
		}
		
		// Wie groß ist der Bildschirm relativ zur Karte?
		double relativeWidth = ((double) getWidth()) / imageWidth;
		double relativeHeight = ((double) getHeight()) / imageHeight;
		
		// Man kann nur soweit rauszoomen, dass die ganze Karte und noch etwas Rand auf den Bildschirm passt.
		minZoomScale = (float) (0.8 * Math.min(1, Math.min(relativeHeight, relativeWidth)));
		
		// Man kann bei hinreichend großen Bildern auf 6x ranzoomen, bei sehr kleinen Bildern maximal so, dass
		// sie den Bildschirm ausfüllen.
		maxZoomScale = (float) Math.max(6.0, Math.max(relativeHeight, relativeWidth));
	}
	
	
	// ////// POSITIONSUMRECHNUNGEN
	
	/**
	 * Gibt zu einer Bildschirmposition die (aktuelle) Bildposition zurück.
	 */
	public PointF screenToImagePosition(float screenX, float screenY) {
		if (Float.isNaN(panCenterX) || Float.isNaN(panCenterY) || getWidth() == 0 || getHeight() == 0) {
			Log.w("LIV/screenToImgPosition", "Either panCenter is not initialized (" + panCenterX + ", " + panCenterY
					+ ") or view dimensions are still zero (getWidth/getHeight: " + getWidth() + "/" + getHeight() + ")");
			return null;
		}
		
		// Der Offset berechnet sich aus PanCenterPos und halber (scale-gewichteter) Viewgröße.
		float imageX = panCenterX + (-getWidth() / 2 + screenX) / zoomScale;
		float imageY = panCenterY + (-getHeight() / 2 + screenY) / zoomScale;
		
		return new PointF(imageX, imageY);
	}
	
	/**
	 * Gibt zu einer Bildposition die (aktuelle) Bildschirmposition zurück.
	 */
	public PointF imageToScreenPosition(float imageX, float imageY) {
		if (Float.isNaN(panCenterX) || Float.isNaN(panCenterY) || getWidth() == 0 || getHeight() == 0) {
			Log.w("LIV/screenToImgPosition", "Either panCenter is not initialized (" + panCenterX + ", " + panCenterY
					+ ") or view dimensions are still zero (getWidth/getHeight: " + getWidth() + "/" + getHeight() + ")");
			return null;
		}
		
		// Umkehrfunktion zu screenToImagePosition()
		float screenX = (imageX - panCenterX) * zoomScale + getWidth() / 2;
		float screenY = (imageY - panCenterY) * zoomScale + getHeight() / 2;
		
		return new PointF(screenX, screenY);
	}
	
	
	// ////// EVENT HANDLERS
	
	/**
	 * Wird getriggert, wenn sich Pan-Position oder Zoom durch ein Touch-Event ändern. Tut nichts, kann aber von
	 * Subklassen überschrieben werden.
	 */
	protected void onTouchPanZoomChange() {
		return;
	}
	
	/**
	 * Wird getriggert, wenn ein Klick auf eine bestimmte Bildschirmposition stattfindet. Tut nichts, kann aber von
	 * Subklassen überschrieben werden. Um Bildschirmkoordinaten in Bildkoordinaten umzuwandeln siehe
	 * {@link #screenToImagePosition(float, float)}.
	 * 
	 * @param clickX
	 * @param clickY
	 * @return true, falls das Event behandelt wurde.
	 */
	protected boolean onClickPosition(float clickX, float clickY) {
		return false;
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// OVERLAY ICONS
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Fügt ein OverlayIcon der Liste hinzu, sodass dieses in onDraw() gezeichnet wird. (Inklusive update())
	 */
	public void attachOverlayIcon(OverlayIcon icon) {
		overlayIconList.add(icon);
		update();
	}
	
	/**
	 * Entfernt ein OverlayIcon aus der Liste, sodass dieses nicht mehr gezeichnet wird. (Inklusive update())
	 */
	public void detachOverlayIcon(OverlayIcon icon) {
		overlayIconList.remove(icon);
		update();
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// TOUCH AND CLICK EVENT HANDLING
	// ////////////////////////////////////////////////////////////////////////
	
	// To allow zooming with keyboard (e.g. emulator)
	@Override
	public boolean onKeyDown(int code, KeyEvent event) {
		if (code == KeyEvent.KEYCODE_COMMA) {
			setZoomScale(zoomScale * 2);
			return true;
		} else if (code == KeyEvent.KEYCODE_PERIOD) {
			setZoomScale(zoomScale / 2);
			return true;
		}
		return super.onKeyDown(code, event);
	}
	// ////// MAIN ONTOUCHEVENT
	
	/**
	 * Behandelt Touchgesten, primär Klickerkennung, Panning und Zooming.
	 * Kann überschrieben werden, um eigene Touchgesten zu implementieren. Soll Pan und Zoom vermieden werden, aber
	 * Klicks dennoch erkannt werden, rufe super.onTouchEvent_clickDetection(event) auf, in welche die Klickerennung
	 * ausgelagert wurde.
	 * 
	 * @return true, falls Event behandelt wurde, sonst false. (Hier: eigentlich immer true.)
	 */
	// Die Lint-Warnung "onTouchEvent should call performClick when a click is detected" wird fälschlicherweise(?)
	// angezeigt, obwohl onTouchEvent() onTouchEvent_clickDetection() aufruft, welche wiederum performClick() aufruft.
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// ////// KLICKERKENNUNG
		
		// Wenn Klick erkannt wurde, wurde das Event behandelt.
		if (onTouchEvent_clickDetection(event)) {
			return true;
		}
		
		// ////// OVERLAYICON DRAG AND DROP
		
		// Wenn Drag/Drop erkannt wurde, wurde das Event behandelt.
		boolean dragAndDropHandled = onTouchEvent_dragAndDrop(event);
		
		// ////// PANNING UND ZOOMING
		
		// (auch bei true von onTouchEvent_dragAndDrop() aufrufen, weil manche panZoom-Events ausgeführt werden müssen)
		onTouchEvent_panZoom(event, dragAndDropHandled);
		
		return true;
	}
	
	
	// ////// PANNING AND ZOOMING
	
	/**
	 * Führt das Panning und Zooming durch. Wird als Teil von onTouchEvent aufgerufen.
	 * 
	 * @return true. Auch Overrides sollten stets true zurückgeben (sonst kommen folgende Touch-Events nicht mehr an).
	 */
	public boolean onTouchEvent_panZoom(MotionEvent event, boolean dragAndDropHandled) {
		// Was für ein MotionEvent wurde detektiert?
		int action = event.getActionMasked();
		
		// Behandlung des Panning
		switch (action) {
			case MotionEvent.ACTION_DOWN: {
				// Erste Berührung des Touchscreens (mit einem Finger)
				
				// Auch wenn wir gerade einen laufenden Drag-Vorgang haben, Pan-Start behandeln, da der
				// Drag-Vorgang abgebrochen werden könnte.
				
				// Position des ersten Pointers (Fingers) ermitteln
				final int pointerIndex = event.getActionIndex();
				final float x = event.getX(pointerIndex);
				final float y = event.getY(pointerIndex);
				
				// Position der Pan-Bewegung merken
				panLastTouchX = x;
				panLastTouchY = y;
				
				// Pointer-ID merken
				panActivePointerId = event.getPointerId(0);
				break;
			}
			
			case MotionEvent.ACTION_MOVE: {
				// Bewegung der Finger während Berührung
				
				// Nur behandeln, wenn wir einen aktiven Pan-Finger haben
				if (panActivePointerId == -1)
					return true;
				
				// Position des aktuellen Pointers ermitteln
				final int pointerIndex = event.findPointerIndex(panActivePointerId);
				float x = event.getX(pointerIndex);
				float y = event.getY(pointerIndex);
				
				// Führe Panning nur dann durch, wenn das Event sicher kein Klick ist. Das verhindert kleine
				// Bewegungen des Bildes beim Klicken.
				if (touchCouldBeClick) {
					return true;
				}
				
				// Wenn wir gerade Zoomen, richtet sich das Panning nach dem ScaleFocus
				if (panLastTouchIsScaleFocus) {
					if (!SGD.isInProgress()) {
						// Zoomvorgang beendet, also wieder nach einzelnem Finger pannen
						panLastTouchIsScaleFocus = false;
						panLastTouchX = x;
						panLastTouchY = y;
						break;
					}
					else {
						x = SGD.getFocusX();
						y = SGD.getFocusY();
					}
				}
				
				// Bewegungsdistanz errechnen
				final float dx = x - panLastTouchX;
				final float dy = y - panLastTouchY;
				
				// Aktuelle Position für nächstes move-Event merken
				panLastTouchX = x;
				panLastTouchY = y;
				
				// Nur, falls wir gerade keinen laufenden Drag-Vorgang haben, das Panning tatsächlich ändern
				if (!currentlyDragging) {
					// Jetzt pannen wir "offiziell" (ab jetzt keine Drag-Vorgänge mehr starten)
					panActive = true;
					
					// Falls wir noch keine Pan-Werte haben (?) initialisiere sie mit 0
					if (Float.isNaN(panCenterX))
						panCenterX = 0;
					if (Float.isNaN(panCenterY))
						panCenterY = 0;
					
					// Panning Geschwindigkeit wird an die aktuelle Skalierung angepasst
					panCenterX -= dx / zoomScale;
					panCenterY -= dy / zoomScale;
					
					// Event auslösen, dass Pan/Zoom durch Touchevent verändert wurden
					onTouchPanZoomChange();
				}
				
				break;
			}
			
			case MotionEvent.ACTION_UP:
				// Der letzte Finger wird gehoben
				panActivePointerId = -1;
				panActive = false;
				break;
			
			case MotionEvent.ACTION_POINTER_DOWN:
				// Ein weiterer Finger berührt das Touchscreen
				break;
			
			case MotionEvent.ACTION_POINTER_UP: {
				// Ein Finger verlässt das Touchscreen, aber es sind noch Finger auf dem Touchscreen.
				
				// Welcher Finger wurde entfernt?
				final int pointerIndex = event.getActionIndex();
				final int pointerId = event.getPointerId(pointerIndex);
				
				if (pointerId == panActivePointerId) {
					// der "aktive" Finger wurde entfernt, wähle neuen und passe gemerkte Koordinaten an
					final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
					panLastTouchX = event.getX(newPointerIndex);
					panLastTouchY = event.getY(newPointerIndex);
					panActivePointerId = event.getPointerId(newPointerIndex);
				}
				break;
			}
		}
		
		// Behandlung vom Zoomen
		if (!currentlyDragging) {
			SGD.onTouchEvent(event);
		}
		
		// Position/Skalierung der ImageView anpassen
		update();
		
		return true;
	}
	
	// ////// SCALELISTENER: implementiert die onScale-Methode des SGD und kümmert sich damit um den Zoom.
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			// Wenn wir scalen, haben wir definitiv kein Klick-Event mehr.
			touchCouldBeClick = false;
			
			// Damit das Panning während des Zooms sich nicht nur auf einen
			// Finger beschränkt, panne hier anhand des Focus-Punktes.
			if (!panLastTouchIsScaleFocus) {
				panLastTouchX = detector.getFocusX();
				panLastTouchY = detector.getFocusY();
				panLastTouchIsScaleFocus = true;
			}
			
			// Zoom-Level aktualisieren
			float oldScale = zoomScale;
			float scaleFactor = detector.getScaleFactor();
			zoomScale *= scaleFactor;
			
			// Zoom-Level darf Minimum und Maximum nicht unter-/überschreiten
			if (zoomScale < minZoomScale || zoomScale > maxZoomScale) {
				zoomScale = Math.max(minZoomScale, Math.min(zoomScale, maxZoomScale));
				
				// scaleFactor wird unten noch mal benötigt, also anpassen
				scaleFactor = zoomScale / oldScale;
			}
			
			// Um den Zoom-Focus (der Mittelpunkt zwischen den Fingern) beizubehalten, ist eine
			// zusätzliche Translation erforderlich.
			// (Vermeide Pivot-Parameter von Matrix.setScale(), da dies mit der Translation durchs
			// Panning kollidiert... so ist es einfacher.)
			
			// Berechne Fokus-Koordinaten relativ zum Pan-Center und zur Zoomscale
			float focusX = (detector.getFocusX() - getWidth() / 2) / zoomScale;
			float focusY = (detector.getFocusY() - getHeight() / 2) / zoomScale;
			
			// Durch Zoom wird Focus verschoben, hierdurch wird die Verschiebung rückgängig gemacht.
			float dx = focusX * (1 - scaleFactor);
			float dy = focusY * (1 - scaleFactor);
			
			if (Float.isNaN(panCenterX))
				panCenterX = 0;
			if (Float.isNaN(panCenterY))
				panCenterY = 0;
			
			// Verschiebe das Panning
			panCenterX -= dx;
			panCenterY -= dy;
			
			// SampleSize neuberechnen
			sampleSize = calculateSampleSize(zoomScale);
			
			// Event auslösen, dass Pan/Zoom durch Touchevent verändert wurden
			onTouchPanZoomChange();
			
			return true;
		}
	}
	
	
	// ////// CLICK DETECTION
	
	/**
	 * Führt die Klickerkennung durch. Wird von onTouchEvent aufgerufen, welches nur dann weitermacht, wenn hier false
	 * zurückgegeben wird (d.h. wenn kein Klick detektiert wurde).
	 * Die Klickerkennung wurde ausgelagert, damit Subklassen onTouchEvent überschreiben und damit das Panning
	 * deaktivieren, aber dennoch per Aufruf von super.onTouchEvent_clickDetection() Klicks erkennen lassen können.
	 */
	public boolean onTouchEvent_clickDetection(MotionEvent event) {
		// Was für ein MotionEvent wurde detektiert?
		int action = event.getActionMasked();
		
		// Falls mehrere Finger das Touchscreen berühren, kann es kein Klick sein.
		if (event.getPointerCount() > 1) {
			touchCouldBeClick = false;
			return false;
		}
		
		// Position des Fingers ermitteln
		// final int pointerIndex = event.getActionIndex();
		final float x = event.getX();
		final float y = event.getY();
		
		// Behandlung des Panning
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				// Erste Berührung des Touchscreens: Speichere Startposition.
				// (Falls wir uns zu weit davon wegbewegen, wollen wir keinen Klick auslösen.)
				touchCouldBeClick = true;
				touchStartX = x;
				touchStartY = y;
				break;
			
			case MotionEvent.ACTION_MOVE:
				// Bewegung der Finger während Berührung
				
				if (touchCouldBeClick) {
					// Falls wir uns zu weit vom Startpunkt der Geste wegbewegen, ist es kein Klick mehr.
					if (Math.abs(x - touchStartX) > TOUCH_CLICK_TOLERANCE || Math.abs(y - touchStartY) > TOUCH_CLICK_TOLERANCE) {
						touchCouldBeClick = false;
					}
				}
				break;
			
			case MotionEvent.ACTION_UP:
				// Der letzte Finger wird gehoben
				
				// Klick auslösen, falls das Event als Klick interpretiert wurde (d.h. nur ein Finger und vom
				// Startpunkt nicht weiter als TOUCH_CLICK_TOLERANCE Pixel entfernt).
				if (touchCouldBeClick) {
					touchCouldBeClick = false;
					performClick();
					return true;
				}
				break;
		}
		
		// Event wurde nicht behandelt, reiche es an (den Rest von) onTouchEvent weiter.
		return false;
	}
	
	@Override
	public boolean performClick() {
		Log.d("LIV/performClick", "Click on screen position " + touchStartX + ", " + touchStartY + " detected!");
		
		// Prüfe, ob ein OverlayIcon angeklickt wurde und führe gegebenenfalls dessen onClick-Methode aus.
		for (OverlayIcon icon : overlayIconList) {
			// Icon überspringen, falls es keine Hitbox hat
			if (icon.getTouchHitbox() == null)
				continue;
			
			// Bildschirmposition des Icons (ohne Offset) errechnen
			PointF screenPoint = imageToScreenPosition(icon.getImagePositionX(), icon.getImagePositionY());
			
			// Position des Klicks relativ zum Icon
			int relativeX = (int) (touchStartX - screenPoint.x);
			int relativeY = (int) (touchStartY - screenPoint.y);
			
			// war der Klick innerhalb der Icon-Hitbox?
			if (icon.getTouchHitbox().contains(relativeX, relativeY)) {
				// Falls eine Drag-Bewegung gestartet wurde, muss diese abgebrochen werden.
				if (icon.getDragPointerID() > -1) {
					icon.onDragUp(touchStartX, touchStartY);
				}
				
				// Führe onClick-Event aus. Return, falls onClick das Event behandelt hat.
				if (icon.onClick(touchStartX, touchStartY) == true)
					return true;
			}
		}
		
		// onClickPosition-Event auslösen. Falls es true zurückgibt, wurde das Event behandelt...
		if (onClickPosition(touchStartX, touchStartY) == true)
			return true;
		
		// ... ansonsten Standard-Handler ausführen, der dann andere onClick-Events triggert.
		return super.performClick();
	}
	
	
	// ////// OVERLAY ICON DRAG AND DROP
	
	/**
	 * Führt die Erkennung von Drag- und Drop-Events von OverlayIcons durch. Wird von onTouchEvent aufgerufen, welches
	 * nur dann weitermacht, wenn hier false zurückgegeben wird (d.h. wenn für den aktuellen Finger kein Drag and Drop
	 * detektiert wurde).
	 */
	public boolean onTouchEvent_dragAndDrop(MotionEvent event) {
		// Was für ein MotionEvent wurde detektiert?
		int action = event.getActionMasked();
		
		// Position des ersten Pointers (Fingers) ermitteln
		final int pointerIndex = event.getActionIndex();
		final float x = event.getX(pointerIndex);
		final float y = event.getY(pointerIndex);
		final int pointerID = event.getPointerId(pointerIndex);
		
		// Behandlung des Drag and Drops
		switch (action) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN:
				// Ein (erster oder weiterer) Finger berührt das Touchscreen
				
				// Drag and Drop nur dann, wenn wir gerade nicht mitten beim Panning oder Zooming sind.
				if (panActive) {
					return false;
				}
				
				// Prüfe, ob ein OverlayIcon berührt wurde und trigger ggf. dessen onDragDown-Event.
				for (OverlayIcon icon : overlayIconList) {
					// Icon überspringen, falls es keine Hitbox hat
					if (icon.getTouchHitbox() == null)
						continue;
					
					// Bildschirmposition des Icons (ohne Offset) errechnen
					PointF screenPoint = imageToScreenPosition(icon.getImagePositionX(), icon.getImagePositionY());
					
					// Position des Klicks relativ zum Icon
					int relativeX = (int) (x - screenPoint.x);
					int relativeY = (int) (y - screenPoint.y);
					
					// war der Klick innerhalb der Icon-Hitbox?
					if (icon.getTouchHitbox().contains(relativeX, relativeY)) {
						// Ja, führe onDragDown-Event aus. Return, falls onClick das Event behandelt hat.
						if (icon.onDragDown(pointerID, x, y) == true) {
							// Merken, dass ein Drag-Vorgang läuft (Extra-Test, da onDragDown() z.B. auch laufende
							// Drag-Vorgänge abbrechen könnte (gilt als behandeltes Event))
							if (icon.getDragPointerID() != -1) {
								currentlyDragging = true;
							}
							return true;
						}
					}
				}
				break;
			
			case MotionEvent.ACTION_MOVE:
				// Bewegung eines Fingers während Berührung
				
				// Drag and Drop nur dann, wenn das Event definitiv kein Klick ist.
				if (touchCouldBeClick) {
					return false;
				}
				
				// Falls gerade gar kein Drag-Vorgang läuft, können wir hier auch abbrechen
				if (!currentlyDragging) {
					return false;
				}
				
				boolean handledDragAndDrop = false;
				
				// Prüfe für jeden Pointer (=Finger)...
				for (int i = 0; i < event.getPointerCount(); i++) {
					// ... und für jedes OverlayIcon...
					for (OverlayIcon icon : overlayIconList) {
						// ... ob der Finger das OverlayIcon draggt...
						// (Falls nicht gedraggt, gibt die Methode -1 zurück)
						if (icon.getDragPointerID() == event.getPointerId(i)) {
							// ... falls ja, führe onDragMove-Event aus
							if (icon.onDragMove(event.getX(i), event.getY(i)) == true)
								handledDragAndDrop = true;
						}
					}
				}
				// true zurückgeben, falls wir mindestens ein Drag- and Drop-Event gehandlet haben
				return handledDragAndDrop;
				
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP:
				// Ein Finger verlässt das Touchscreen
				
				// Falls gerade gar kein Drag-Vorgang läuft, können wir hier auch abbrechen
				if (!currentlyDragging) {
					return false;
				}
				
				// Counter, wie viele Icons (im Fall von Multitouch) noch gedraggt werden.
				int stillDraggedCount = 0;
				boolean returnvalue = false;
				
				// Prüfe, ob der Finger eines der OverlayIcons draggt und trigger ggf. dessen onDragUp-Event.
				for (OverlayIcon icon : overlayIconList) {
					// Wird das Icon gerade gedraggt?
					if (icon.getDragPointerID() != -1) {
						stillDraggedCount++;
						
						// Wird es vom aktuellen Finger gedraggt?
						if (icon.getDragPointerID() == pointerID) {
							// Ja, führe onDragUp-Event aus. (Ignoriere return value)
							icon.onDragUp(x, y);
							
							stillDraggedCount--;
							returnvalue = true;
						}
					}
				}
				
				// Falls nun kein Icon mehr gedraggt wird, Variable zurücksetzen
				if (stillDraggedCount == 0) {
					currentlyDragging = false;
				}
				
				if (returnvalue == true)
					return true;
				
				break;
		}
		
		// Event wurde nicht behandelt, reiche es an (den Rest von) onTouchEvent weiter.
		return false;
	}
	
	/**
	 * Gibt true zurück, falls gerade ein OverlayIcon gedraggt wird.
	 */
	public boolean isCurrentlyDragging() {
		return currentlyDragging;
	}
	
	/**
	 * Bricht alle laufenden Dragvorgänge ab.
	 */
	public void cancelAllDragging() {
		for (OverlayIcon icon : overlayIconList) {
			// Wird das Icon gerade gedraggt?
			if (icon.getDragPointerID() != -1) {
				// Drag-Vorgang abbrechen
				icon.onDragUp(Float.NaN, Float.NaN);
			}
		}
		
		currentlyDragging = false;
	}
	
	
	// ////////////////////////////////////////////////////////////////////////
	// //////////// DARSTELLUNG DES BILDES
	// ////////////////////////////////////////////////////////////////////////
	
	/**
	 * Aktualisiert die Darstellung. Wird aufgerufen, wenn Pan-Position oder Zoomlevel verändert werden.
	 */
	public void update() {
		// Begrenze das Panning, so dass man das Bild nicht beliebig weit aus der Bildfläche schieben kann.
		
		if (imageWidth > 0 && imageHeight > 0) {
			// Panning so begrenzen, dass PanCenter nicht die Bildgrenzen verlassen kann. (Simple, huh?)
			
			// Linke Begrenzung
			if (panCenterX < 0)
				panCenterX = 0;
			// Rechte Begrenzung
			else if (panCenterX >= imageWidth)
				panCenterX = imageWidth;
			
			// Obere Begrenzung
			if (panCenterY < 0)
				panCenterY = 0;
			// Untere Begrenzung
			else if (panCenterY >= imageHeight)
				panCenterY = imageHeight;
			
			// Log.d("LIV/update", "Pan center: " + panCenterX + "/" + panCenterY + ", zoom: " + zoomScale);
		}
		
		// View neu zeichnen lassen (onDraw)
		// TODO ausprobieren, ob mehrere invalidate-Aufrufe in Folge onDraw auch mehrfach aufrufen
		// .... (da manche Methoden implizit mehrmals update() aufrufen :S)
		this.invalidate();
	}
	
	/**
	 * Returns true if everything is ready to call onDraw (pan set, getWidth/Height return non-zero values, etc.).
	 * Check this if you override onDraw!
	 */
	protected boolean isReadyToDraw() {
		if (Float.isNaN(panCenterX) || Float.isNaN(panCenterY) || getWidth() == 0 || getHeight() == 0) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Zeichnet das Bild, gegebenenfalls in Einzelteilen, sowie die OverlayIcons.
	 * Siehe auch {@link #onDraw_cachedImage(Canvas)}, {@link #onDraw_staticBitmap(Canvas)},
	 * {@link #onDraw_overlayIcons(Canvas)}.
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		if (!isReadyToDraw()) {
			return;
		}
		
		// ZoomScale darf nicht 0 sein -- wird es eigentlich auch nie, aber für den Fall der Fälle...
		if (zoomScale == 0) {
			zoomScale = (float) 1.0 / 256; // dürfte klein genug sein :P
		}
		
		canvas.save();
		
		// Prüfe, ob wir ein CachedImage oder ein statisches Bitmap verwenden
		if (cachedImage != null) {
			onDraw_cachedImage(canvas);
		}
		else {
			if (!onDraw_staticBitmap(canvas)) {
				// Fallback (setBackgroundAlpha funktioniert hiermit nicht... naja.)
				super.onDraw(canvas);
			}
		}
		
		canvas.restore();
		
		// Overlay-Icons zeichnen
		canvas.save();
		onDraw_overlayIcons(canvas);
		canvas.restore();
	}
	
	/**
	 * Übernimmt den Teil von {@link #onDraw(Canvas)}, der ein gecachtes Bild (in Tiles) anzeigt.
	 */
	protected void onDraw_cachedImage(Canvas canvas) {
		// Effektive Skalierung berechnen (Skalierung, die nach dem Sampling noch erforderlich ist)
		float effectiveScale = sampleSize * zoomScale;
		
		// Viewport berechnen: sichtbarer Bildausschnitt (relativ zum gesampelten Bild; mit Rand)
		int viewportWidth = (int) (getWidth() / effectiveScale);
		int viewportHeight = (int) (getHeight() / effectiveScale);
		
		// Viewportgrenzen: PanCenter bei aktueller SampleSize - 1/2 * Viewport, weil PanCenter
		int viewportLeft = (int) (panCenterX / sampleSize) - viewportWidth / 2;
		int viewportTop = (int) (panCenterY / sampleSize) - viewportHeight / 2;
		int viewportRight = viewportLeft + viewportWidth;
		int viewportBottom = viewportTop + viewportHeight;
		
		// Log.d("LIV/onDraw_cachedImage", "sample: " + sampleSize + ", viewport: " + viewportWidth + "x" +
		// viewportHeight + ", (l,t,r,b) = ("
		// + viewportLeft + "," + viewportTop + "," + viewportRight + "," + viewportBottom + ")");
		
		// Startkoordinaten für die Zeichnen-Schleife
		// (Linksoberstes Tile beginnt i.A. weiter links oben als der Viewport)
		int startX = viewportLeft - viewportLeft % CachedImage.TILESIZE;
		int startY = viewportTop - viewportTop % CachedImage.TILESIZE;
		
		// Nicht versuchen, etwas links/oben vom Bild zu zeichnen
		if (startX < 0)
			startX = 0;
		if (startY < 0)
			startY = 0;
		
		// Zeichenbereich skalieren und verschieben (effektive Skalierung nach dem Sampling)
		canvas.scale(effectiveScale, effectiveScale);
		canvas.translate(-viewportLeft, -viewportTop);
		
		// Zeilenweise Tiles zeichnen, bis am Viewportrand oder Bildrand angekommen
		for (int y = startY; y < viewportBottom && y < imageHeight / sampleSize; y += CachedImage.TILESIZE) {
			for (int x = startX; x < viewportRight && x < imageWidth / sampleSize; x += CachedImage.TILESIZE) {
				// Unsere Koordinaten sind abhängig vom Sampling. Das gesuchte Tile beginnt also nicht
				// bei (x,y) sondern bei samplingLevel*(x,y), wird aber an (x,y) gezeichnet.
				Bitmap bm = cachedImage.getTileBitmap(sampleSize * x, sampleSize * y, sampleSize);
				
				// Log.d("LIV/onDraw_cachedImage", "Drawing tile " + getCacheKey(sampleSize * x, sampleSize * y,
				// sampleSize)
				// + (bm == null ? " ... null" : (" at " + x + "," + y)));
				
				// Tile zeichnen, falls es bereits existiert (also im Cache gefunden wurde)
				if (bm != null) {
					canvas.drawBitmap(bm, x, y, bgAlphaPaint);
				}
			}
		}
	}
	
	/**
	 * Übernimmt den Teil von {@link #onDraw(Canvas)}, der ein statisches (nicht gecachtes) Bild anzeigt.
	 * 
	 * @param canvas
	 * @return False, falls auch kein staticBitmap vorhanden ist... Benutze super.onDraw().
	 */
	protected boolean onDraw_staticBitmap(Canvas canvas) {
		// Viewport berechnen: sichtbarer Bildausschnitt (relativ zum Bild)
		int viewportWidth = (int) (getWidth() / zoomScale);
		int viewportHeight = (int) (getHeight() / zoomScale);
		
		// Viewportgrenzen: PanCenter - 1/2 * Viewport
		int viewportLeft = (int) panCenterX - viewportWidth / 2;
		int viewportTop = (int) panCenterY - viewportHeight / 2;
		
		// Log.d("LIV/onDraw_staticBitmap", "width, height: " + getWidth() + "/" + getHeight());
		
		// Skalieren und verschieben
		canvas.scale(zoomScale, zoomScale);
		canvas.translate(-viewportLeft, -viewportTop);
		
		if (staticBitmap != null) {
			// Bitmap statisch anzeigen
			canvas.drawBitmap(staticBitmap, 0, 0, bgAlphaPaint);
		}
		else {
			return false;
		}
		return true;
	}
	
	/**
	 * Übernimmt den Teil von {@link #onDraw(Canvas)}, der die OverlayIcons zeichnet.
	 */
	protected void onDraw_overlayIcons(Canvas canvas) {
		// Nichts tun, falls keine Overlay Icons vorhanden
		if (overlayIconList.isEmpty())
			return;
		
		// Bildursprung relativ zu Bildschirmkoordinaten berechnen
		float imageOriginX = -panCenterX * zoomScale + getWidth() / 2;
		float imageOriginY = -panCenterY * zoomScale + getHeight() / 2;
		
		// Log.d("LIV/onDraw_overlayIcons", "width, height: " + getWidth() + "/" + getHeight() + ", image origin " +
		// imageOriginX + "/" + imageOriginY);
		
		// das Koordinatensystem des Canvas entspricht nun dem des Bildes
		canvas.translate(imageOriginX, imageOriginY);
		
		for (OverlayIcon icon : overlayIconList) {
			// save und restore, um alle Icons einzeln zu verschieben
			canvas.save();
			
			// Translation für Icon berechnen
			float translateX = icon.getImagePositionX() * zoomScale + icon.getImageOffsetX();
			float translateY = icon.getImagePositionY() * zoomScale + icon.getImageOffsetY();
			
			// Log.d("LIV/onDraw_overlayIcons", "image pos " + icon.getImagePositionX() + "/" + icon.getImagePositionY()
			// + ", offset " + icon.getImageOffsetX() + "/" + icon.getImageOffsetY() + ", zoomscale " + zoomScale +
			// ", translate " + translateX + "/" + translateY);
			
			// Canvas verschieben und Icon zeichnen
			canvas.translate(translateX, translateY);
			icon.draw(canvas);
			
			canvas.restore();
		}
	}
	
}
