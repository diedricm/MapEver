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

import java.io.Serializable;

/**
 * A tuple of GpsPoint and Point2D.
 */
public class Marker implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public long time;
	public Point2D imgpoint;
	public GpsPoint realpoint = null;
	
	public Marker(Point2D imgpoint, long time, GpsPoint a) {
		this.imgpoint = imgpoint;
		this.time = time;
		realpoint = a;
	}
	
	public Marker(Point2D imgpoint, long time, GpsPoint a, GpsPoint b) {
		this.imgpoint = imgpoint;
		this.time = time;
		realpoint = new GpsPoint(a, b, time);
	}
	
	public void updateRealpoint(GpsPoint a) {
		realpoint = new GpsPoint(this.realpoint, a, this.time);
	}
	
	public void updateRealPoint(GpsPoint a, GpsPoint b) {
		realpoint = new GpsPoint(a, b, this.time);
	}
	
	public Marker getOrthogonal(Marker a) {
		return new Marker(imgpoint.getOrthogonal(a.imgpoint), time, realpoint.getOrthogonal(a.realpoint));
	}
	
	public String toString() {
		return Integer.toHexString(this.hashCode()) + " Imagep: " + imgpoint.toString() + " Realp: " + realpoint.toString();
	}
}