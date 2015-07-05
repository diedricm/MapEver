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

import org.opencv.core.MatOfFloat6;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Subdiv2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements an algorithm for matching GpsPoints to
 * Point2D's based on Markers
 */
public class ImagePositionLocator {
	IPLSettingsContainer settings;
	
	private ArrayList<ProjectionTriangle> projs;
	private List<Marker> markers;
	private Point2D imageSize;
	
	public ImagePositionLocator(LocationDataManager inpdman, Point2D imageSize, IPLSettingsContainer settings) {
		this.imageSize = imageSize;
		this.settings = settings;
	}
	
	public ArrayList<ProjectionTriangle> getProjectionTriangles() {
		return projs;
	}
	
	/**
	 * Computes the image point to currentPosition
	 * @param currentPosition
	 * @return new image point
	 */
	public Point2D getPointPosition(GpsPoint currentPosition) {
		if (markers.size() <= 2) return null;
		if (/*projs == null ||*/ currentPosition == null)
			return null;
		System.out.println("\n\nNew Point : " + currentPosition.toString());
		double[][] A = new double[markers.size()][3];
		double[] bx = new double[markers.size()];
		double[] by = new double[markers.size()];
		for (int i = 0; i < markers.size(); i++) {
			A[i][0] = markers.get(i).realpoint.longitude;
			A[i][1] = markers.get(i).realpoint.latitude;
			A[i][2] = 1;
			bx[i] = markers.get(i).imgpoint.x;
			by[i] = markers.get(i).imgpoint.y;
		}
		double[] bx3 = new double[3];
		double[] by3 = new double[3];
		double[][] AtWA = new double[3][3];
		for (int i = 0; i < markers.size(); i++) {
			bx3[0] += bx[i] * A[i][0];
			bx3[1] += bx[i] * A[i][1];
			bx3[2] += bx[i] * A[i][2];
			by3[0] += by[i] * A[i][0];
			by3[1] += by[i] * A[i][1];
			by3[2] += by[i] * A[i][2];
			AtWA[0][0] += A[i][0] * A[i][0];
			AtWA[0][1] += A[i][0] * A[i][1];
			AtWA[0][2] += A[i][0] * A[i][2];
			AtWA[1][1] += A[i][1] * A[i][1];
			AtWA[1][2] += A[i][1] * A[i][2];
			AtWA[2][2] += A[i][2] * A[i][2];
		}
		AtWA[1][0] = AtWA[0][1];
		AtWA[2][0] = AtWA[0][2];
		AtWA[2][1] = AtWA[1][2];
		double detAtWA = AtWA[0][0] * AtWA[1][1] * AtWA[2][2] +
			AtWA[0][1] * AtWA[1][2] * AtWA[2][0] +
			AtWA[0][2] * AtWA[1][0] * AtWA[2][1] -
			AtWA[0][2] * AtWA[1][1] * AtWA[2][0] -
			AtWA[1][2] * AtWA[2][1] * AtWA[0][0] -
			AtWA[2][2] * AtWA[0][1] * AtWA[1][0];
		double[][] inverse = new double [3][3];
		inverse[0][0] = AtWA[1][1] * AtWA[2][2] - AtWA[1][2] * AtWA[2][1];
		inverse[0][1] = AtWA[2][1] * AtWA[0][2] - AtWA[2][2] * AtWA[0][1];
		inverse[0][2] = AtWA[0][1] * AtWA[1][2] - AtWA[0][2] * AtWA[1][1];
		inverse[1][0] = AtWA[1][2] * AtWA[2][0] - AtWA[1][0] * AtWA[2][2];
		inverse[1][1] = AtWA[2][2] * AtWA[0][0] - AtWA[2][0] * AtWA[0][2];
		inverse[1][2] = AtWA[0][2] * AtWA[1][0] - AtWA[0][0] * AtWA[1][2];
		inverse[2][0] = AtWA[1][0] * AtWA[2][1] - AtWA[1][1] * AtWA[2][0];
		inverse[2][1] = AtWA[2][0] * AtWA[0][1] - AtWA[2][1] * AtWA[0][0];
		inverse[2][2] = AtWA[0][0] * AtWA[1][1] - AtWA[0][1] * AtWA[1][0];
		double[] coeffsx = new double[3];
		double[] coeffsy = new double[3];
		for (int i = 0; i < 3; i++) {
			coeffsx[i] = inverse[i][0] * bx3[0] + inverse[i][1] * bx3[1] + inverse[i][2] * bx3[2];
			coeffsy[i] = inverse[i][0] * by3[0] + inverse[i][1] * by3[1] + inverse[i][2] * by3[2];
			coeffsx[i] /= detAtWA;
			coeffsy[i] /= detAtWA;
		}
		if (true)
		return new Point2D(coeffsx[0] * currentPosition.longitude + coeffsx[1] * currentPosition.latitude + coeffsx[2],
				coeffsy[0] * currentPosition.longitude + coeffsy[1] * currentPosition.latitude + coeffsy[2]);
		
		FPoint2D result = new FPoint2D();
		double sum = 0;
		double distances[] = new double[projs.size()];
		double distToClosestPivot = Double.MAX_VALUE;
		
		//Get closest pivot
		for (int i = 0; i < projs.size(); i++) {
			distances[i] = projs.get(i).getPivot().getSphericalDistance(currentPosition);
			if (distToClosestPivot > distances[i])
				distToClosestPivot = distances[i];
		}
		
		for (int i = 0; i < projs.size(); i++) {
			double unnormdist = distToClosestPivot / distances[i];
			
			double tmp = distanceFallofFunction(unnormdist) * projs.get(i).getWeigth();
			
			int weight = (distances[i] < 0.01) ?
					(int) (0.01 - distances[i]) * 1000 : 1;
			
			FPoint2D dbgtmp = projs.get(i).project(currentPosition, weight);
			
			result.fma(dbgtmp, tmp);
			sum += tmp;
			
			System.out.println("Proj of " + projs.get(i).toString() + " x:" + dbgtmp.x + " y:" + dbgtmp.y + " actweight: " + tmp);
		}
		result.div(sum);
		
		System.out.println("Result: " + result.toString());
		
		return new Point2D(result);
	}
	
	/**
	 * Builds ProjectionTriangles from triangulated markers.
	 * Requires OpenCV!
	 */
	public void newMarkerAdded(List<Marker> markers) {
		this.markers = markers;
		if (true)
		return;
		if (markers.size() < 2) return;
		
		if (markers.size() == 2) {
			//Guess third marker
			projs = new ArrayList<ProjectionTriangle>();
			projs.add(new ProjectionTriangle(markers.get(0), markers.get(1)));
		} else {
			Subdiv2D subdiv = new Subdiv2D();
			subdiv.initDelaunay(new Rect(0,0,imageSize.x,imageSize.y));
			
			for (Marker m : markers)
				System.out.println("-> " + m.realpoint.longitude + " / " + m.realpoint.latitude);
			for (Marker m : markers)
				subdiv.insert(new Point(m.imgpoint.x, m.imgpoint.y));
			
			MatOfFloat6 mafloat = new MatOfFloat6();
			subdiv.getTriangleList(mafloat);
			float[] tmparray = mafloat.toArray();
			
			ArrayList<ProjectionTriangle> tmplist = new ArrayList<ProjectionTriangle>();
			for (int i = 0; i < tmparray.length; i += 6) {
				Marker m1 = findMarkerByPoint(markers, tmparray[i], tmparray[i + 1]);
				Marker m2 = findMarkerByPoint(markers, tmparray[i + 2], tmparray[i + 3]);
				Marker m3 = findMarkerByPoint(markers, tmparray[i + 4], tmparray[i + 5]);
				
				if (m1 != null && m2 != null && m3 != null)
					tmplist.add(new ProjectionTriangle(m1, m2, m3,
							settings.getMaxDissimilarityPercent(),
							settings.getBadTriWeightPenalty(),
							settings.getMinTriAngleSize()));
			}
			
			for (ProjectionTriangle mainPt : tmplist) {
				for (ProjectionTriangle subPt : tmplist) {
					if (mainPt != subPt)
						mainPt.tryAddToProjGroup(subPt);
				}
			}
			
			projs = tmplist;
		}
	}
	
	/**
	 * Decides if the lng and lat values represent a GpsPoint of a Marker.
	 * @return The found Marker.
	 */
	private Marker findMarkerByPoint(List<Marker> markers, float x, float y) {
		for (Marker m : markers) {
			if (m.imgpoint.x == (int) x
			&& (m.imgpoint.y == (int) y)) {
				return m;
			}
		}
		return null;
	}
	
	private double distanceFallofFunction(double d) {
		if (d > 1) throw new IllegalArgumentException("The distance fallof is only defined from 0..1!");
		
		return Math.pow(d, settings.getFallofExponent());
	}
}
