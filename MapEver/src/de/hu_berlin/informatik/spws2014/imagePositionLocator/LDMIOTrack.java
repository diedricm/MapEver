/* Copyright (C) 2014,2015  Maximilian Diedrich
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

package de.hu_berlin.informatik.spws2014.imagePositionLocator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

/**
 * Main backend for LDM. 
 * Does not provide realtime saving.
 * Saves only when save is called.
 */
public class LDMIOTrack implements ILDMIOHandler {
	String filename;
	
	//Denotes the highest supported .track version
	int protVersionNumber = 3;
	
	ArrayList<GpsPoint> gpspath;
	ArrayList<Marker> markers;
	long time;
	
	/**
	 * Resets all the markers GpsPoints to the timely closest GpsPoints in track.
	 */
	private void repairBrockenMarkers() {
		for (Marker m : markers) {
			for (int index = 0; index < gpspath.size(); index++) {
				if(gpspath.get(index).time > m.time) {
					m.realpoint = gpspath.get(index);
					break;
				}
			}
		}
	}
	
	/**
	 * Reads track format.
	 */
	@SuppressWarnings("unchecked")
	private boolean readTrackFile(ObjectInputStream ois) throws IOException {
		protVersionNumber = ois.readInt();
		try {
			switch (protVersionNumber) {
			case 3:
				gpspath = (ArrayList<GpsPoint>) ois.readObject();
				markers = (ArrayList<Marker>) ois.readObject();
				repairBrockenMarkers();
				break;
			default:
				System.err.println("Unknown file version number: "
						+ protVersionNumber
						+ " encountered while reading DataManager.");
				return false;
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public LDMIOTrack(String pathToFile) throws IOException {
		filename = pathToFile;
		File dbgTrackfile = new File(pathToFile);
		boolean isFileValid = dbgTrackfile.exists();
		
		if (isFileValid) {
			FileInputStream fis = new FileInputStream(dbgTrackfile);
			ObjectInputStream ois = new ObjectInputStream(fis);
			readTrackFile(ois);
			ois.close();
			fis.close();
		}
		if (!isFileValid) {
			gpspath = new ArrayList<GpsPoint>();
			markers = new ArrayList<Marker>();
		}
	}
	
	@Override
	public ArrayList<Marker> getAllMarkers() {
		return markers;
	}

	@Override
	public Marker getMarker(Point2D imgpoint) {
		for (Marker m : markers) {
			if (m.imgpoint.equals(imgpoint))
				return m;
		}
		return null;
	}

	@Override
	public Marker getMarker(GpsPoint realpoint) {
		for (Marker m : markers) {
			if (m.realpoint.equals(realpoint))
				return m;
		}
		return null;
	}
	
	@Override
	public Marker getLastMarker() {
		if (markers.size() == 0)
			return null;
		else
			return markers.get(markers.size()-1);
	}

	@Override
	public boolean removeMarker(Point2D imgpoint) {
		Marker m = getMarker(imgpoint);
		if (m == null)
			return false;
		markers.remove(m);
		return true;
	}

	@Override
	public boolean removeMarker(GpsPoint realpoint) {
		Marker m = getMarker(realpoint);
		if (m == null)
			return false;
		markers.remove(m);
		return true;
	}
	
	@Override
	public ArrayList<GpsPoint> getAllGpsPoints() {
		return gpspath;
	}
	
	@Override
	public GpsPoint getLastGpsPoint() {
		if (gpspath.size() == 0)
			return null;
		else
			return gpspath.get(gpspath.size()-1);
	}

	@Override
	public boolean removeMarker(Marker m) {
		return markers.remove(m);
	}

	@Override
	public void removeAllMarkers() {
		markers = new ArrayList<Marker>();
	}

	@Override
	public boolean removeGpsPoint(GpsPoint p) {
		return gpspath.remove(p);
	}

	@Override
	public void removeAllGpsPoints() {
		gpspath = new ArrayList<GpsPoint>();
	}

	@Override
	public void addMarker(Marker m) {
		markers.add(m);
	}

	@Override
	public void addGpsPoint(GpsPoint p) {
		gpspath.add(p);
	}
	
	@Override
	public void setLastGpsPointTime(long unixTime) {
		time = unixTime;
	}

	@Override
	public long getLastGpsPointTime() {
		return this.time;
	}
	
	/**
	 * Prints two csv tables to ps.
	 * The first represents all the known GpsPoints
	 * while the second consists of all Markers.
	 * @param ps
	 */
	public void printValuesAsCSV(PrintStream ps) {
		ps.println("type,name,latitude,longitude");
		for (GpsPoint p : getAllGpsPoints())
			ps.println("P, " + p.time + ", " + p.latitude + ", " + p.longitude);
		ps.println("name,name2,latitude,longitude");
		for (Marker m : getAllMarkers())
			ps.println(m.imgpoint.x + ", " + m.imgpoint.y + ", "
					+ m.realpoint.latitude + ", " + m.realpoint.longitude);
	}
	
	/**
	 * Writes into track format.
	 */
	private void writeTrackFile(ObjectOutputStream oos) throws IOException {
		oos.writeInt(protVersionNumber);
		oos.writeObject(gpspath);
		oos.writeObject(markers);
		oos.writeLong(time);
	}
	
	@Override
	public void save() {
		File dbgTrackfile = new File(filename);
		try {
			FileOutputStream fis = new FileOutputStream(dbgTrackfile);
			ObjectOutputStream oos = new ObjectOutputStream(fis);
			writeTrackFile(oos);
			oos.close();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
