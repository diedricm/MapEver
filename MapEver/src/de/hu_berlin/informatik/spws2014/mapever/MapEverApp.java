/* Copyright (C) 2014,2015 Björn Stelter
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

package de.hu_berlin.informatik.spws2014.mapever;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;

// WAS IST DAS?
// "Application" sorgt für einen globalen (d.h. app-weiten) Kontext, in dem wir
// beispielsweise unser GPS-Objekt speichern können, dieses bereits in der
// Start-Activity initialisieren und dann später in Navigation verwenden können.
// Wir erzeugen also eine Erweiterung dieser Klasse um eigene Objekte zu speichern.
// Die Referenz auf diesen App-Kontext erhalten wir in einer Activity mittels:
// MapEverApp appContext = ((MapEverApp)getApplicationContext());
//
// SIEHE AUCH: http://stackoverflow.com/a/708317

public class MapEverApp extends Application {
	
	// Basisverzeichnis, in dem unsere Dateien zu finden sind
	public static final String BASE_DIR_DIRNAME = "mapever";
	public static final String BASE_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + BASE_DIR_DIRNAME;
	
	public static final String TEMP_IMAGE_FILENAME = "temp";
	public static final String THUMB_EXT = "_thumb";
	
	public MapEverApp() {
		// ////// INITIALIZE APP
		
		// Erstelle App-Verzeichnis, falls dieses noch nicht existiert.
		try {
			initializeBaseDir();
		}
		catch (IOException e) {
			Log.e("MapEverApp", "Failed to initialize base directory!");
			e.printStackTrace();
		}
	}
	
	private void initializeBaseDir() throws IOException {
		File baseDir = new File(BASE_DIR);
		
		// Erstelle App-Verzeichnis, falls dieses noch nicht existiert.
		if (!baseDir.exists()) {
			Log.d("MapEverApp", "Base directory does not exist, creating new one at '" + BASE_DIR + "'");
			
			if (!baseDir.mkdirs()) {
				throw new IOException("mkdirs() returned false");
			}

			File nomediaFile = new File(BASE_DIR + File.separator + ".nomedia");
			nomediaFile.createNewFile();
		}
	}
	
	/**
	 * Wandelt einen relativen Pfad in einen absoluten Pfad um. Die Pfade sind dabei relativ zum Appverzeichnis
	 * (externalStorageDirectory + "/mapever/") anzugeben.
	 * 
	 * @param relativeFilename
	 * @return
	 */
	public static String getAbsoluteFilePath(String relativeFilename) {
		return BASE_DIR + File.separator + relativeFilename;
	}
	
	/**
	 * Gibt true zurück, falls Debug-Mode aktiviert ist.
	 */
	public static boolean isDebugModeEnabled(Context context) {
		return Settings.getPreference_debugMode(context);
	}
}
