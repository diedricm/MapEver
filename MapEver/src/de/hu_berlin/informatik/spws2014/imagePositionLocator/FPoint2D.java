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

/**
 * Floating Point 2D
 */
public class FPoint2D {
	public double x;
	public double y;
	
	public FPoint2D (int x, int y) {
		this.x = x;
		this.y = y;
	}
	
	public FPoint2D(double x, double y) {
		this.x = x;
		this.y = y;
	}
	
	public FPoint2D(Point2D inp) {
		this.x = inp.x;
		this.y = inp.y;
	}
	
	public FPoint2D () { }
	
	/**
	 * FusedMultiplyAdd
	 * Add the scalar * fPoint2D to this point.
	 * @param fPoint2D
	 * @param scalar
	 */
	public void fma(FPoint2D fPoint2D, double scalar) {
		x += fPoint2D.x * scalar;
		y += fPoint2D.y * scalar;
	}
	
	public void div(double scalar) {
		x /= scalar;
		y /= scalar;
	}
	
	public String toString() {
		return Integer.toHexString(this.hashCode()) + " X: " + x + " Y: " + y;
	}
}
