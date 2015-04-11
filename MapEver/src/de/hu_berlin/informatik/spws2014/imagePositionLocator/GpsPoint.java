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
 * Represents a point defined by latitude
 * and longitude.
 */
public class GpsPoint implements Serializable {
	private static final long serialVersionUID = 1L;
	static private  transient double  RADIUS = 6371;  // earth's mean radius in km
	
	public double longitude;
	public double latitude;
	public long time;
	
	public GpsPoint(double longitude, double latitude, long time) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.time = time;
	}
	
	/**
	 * The new GpsPoint will be the linear weighted average
	 * of a and b based on time.
	 */
	public GpsPoint(GpsPoint a, GpsPoint b, long time) {
		this.time = time;
		this.longitude = linInterpolation(a.time, b.time, time, a.longitude, b.longitude);
		this.latitude = linInterpolation(a.time, b.time, time, a.latitude, b.latitude);
	}
	
	/**
	 * The new GpsPoint will be the average of a and b.
	 */
	public GpsPoint(GpsPoint a, GpsPoint b) {
		this.longitude = (a.longitude + b.longitude) / 2;
		this.latitude = (a.latitude+ b.latitude) / 2;
		this.time = (a.time + b.time) / 2;
	}
	
	/**
	 * The new GpsPoint will be the average of a, b and c.
	 */
	public GpsPoint(GpsPoint a, GpsPoint b, GpsPoint c) {
		this.longitude = (a.longitude + b.longitude + c.longitude) / 3;
		this.latitude = (a.latitude+ b.latitude + c.latitude) / 3;
		this.time = (a.time + b.time + c.time) / 3;
	}
	
	private double linInterpolation(long atime, long btime, long time, double aval, double bval) {
		return (bval-aval)/(btime-atime)*(time-atime)+aval;
	}

	public GpsPoint getOrthogonal(GpsPoint origin) {
		  double s = Math.sin(Math.toRadians(-90));
		  double c = Math.cos(Math.toRadians(-90));
		  
		  double xnew = ((longitude - origin.longitude) * c - (latitude - origin.latitude) * s) + origin.longitude;
		  double ynew = ((longitude - origin.longitude) * s + (latitude - origin.latitude) * c) + origin.latitude;
		  
		  return new GpsPoint(xnew, ynew, this.time);
	}
	
	/**
	 * @return the distance of this point to a
	 * assuming both are in a plane
	 */
	public double getPlanarDistance(GpsPoint a) {
		double tmp = Math.sqrt(Math.pow(a.latitude - this.latitude, 2) + Math.pow(a.longitude - this.longitude, 2));
		if (tmp != 0)
			return tmp;
		else
			return Double.MIN_NORMAL;
	}

	/**
	 * Returns the distance from this point to the supplied point, in km (using
	 * Haversine formula)
	 *
	 * from: Haversine formula - R. W. Sinnott, "Virtues of the Haversine", Sky
	 * and Telescope, vol 68, no 2, 1984
	 *
	 * @this {LatLon} latitude/longitude of origin point
	 * @param {LatLon} point: latitude/longitude of destination point
	 * @param {Number} [precision=4]: number of significant digits to use for
	 *        returned value
	 * @returns {Number} distance in km between this point and destination point
	 */
	public double getSphericalDistance(GpsPoint point) {

		double R = RADIUS;
		double phi1 = this.latitude * Math.PI / 180d, lambda1 = this.longitude
				* Math.PI / 180d;
		double phi2 = point.latitude * Math.PI / 180d, lambda2 = point.longitude
				* Math.PI / 180d;
		double deltaLamda = phi2 - phi1;
		double deltaLambda = lambda2 - lambda1;

		double a = Math.sin(deltaLamda / 2) * Math.sin(deltaLamda / 2)
				+ Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2)
				* Math.sin(deltaLambda / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double d = R * c;

		return d;
	}
	
	public String toString() {
		return "GPS<" + latitude + "," + longitude + ">";
	}
}