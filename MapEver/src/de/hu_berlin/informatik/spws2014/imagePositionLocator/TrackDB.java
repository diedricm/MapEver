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
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;

/**
 * Class for listing and managing meta information about
 * track files in a directory. Saves data in a track.conf file
 * in the passed directory.
 */
public class TrackDB implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final String configFileName = "track.conf";
	private static final int versionNumber = 0;
	private static final int FIRST_IDENTIFIER = 1;
	
	//Singleton db
	public static TrackDB main = null;
	
	private File baseDir;
	private File dbFile;
	
	private HashMap<Long, TrackDBEntry> maps;
	private long lastIdentifier;
	
	/**
	 * Tries to load a TrackDB from the baseDirectory
	 * If it was already loaded than nothing happens.
	 * @return true if load was successful, false if a
	 * different db was already loaded before or if 
	 * IOExceptions happened.
	 */
	public static boolean loadDB(File baseDirectory) {
		try {
			System.err.println("Loading DB from:" + baseDirectory);
			
			if (main != null) {
				System.err.println("Tried to create second TrackDB.");
				return main.baseDir.equals(baseDirectory);
			}
			if (!baseDirectory.isDirectory()) {
				System.err.println("Provide valid directory for TrackDB.");
				return false;
			}
			
			main = new TrackDB(baseDirectory);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	@SuppressWarnings("unchecked")
	private boolean versionDependendLoad(ObjectInputStream ois) throws IOException {
		try {
			int thisVersion = ois.readInt();
			switch (thisVersion) {
			case 0:
				maps = (HashMap<Long, TrackDBEntry>) ois.readObject();
				lastIdentifier = ois.readLong();
				break;
			default:
				System.err.println("Unsupported version number: " + thisVersion + "!");
				return false;
			}
			return true;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * loads track.conf file from dbDir
	 * @throws IOException
	 */
	TrackDB(File dbDir) throws IOException {
		baseDir = dbDir;
		dbFile = new File(dbDir + File.separator + configFileName);
		boolean isFileValid = dbFile.isFile();
		
		if (isFileValid) {
			System.err.println("All OK.");
			FileInputStream fis = new FileInputStream(dbFile);
			ObjectInputStream ois = new ObjectInputStream(fis);
			isFileValid = versionDependendLoad(ois);
			ois.close();
			fis.close();
		}
		
		if (!isFileValid) {
			System.err.println("Could not read file. Creatin new trackDB in " + dbFile);
			maps = new HashMap<Long, TrackDBEntry>();
			lastIdentifier = FIRST_IDENTIFIER;
		}
	}
	
	/**
	 * Tries to delete map
	 * @return true if map was deleted. False otherwise.
	 */
	public boolean delete(TrackDBEntry map) {
		if (maps.remove(map.getIdentifier()) != null) {
			save();
			new File(baseDir + File.separator + map.getIdentifier() + ".track").delete();
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * @return The TrackDBEntry with the given identifier.
	 */
	public TrackDBEntry getMap(long identifier) {
		return maps.get(identifier);
	}
	
	/**
	 * @return The TrackLDMIO object referenced by map
	 * @throws IOException
	 */
	public LDMIOTrack getLDMIO(TrackDBEntry map) throws IOException {
		return new LDMIOTrack(baseDir + File.separator + map.getIdentifier() + ".track");
	}
	
	/**
	 * Creates and returns new entry
	 */
	public TrackDBEntry createMap() {
		Long currentIdentifier = lastIdentifier++;
		TrackDBEntry tmp = new TrackDBEntry(currentIdentifier);
		maps.put(currentIdentifier, tmp);
		save();
		return tmp;
	}
	
	/**
	 * @return all entries
	 */
	public Collection<TrackDBEntry> getAllMaps() {
		return maps.values();
	}
	
	/**
	 * save all entries
	 */
	public void save() {
		try {
			FileOutputStream fis = new FileOutputStream(dbFile);
			ObjectOutputStream oos = new ObjectOutputStream(fis);
			
			oos.writeInt(versionNumber);
			oos.writeObject(maps);
			oos.writeLong(lastIdentifier);
			
			oos.close();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
