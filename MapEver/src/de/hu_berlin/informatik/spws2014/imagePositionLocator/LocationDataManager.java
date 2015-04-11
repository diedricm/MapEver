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
import java.util.concurrent.Callable;


/**
 * Application interface to the imagePositionLocator.
 * Simplifies the usage and provides plugable data backends
 * via ILDMIOHandler. By default also tries to filter too fast
 * GPS movements.
 */
public class LocationDataManager {
	// seconds until would be accepted
	static int MAX_POINT_DENIAL_TIME = 10;
	// accepted movement speed in meters per second
	static int MAX_MOVEMENT_SPEED = 20;
	// seconds untill the last GpsPoint cannot be used as a marker anymore
	static int MAX_VALID_GPS_TIME = 30;

	// track data
	Point2D imageSize;
	long lastGPSFixTime;
	boolean isSpeedFiltering = true;

	// Session data
	ILDMIOHandler iohandler;
	ImagePositionLocator imagePositionAlg;
	Callable<Void> hostAppCallback;
	Point2D lastImagePoint;
	GpsPoint lastGpsPoint;

	/**
	 * Create new LocationDataManager and inits algorithm
	 * Gets initialized with data from IOHandler
	 * 
	 * @param callback Function to be called, when a new ImagePosition is found.
	 * @param ldmio IOHandler
	 */
	public LocationDataManager(Callable<Void> callback, ILDMIOHandler ldmio, Point2D imageSize, IPLSettingsContainer settings) {
		hostAppCallback = callback;
		iohandler = ldmio;

		this.imageSize = imageSize;
		lastGPSFixTime = iohandler.getLastGpsPointTime();
		int numberOfGpsPoints = iohandler.getAllGpsPoints().size();
		if (numberOfGpsPoints != 0)
			lastGpsPoint = iohandler.getAllGpsPoints().get(numberOfGpsPoints - 1);
		
		imagePositionAlg = new ImagePositionLocator(this, imageSize, settings);
	}

	/**
	 * Adds new GpsPoint to the algorithms knowledge. Input may be raw.
	 * 
	 * @param input
	 */
	public void addPoint(GpsPoint input) {
		if (input == null)
			return;
		
		boolean conforms = false;
		double elapsedTime = 0;
		double movementSpeed = 0;
		
		if (lastGpsPoint == null || ((input.time - lastGpsPoint.time) / 1000) >= MAX_POINT_DENIAL_TIME) {
			conforms = true;
		} else {
			elapsedTime = ((input.time - lastGpsPoint.time) / 1000);
			movementSpeed = lastGpsPoint.getSphericalDistance(input) / elapsedTime / 1000;
			
			if (movementSpeed < MAX_MOVEMENT_SPEED)
				conforms = true;
		}
		
		if (conforms || !isSpeedFiltering) {
			lastGpsPoint = input;
			lastGPSFixTime = System.currentTimeMillis();
			iohandler.setLastGpsPointTime(lastGPSFixTime);
			iohandler.addGpsPoint(input);

			reportNewImagePoint(imagePositionAlg.getPointPosition(input));
		} else {
			System.err.println("Point: " + input.toString() + " exceeds MAX_MOVEMENT_SPEED by " + movementSpeed);
		}
	}

	/**
	 * Checks if setting a point now would be accepted
	 */
	public boolean isMarkerPlacingAllowed() {
		if (lastGpsPoint == null) 
			return false;
		
		if ((System.currentTimeMillis() - lastGPSFixTime) / 1000 > MAX_VALID_GPS_TIME)
			return false;
		
		return true;
	}
	
	/**
	 * Adds new Marker to the algorithms knowledge.
	 * 
	 * @param imgpoint
	 *            Pressed Point on original image. Is expected to be accurate.
	 * @param time
	 *            Current time in milliseconds. Is expected to be consistent
	 *            with the time of the GpsPoints.
	 */
	public Marker addMarker(Point2D imgpoint, long time)
			throws NoGpsDataAvailableException, PointNotInImageBoundsException {
		if (!isMarkerPlacingAllowed())
			throw new NoGpsDataAvailableException("No GPS fix!");
		
		if (imgpoint == null)
			return null;
		
		if (imgpoint.smallerThan(new Point2D(0, 0))) 
			throw new PointNotInImageBoundsException("Point has negative coordinates!");
		
		if (imageSize.smallerThan(imgpoint))
			throw new PointNotInImageBoundsException("Point greater than image size!");
		
		Marker result = new Marker(imgpoint, time, lastGpsPoint);
		if (iohandler.getMarker(result.realpoint) != null)
			throw new NoGpsDataAvailableException("Point already known!");

		iohandler.addMarker(result);

		if (imagePositionAlg != null)
			imagePositionAlg.newMarkerAdded(iohandler.getAllMarkers());
		
		refreshLastPosition();
		return result;
	}

	/**
	 * Refreshes all parameters and computes
	 * the last recieved GpsPoint anew.
	 */
	public void refreshLastPosition() {
		// Send new estimated user position
		if (lastGpsPoint != null) {
			if (iohandler.getAllMarkers().size() != 0) {
				Point2D tmp;
				if (iohandler.getAllMarkers().size() == 1) {
					tmp = iohandler.getAllMarkers().get(0).imgpoint;
				} else {
					imagePositionAlg.newMarkerAdded(iohandler.getAllMarkers());
					tmp = imagePositionAlg.getPointPosition(lastGpsPoint);
				}
				reportNewImagePoint(tmp);
			}
		}
	}
	
	/**
	 * Signals the host application that a new point was found.
	 * 
	 * @param p New image position
	 */
	private void reportNewImagePoint(Point2D p) {
		if (p != null || p != lastImagePoint) {
			lastImagePoint = p;
			if (hostAppCallback != null) {
				try {
					hostAppCallback.call();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * @return Last known image position.
	 */
	public Point2D getLastImagePoint() {
		return lastImagePoint;
	}
	
	/**
	 * @return The number of remaining markers to be set
	 *         by the user before the location algorithm starts.
	 */
	public int remainingUserMarkerInputs() {
		int tmp = 3 - iohandler.getAllMarkers().size();
		return (tmp < 0) ? 0 : tmp;
	}
	
	//-----------------------------------------------------
	// IO-Handling
	//-----------------------------------------------------	
	
	/**
	 * Saves all currently known data in the current LDMIO Handler
	 * and replaces it by newHandler.
	 * 
	 * @param newHandler New data handler. If this is null,
	 *            then a new LDMIOEmpty will be used.
	 */
	public void resetIOHandler(ILDMIOHandler newHandler) {
		iohandler.save();
		iohandler = (newHandler != null) ? newHandler : new LDMIOEmpty();
	}

	/**
	 * Adds all data in addHandler to the LDMs knowledge.
	 * Currently only transports marker data.
	 */
	public void addAllDataPointsFromIOHandler(ILDMIOHandler addHandler) {
		ArrayList<Marker> tmpmarkers = addHandler.getAllMarkers();

		for (Marker m : tmpmarkers) {
			iohandler.addMarker(m);
		}
	}
	
	//-----------------------------------------------------
	// DEBUGGING
	//-----------------------------------------------------
	public ArrayList<ProjectionTriangle> getProjectionTriangles() {
		return imagePositionAlg.getProjectionTriangles();
	}

	public void setDebugConstants(IPLSettingsContainer settings) {
		imagePositionAlg = new ImagePositionLocator(this, imageSize, settings);
	}
	
	/**
	 * Checks or unchecks whether too fast movements should be registered
	 * @param isSpeedFiltering Desired state of isSpeedFiltering
	 * @return The previous state of isSpeedFiltering
	 */
	public boolean setSpeedFiltering(boolean isSpeedFiltering) {
		boolean tmp = isSpeedFiltering;
		this.isSpeedFiltering = isSpeedFiltering;
		return tmp;
	}
}
