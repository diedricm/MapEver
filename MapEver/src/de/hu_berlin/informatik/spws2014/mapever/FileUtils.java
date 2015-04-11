/* Copyright (C) 2014,2015 Florian Kempf, Björn Stelter
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
	
	public static void copyStreamToStream(InputStream srcStream, OutputStream destStream) throws IOException {
		byte[] buffer = new byte[4096];
		int n;
		
		while ((n = srcStream.read(buffer)) > 0) {
			destStream.write(buffer, 0, n);
		}
	}
	
	public static void copyStreamToFile(InputStream srcStream, File destFile) throws IOException {
		// OutputStream für Zieldatei erzeugen
		OutputStream destStream = new FileOutputStream(destFile);
		
		// Kopiere Daten von InputStream zu OutputStream
		copyStreamToStream(srcStream, destStream);
		
		// OutputStream schließen
		destStream.close();
	}
	
	public static void copyFileToFile(File srcFile, File destFile) throws IOException {
		// Streams für Quell- und für Zieldatei erzeugen
		InputStream srcStream = new FileInputStream(srcFile);
		OutputStream destStream = new FileOutputStream(destFile);
		
		// Kopiere Daten von InputStream zu OutputStream
		copyStreamToStream(srcStream, destStream);
		
		// Streams schließen
		srcStream.close();
		destStream.close();
	}
	
}
