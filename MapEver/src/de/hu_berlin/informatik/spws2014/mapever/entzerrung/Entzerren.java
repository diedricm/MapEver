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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import de.hu_berlin.informatik.spws2014.mapever.BaseActivity;
import de.hu_berlin.informatik.spws2014.mapever.FileUtils;
import de.hu_berlin.informatik.spws2014.mapever.MapEverApp;
import de.hu_berlin.informatik.spws2014.mapever.R;
import de.hu_berlin.informatik.spws2014.mapever.navigation.Navigation;

public class Entzerren extends BaseActivity {
	
	// savedInstanceState constants
	private static final String SHOWHELPSAVED = "SHOWHELPSAVED";
	private static final String IMAGEENTZERRT = "IMAGEENTZERRT";
	
	// other constants
	private static final String INPUTFILENAME = MapEverApp.TEMP_IMAGE_FILENAME;
	private static final String INPUTFILENAMEBAK = INPUTFILENAME + "_bak";
	
	// View references
	private EntzerrungsView entzerrungsView;
	private FrameLayout layoutFrame;
	
	// various state variables
	private boolean entzerrt = false; // ist mindestens einmal entzerrt worden?
	private boolean tutorial = false; // Quickhelp aktiv?
	private boolean loading_active = false; // Entzerrungsvorgang aktiv?
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_entzerren);
		
		// Layout aufbauen
		layoutFrame = new FrameLayout(getBaseContext());
		setContentView(layoutFrame);
		getLayoutInflater().inflate(R.layout.activity_entzerren, layoutFrame);
		
		// Referenz auf EntzerrungsView speichern
		entzerrungsView = (EntzerrungsView) findViewById(R.id.entzerrungsview);
		
		// Bild in die View laden
		loadImageFile();
		
		// Wird die Activity frisch neu erstellt oder haben wir einen gespeicherten Zustand?
		if (savedInstanceState == null) {
			// Verwende statischen Dateinamen als Eingabe
			File imageFile = new File(MapEverApp.getAbsoluteFilePath(INPUTFILENAME));
			File imageFile_bak = new File(MapEverApp.getAbsoluteFilePath(INPUTFILENAMEBAK));
			
			// Backup von der Datei erstellen, um ein Rückgängigmachen zu ermöglichen
			copy(imageFile, imageFile_bak);
		}
		else {
			// Zustandsvariablen wiederherstellen
			entzerrt = savedInstanceState.getBoolean(IMAGEENTZERRT);
			boolean _tutorial = savedInstanceState.getBoolean(SHOWHELPSAVED);
			
			if (_tutorial) {
				startQuickHelp();
			}
		}
		
		// EntzerrungsView updaten und neu zeichnen lassen
		entzerrungsView.update();
	}
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.entzerren, menu);

		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (loading_active)
			return false;
		
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
			case R.id.action_quick_help:
				// Schnellhilfe-Button
				if (tutorial) {
					endQuickHelp();
				}
				else {
					startQuickHelp();
				}
				return true;
				
			case R.id.action_show_corners:
				// Toggle showCorners
				if (entzerrungsView.isShowingCorners()) {
					entzerrungsView.showCorners(false);
				}
				else if (entzerrungsView.isImageTypeSupported()) {
					entzerrungsView.showCorners(true);
				}
				else if (entzerrungsView.isOpenCVLoadError()) {
					showErrorMessage(R.string.deskewing_opencv_not_available);
				}
				else {
					// Image type is not supported by deskewing algorithm (GIF?) so don't allow deskewing
					showErrorMessage(R.string.deskewing_imagetype_not_supported);
				}
				return true;
				
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		Log.d("Entzerren", "onSaveInstanceState...");
		
		// Save the current state
		savedInstanceState.putBoolean(SHOWHELPSAVED, tutorial);
		savedInstanceState.putBoolean(IMAGEENTZERRT, entzerrt);
		
		// Always call the superclass so it can save the view hierarchy state
		super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	public void onBackPressed() {
		if (loading_active)
			return;
		
		if (entzerrt) {
			// ersetze das Bild mit dem Backup
			File imageFile_bak = new File(MapEverApp.getAbsoluteFilePath(INPUTFILENAMEBAK));
			File imageFile = new File(MapEverApp.getAbsoluteFilePath(INPUTFILENAME));
			
			copy(imageFile_bak, imageFile);
			
			// Bild in die View laden
			loadImageFile();
			
			entzerrungsView.showCorners(true);
			entzerrungsView.calcCornersWithDetector();
			
			entzerrt = false;
			
			entzerrungsView.update();
		}
		else {
			// wenn nicht, gehe zum vorherigen Screen zurück
			// (nicht manuell die Start-Activity starten, einfach das onBackPressed gewöhnlich handlen lassen)
			super.onBackPressed();
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		Log.d("Entzerrung", "onPause...");
		
		// Gegebenenfalls laufende Drag-Operationen abbrechen
		if (entzerrungsView.isCurrentlyDragging()) {
			entzerrungsView.cancelAllDragging();
		}
	}
	
	public void onClick_EntzerrungOk(View v) {
		if (loading_active)
			return;
		
		if (tutorial) {
			endQuickHelp();
			return;
		}
		
		// wenn entzerrt werden soll...
		if (entzerrungsView.isShowingCorners()) {
			// Bildschirm sperren
			lockScreenOrientation();
			startLoadingScreen();
			
			// Entzerrung in AsyncTask starten
			new EntzerrenTask().execute();
		}
		else {
			// temp_bak löschen
			File imageFile_bak = new File(MapEverApp.getAbsoluteFilePath(INPUTFILENAMEBAK));
			if (imageFile_bak.exists()) {
				imageFile_bak.delete();
			}
			
			// Navigation mit Parameter loadmapid = -1 (== neue Karte) aufrufen
			Intent intent_nav = new Intent(this, Navigation.class);
			intent_nav.putExtra(Navigation.INTENT_LOADMAPID, -1L);
			startActivity(intent_nav);
			finish();
		}
	}
	
	
	public void startLoadingScreen() {
		if (!loading_active) {
			loading_active = true;
			getLayoutInflater().inflate(R.layout.entzerren_loading, layoutFrame);
		}
	}
	
	public void endLoadingScreen() {
		if (loading_active) {
			loading_active = false;
			layoutFrame.removeViewAt(layoutFrame.getChildCount() - 1);
		}
	}
	
	public boolean isLoadingActive() {
		return loading_active;
	}
	
	
	public void startQuickHelp() {
		if (!tutorial) {
			tutorial = true;
			getLayoutInflater().inflate(R.layout.entzerren_help, layoutFrame);
		}
	}
	
	public void endQuickHelp() {
		if (tutorial) {
			tutorial = false;
			layoutFrame.removeViewAt(layoutFrame.getChildCount() - 1);
		}
	}
	
	public boolean isInQuickHelp() {
		return tutorial;
	}
	
	public void showErrorMessage(String text) {
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
	}
	
	public void showErrorMessage(int resID) {
		showErrorMessage(getResources().getString(resID));
	}
	
	
	public void lockScreenOrientation() {
		WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		Configuration configuration = getResources().getConfiguration();
		int rotation = windowManager.getDefaultDisplay().getRotation();
		
		// Search for the natural position of the device
		if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
				(rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) ||
				configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
				(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)) {
			// Natural position is Landscape
			switch (rotation) {
				case Surface.ROTATION_0:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					break;
				case Surface.ROTATION_90:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
					break;
				case Surface.ROTATION_180:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
					break;
				case Surface.ROTATION_270:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					break;
			}
		}
		else {
			// Natural position is Portrait
			switch (rotation) {
				case Surface.ROTATION_0:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					break;
				case Surface.ROTATION_90:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					break;
				case Surface.ROTATION_180:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
					break;
				case Surface.ROTATION_270:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
					break;
			}
		}
	}
	
	public void unlockScreenOrientation()
	{
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
	}
	
	
	public void loadImageFile() {
		// Verwende statischen Dateinamen als Eingabe
		File imageFile = new File(MapEverApp.getAbsoluteFilePath(INPUTFILENAME));
		
		try {
			// Bild in die View laden
			entzerrungsView.loadImage(imageFile);
		}
		catch (FileNotFoundException e) {
			showErrorMessage(R.string.error_filenotfound);
			e.printStackTrace();
		}
		catch (OutOfMemoryError e) {
			showErrorMessage(R.string.error_outofmemory);
			e.printStackTrace();
		}
	}
	
	// TODO mit neuem optimiertem Entzerrungsalgorithmus hinfällig...?
	private boolean saveBitmap(Bitmap bitmap, String outFilename) {
		File outFile = new File(MapEverApp.getAbsoluteFilePath(outFilename));
		
		try {
			// Outputstream öffnen
			FileOutputStream outStream = new FileOutputStream(outFile);
			
			// Bitmap komprimiert in Outputstream schreiben
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
			
			outStream.close();
		}
		catch (IOException e) {
			showErrorMessage(R.string.error_io);
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	
	public void copy(File src, File dst) {
		try {
			FileUtils.copyFileToFile(src, dst);
		}
		catch (IOException e) {
			showErrorMessage(R.string.error_io);
			e.printStackTrace();
		}
	}
	
	
	private class EntzerrenTask extends AsyncTask<Void, Void, String> {
		Bitmap entzerrtesBitmap = null;
		
		@Override
		protected String doInBackground(Void... params) {
			String result = null;
			
			try {
				// TODO nach Möglichkeit ohne große Bitmaps (also streambasierte LargeImage-Version für JumbledImage)
				// .... (auch saveBitmap ersetzen)
				
				int sampleSize = 1;
				
				// Beginne mit SampleSize 1 und skalier das Bild soweit runter, bis es nicht an einem OOM scheitert.
				// TODO bessere Methode um initiale SampleSize zu bestimmen, um Zeit zu sparen?
				// .... (eig. hinfällig mit optimiertem Algorithmus)
				while (sampleSize <= 32) {
					try {
						// Punktkoordinaten als float[8] abrufen
						float coordinates[] = entzerrungsView.getPointOffsets(sampleSize);
						
						// Bitmap erzeugen
						Bitmap sampledBitmap = entzerrungsView.getSampledBitmap(sampleSize);
						
						if (sampledBitmap == null) {
							Log.e("EntzerrenTask/doInBackground", "Decoding bitmap with SampleSize " + sampleSize + " resulted in null...");
							sampleSize *= 2;
						}
						
						// Bitmap entzerren
						entzerrtesBitmap = JumbledImage.transform(sampledBitmap, coordinates);
						break;
					}
					catch (OutOfMemoryError e) {
						// Noch mal mit doppelter SampleSize (halbiere bisherige Bildgröße) versuchen
						sampleSize *= 2;
						
						if (sampleSize > 32) {
							// Exception weiterreichen
							throw e;
						}
					}
				}
				
				if (entzerrtesBitmap == null) {
					Log.e("EntzerrenTask/doInBackground", "Couldn't decode stream after " + sampleSize + " tries!");
				}
				else {
					// entzerrtes Bild abspeichern
					saveBitmap(entzerrtesBitmap, Entzerren.INPUTFILENAME);
				}
			}
			catch (OutOfMemoryError e) {
				result = getResources().getString(R.string.error_outofmemory);
				e.printStackTrace();
			}
			catch (ArrayIndexOutOfBoundsException e) {
				result = getResources().getString(R.string.deskewing_error_invalidcorners);
			}
			catch (IllegalArgumentException e) {
				result = getResources().getString(R.string.deskewing_error_invalidcorners);
			}
			catch (NullPointerException e) {
				// passiert z.B. bei unpassendem Dateiformat (GIF?)
				// TODO irgendwas weiter machen? Entzerrung für GIFs von vornherein deaktivieren?
				result = getResources().getString(R.string.deskewing_error_deskewfailure);
				Log.e("EntzerrenTask/doInBackground", "NullPointerException while trying to deskew image");
				e.printStackTrace();
			}
			
			return result;
		}
		
		@Override
		protected void onPostExecute(String result) {
			// execution of result of long time consuming operation
			if (result != null) {
				showErrorMessage(result);
			}
			else {
				// entzerrtes Bild in die View laden
				loadImageFile();
				
				entzerrungsView.showCorners(false);
				entzerrungsView.calcCornerDefaults();
				
				entzerrt = true;
			}
			
			endLoadingScreen();
			unlockScreenOrientation();
			
			entzerrungsView.update();
		}
	}
	
}
