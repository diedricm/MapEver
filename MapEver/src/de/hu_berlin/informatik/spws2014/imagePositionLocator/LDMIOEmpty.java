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
 * Empty LDMIO. Does not provide any saving or Loading capabilities.
 * Mostly used for debugging purposes.
 */
public class LDMIOEmpty implements ILDMIOHandler {
	
	ArrayList<GpsPoint> gpspath = new ArrayList<GpsPoint>();
	ArrayList<Marker> markers = new ArrayList<Marker>();
	long time;
	
	@Override
	public ArrayList<Marker> getAllMarkers() {
		return markers;
	}

	/**
	 * Returns one currently known Marker with this imgpoint or null
	 */
	@Override
	public Marker getMarker(Point2D imgpoint) {
		for (Marker m : markers) {
			if (m.imgpoint.equals(imgpoint))
				return m;
		}
		return null;
	}

	/**
	 * Returns one currently known Marker with this realpoint or null
	 */
	@Override
	public Marker getMarker(GpsPoint realpoint) {
		for (Marker m : markers) {
			if (m.realpoint.equals(realpoint))
				return m;
		}
		return null;
	}
	
	/**
	 * Returns the last added Marker
	 */
	@Override
	public Marker getLastMarker() {
		return markers.get(markers.size()-1);
	}

	/**
	 * Removes one currently known Marker with this imgpoint or returns false
	 */
	@Override
	public boolean removeMarker(Point2D imgpoint) {
		Marker m = getMarker(imgpoint);
		if (m == null)
			return false;
		markers.remove(m);
		return true;
	}

	/**
	 * Removes one currently known Marker with this realpoint or returns false
	 */
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
	
	@Override
	public void save() { }
}
