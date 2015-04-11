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

import java.util.ArrayList;

/**
 * Interface of a LocationDataManagerInputOutputHandler
 * a plugable backend the LDM.
 */
public interface ILDMIOHandler {
	
	/**
	 * @return all known Markers. Should not be changed.
	 * If there is no data return empty list(not null).
	 */
	public ArrayList<Marker> getAllMarkers();
	
	/**
	 * @return a marker with a realpoint equal
	 * to the given one or null if none were found.
	 */
	public Marker getMarker(GpsPoint realpoint);
	
	/**
	 * @return a marker with a imgpoint equal
	 * to the given one or null if none were found.
	 */
	public Marker getMarker(Point2D imgpoint);
	
	/**
	 * @return all known GpsPoints. Should not be changed.
 	 * If there is no data return empty list(not null).
	 */
	public ArrayList<GpsPoint> getAllGpsPoints();
	
	/**
	 * Removes last recent Marker equivalent to m 
	 * @return if a occurrence of m was deleted
	 */
	public boolean removeMarker(Marker m);
	
	/**
	 * Deletes all markers
	 */
	public void removeAllMarkers();
	
	/**
	 * Calls getMarker(Point2D imgpoint), deletes the returned
	 * Marker and returns true. If there was no fitting Marker
	 * found returns false.
	 */
	boolean removeMarker(Point2D imgpoint);

	/**
	 * Calls getMarker(Point2D realpoint), deletes the returned
	 * Marker and returns true. If there was no fitting Marker
	 * found returns false.
	 */
	boolean removeMarker(GpsPoint realpoint);
	
	/**
	 * Removes last recent GpsPoint equivalent to m 
	 * @return if a occurrence of p was deleted
	 */
	public boolean removeGpsPoint(GpsPoint p);
	
	/**
	 * Deletes all GpsPoints
	 */
	public void removeAllGpsPoints();
	
	/**
	 * Saves m.
	 */
	public void addMarker(Marker m);
	
	/**
	 * Saves p.
	 */
	public void addGpsPoint(GpsPoint p);
	
	/**
	 * Sets the time when the last new GpsPoint arrived
	 */
	public void setLastGpsPointTime(long unixTime);
	
	/**
	 * @return Gets the time when the last new GpsPoint arrived
	 */
	public long getLastGpsPointTime();
	
	/**
	 * Assures data received up until now is persisted.
	 */
	public void save();

	/**
	 * @return last added Marker
	 */
	public Marker getLastMarker();

	/**
	 * return last added GpsPoint
	 * @return
	 */
	public GpsPoint getLastGpsPoint();
}
