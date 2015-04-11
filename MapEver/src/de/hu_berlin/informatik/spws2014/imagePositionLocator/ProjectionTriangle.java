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
import java.util.List;

/**
 * Projects points from GPS to image coords.
 * Triple of Marker.
 */
public class ProjectionTriangle {	
	private double MIN_TRIANGLE_ANGLE_SIZE;
	private double FLAT_TRI_WEIGHT_PENALTY;
	private double MAX_DISSIMILARITY_PERCENT;
	
	private double weight = 1; //[0..1]
	private GpsPoint pivot;
	private Marker a;
	private Marker b;
	private Marker c;
	private double common_divisor;
	public List<ProjectionTriangle> projectionGroup;
	private GpsPoint cachedInput;
	private FPoint2D cachedResult;
	
	public ProjectionTriangle(Marker a, Marker b) {
		initTriangle(a, b, b.getOrthogonal(a));

		pivot = new GpsPoint(a.realpoint, b.realpoint);
	}
	
	public ProjectionTriangle(Marker a, Marker b, Marker c) {
		initTriangle(a,b,c);
		
		pivot = new GpsPoint(a.realpoint, b.realpoint, c.realpoint);
	}
	
	public ProjectionTriangle(Marker a, Marker b, Marker c, double MaxDissim, double BadTriPanelty, double GoodTriMinAngle) {
		MIN_TRIANGLE_ANGLE_SIZE = GoodTriMinAngle;
		FLAT_TRI_WEIGHT_PENALTY = BadTriPanelty;
		MAX_DISSIMILARITY_PERCENT = MaxDissim;
		
		initTriangle(a,b,c);
		
		pivot = new GpsPoint(a.realpoint, b.realpoint, c.realpoint);
	}
	
	private void initTriangle(Marker ma, Marker mb, Marker mc) {
		a = ma;
		b = mb;
		c = mc;
		
		common_divisor = ((b.realpoint.latitude - c.realpoint.latitude) * (a.realpoint.longitude - c.realpoint.longitude)
                +(c.realpoint.longitude - b.realpoint.longitude) * (a.realpoint.latitude - c.realpoint.latitude));
		
		if (!isValidTriangle(a.realpoint.getPlanarDistance(b.realpoint),
				a.realpoint.getPlanarDistance(c.realpoint),
				b.realpoint.getPlanarDistance(c.realpoint),
				MIN_TRIANGLE_ANGLE_SIZE)
		|| !isValidTriangle(a.imgpoint.getDistance(b.imgpoint),
				a.imgpoint.getDistance(c.imgpoint),
				b.imgpoint.getDistance(c.imgpoint),
				MIN_TRIANGLE_ANGLE_SIZE)) {
			weight *= FLAT_TRI_WEIGHT_PENALTY;
		}
		
		projectionGroup = new ArrayList<ProjectionTriangle>();
		projectionGroup.add(this);
	}

	public GpsPoint getPivot() {
		return this.pivot;
	}
	
	public Marker getMarker(int i) {
		switch (i) {
			case 0: return a;
			case 1: return b;
			case 2: return c;
			default: return null;
		}
	}
	
	public double getWeigth() {
		return weight;
	}
	
	/**
	 * @param sideA Distance A
	 * @param sideB Distance B
	 * @param sideC Distance C
	 * @return If the triangle has no angle less than minAngle(in degrees).
	 */
	public boolean isValidTriangle(double sideA, double sideB, double sideC, double minAngle) {
		if (Math.acos((sideA*sideA+sideB*sideB-sideC*sideC)/(2*sideA*sideB)) < Math.toRadians(minAngle)) return false;
		if (Math.acos((sideC*sideC+sideB*sideB-sideA*sideA)/(2*sideC*sideB)) < Math.toRadians(minAngle)) return false;
		if (Math.acos((sideA*sideA+sideC*sideC-sideB*sideB)/(2*sideA*sideC)) < Math.toRadians(minAngle)) return false;
		
		return true;
	}
	
	/**
	 * Approximates the new image position by averaging
	 * the projectSingle() of every projectionGroup member.
	 * @return new image position
	 */
	public FPoint2D project(GpsPoint pos, int ownPriority) {
		FPoint2D result = new FPoint2D();
		
		for (ProjectionTriangle t : projectionGroup) {
			//own projection is more important than the rest of the projGroup
			if (t == this)
				result.fma(t.projectSingle(pos), ownPriority);
			else
				result.fma(t.projectSingle(pos), 1);
		}
		result.div(projectionGroup.size() - 1 + ownPriority);
		
		return result;
	}
	
	/**
	 * Approximates the new image position using a barycentric coordinate system.
	 * Formula taken from https://en.wikipedia.org/wiki/Barycentric_coordinate_system#Conversion_between_barycentric_and_Cartesian_coordinates
	 * @return new image position
	 */
	public FPoint2D projectSingle(GpsPoint pos) {
		if (pos != cachedInput) {
			double delta1 = ((b.realpoint.latitude - c.realpoint.latitude) * (pos.longitude - c.realpoint.longitude)
					          +(c.realpoint.longitude - b.realpoint.longitude) * (pos.latitude - c.realpoint.latitude))/ common_divisor;
			
			double delta2 = ((c.realpoint.latitude - a.realpoint.latitude) * (pos.longitude - c.realpoint.longitude)
			                  +(a.realpoint.longitude - c.realpoint.longitude) * (pos.latitude - c.realpoint.latitude))/ common_divisor;
			
			double delta3 = 1 - delta1 - delta2;
			
			double x = delta1 * a.imgpoint.x + delta2 * b.imgpoint.x + delta3 * c.imgpoint.x;
			double y = delta1 * a.imgpoint.y + delta2 * b.imgpoint.y + delta3 * c.imgpoint.y;
			
			cachedInput = pos;
			cachedResult = new FPoint2D(x,y);
		}
		
		return cachedResult;
	}
	
	/**
	 * Checks if two triangles are similar and adds inputTri
	 * to the this ProjectionTriangles projection group.
	 * @param inputTri input triangle
	 * @return if triangles are similar
	 */
	public boolean tryAddToProjGroup(ProjectionTriangle inputTri) {
		Point2D center = new Point2D((a.imgpoint.x+b.imgpoint.x+c.imgpoint.x)/3,
									 (a.imgpoint.y+b.imgpoint.y+c.imgpoint.y)/3);
		for (int i = 0; i < 3; i++) {
			double distToCenter = center.getDistance(getMarker(i).imgpoint);
			double distToNew = getMarker(i).imgpoint.getDistance(inputTri.projectSingle(getMarker(i).realpoint));
			
			if (distToNew > MAX_DISSIMILARITY_PERCENT * distToCenter) {
				return false;
			}
		}
		
		projectionGroup.add(inputTri);
		return true;
	}
	
	public String toString() {
		String sim = "";
		for (ProjectionTriangle t : projectionGroup) {
			if (t != this)
				sim += Integer.toHexString(t.hashCode()) + ", ";
		}
		return Integer.toHexString(this.hashCode()) + " weight: " + this.getWeigth() + " sim: " + sim;
	}
}
