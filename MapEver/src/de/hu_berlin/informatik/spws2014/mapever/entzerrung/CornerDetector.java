/* Copyright (C) 2014,2015 Philipp Lenk, Jan Müller
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

package de.hu_berlin.informatik.spws2014.mapever.entzerrung;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class CornerDetector
{
	/**
	 * Guesses the most likly corners of a distorted map within an image.
	 * Expects OpenCV to be initialized.
	 * The results are already pretty good but could propably be improved
	 * via tweaking the parameters or adding some additional line filtering
	 * criteria(like them being kind of parallel for instance...)
	 * 
	 * @param gray_img A grayscale image in OpenCVs Mat format.
	 * @return An array of propable corner points in the following form: {x0,y0,x1,y1,x2,y2,x3,y3} or null on error.
	 **/
	public static Point[] guess_corners(Mat gray_img)
	{
		Mat lines = new Mat();
		Imgproc.Canny(gray_img, gray_img, THRESHOLD0, THRESHOLD1, APERTURE_SIZE, false);
		Imgproc.HoughLinesP(gray_img, lines, RHO, THETA, HOUGH_THRESHOLD,
				Math.min(gray_img.cols(), gray_img.rows()) / MIN_LINE_LENGTH_FRACTION, MAX_LINE_GAP);
		
		double[][] edge_lines = filter_lines(lines, gray_img.size());
		
		Point[] ret_val = new Point[4];
		ret_val[0] = find_intercept_point(edge_lines[0], edge_lines[2]);
		ret_val[1] = find_intercept_point(edge_lines[0], edge_lines[3]);
		ret_val[2] = find_intercept_point(edge_lines[1], edge_lines[3]);
		ret_val[3] = find_intercept_point(edge_lines[1], edge_lines[2]);
		
		// do sanity checks and return null on invalid coordinates
		for (int i = 0; i < 4; i++) {
			// check if coordinates are outside image boundaries
			if (ret_val[i].x < 0 || ret_val[i].y < 0 || ret_val[i].x > gray_img.width() || ret_val[i].y > gray_img.height()) {
				return null;
			}
			
			// check if point equal to other point
			for (int j = i + 1; j < 4; j++) {
				if (ret_val[j].x == ret_val[i].x && ret_val[j].y == ret_val[i].y) {
					return null;
				}
			}
		}
		
		return ret_val;
	}
	
	/**
	 * Finds the intersection point of 2 given lines. Uses a very ugly formula stolen from Wikipedia, feel free to
	 * improve the code below ;-)
	 * Doesnt do proper error handling and returns {-1,-1} with parallel lines.
	 * Yes, I know its aweful, i was tired and not necessarily extremly motivated ;-)
	 * 
	 * @param l0 The first line, that is two points on it, saved like this: {x0,y0,x1,y1}
	 * @param l1 The second line, that is two points on it, saved like this: {x0,y0,x1,y1}
	 * @return The intersection point between those lines as an OpenCV Point
	 **/
	private static Point find_intercept_point(double[] l0, double[] l1)
	{
		double denominator = (l0[0] - l0[2]) * (l1[1] - l1[3]) - (l0[1] - l0[3]) * (l1[0] - l1[2]);
		if (denominator == 0)
			return new Point(-1, -1);
		
		double l0_factor = l0[0] * l0[3] - l0[1] * l0[2];
		double l1_factor = l1[0] * l1[3] - l1[1] * l1[2];
		double x = (l0_factor * (l1[0] - l1[2]) - l1_factor * (l0[0] - l0[2])) / denominator;
		double y = (l0_factor * (l1[1] - l1[3]) - l1_factor * (l0[1] - l0[3])) / denominator; // ugly as hell ;_;
		
		return new Point(x, y);
	}
	
	/**
	 * Calculates the slope of a given line.
	 * 
	 * @param line The line, that is two points on it, saved like this: {x0,y0,x1,y1}
	 * @return The lines slope
	 **/
	private static double get_slope(double[] line)
	{
		if (Math.abs(line[2] - line[0]) < 0.0000001)
			return Double.MAX_VALUE;
		
		return (line[3] - line[1]) / (line[2] - line[0]);
	}
	
	/**
	 * Due to lots of false positives close to the images border, this defines what is
	 * "too close", so that find_lines can reasonably discard those
	 * 
	 * @param line The line to be tested
	 * @param dimensions The images size
	 * @return true if the line is deemed "too close" to the images border, false otherwise
	 **/
	private static boolean too_close(double[] line, Size dimensions)
	{
		return line[0] <= dimensions.width * TOO_CLOSE_FRACTION ||
				line[1] <= dimensions.height * TOO_CLOSE_FRACTION ||
				line[2] <= dimensions.width * TOO_CLOSE_FRACTION ||
				line[3] <= dimensions.height * TOO_CLOSE_FRACTION ||
				
				line[0] >= dimensions.width * (1.0 - TOO_CLOSE_FRACTION) ||
				line[1] >= dimensions.height * (1.0 - TOO_CLOSE_FRACTION) ||
				line[2] >= dimensions.width * (1.0 - TOO_CLOSE_FRACTION) ||
				line[3] >= dimensions.height * (1.0 - TOO_CLOSE_FRACTION);
	}
	
	/**
	 * Finds the lines closest to the images border within a reasonable range
	 * of slopes.
	 * 
	 * Turns out this is sufficient to give acceptable results.
	 * 
	 * @param lines The lines within the original image, in OpenCVs Mat format(as returned by HoughLines or HoughLinesP)
	 * @param image_dimensions The original image size
	 * @return 4 lines in the by now well known format of 4 doubles per line: {x0,y0,x1,y1}{x0,y0,x1,y1}...
	 **/
	private static double[][] filter_lines(Mat lines, Size image_dimensions)
	{
		double[][] ret_lines = new double[4][4];
		double min_x = Double.MAX_VALUE, max_x = Double.MIN_VALUE, min_y = Double.MAX_VALUE, max_y = Double.MIN_VALUE;
		
		for (int l = 0; l < lines.cols(); ++l)
		{
			double current_line[] = lines.get(0, l);
			if (too_close(current_line, image_dimensions))
				continue;
			
			double slope = get_slope(current_line);
			if (Math.abs(slope) <= MAX_SLOPE)
			{
				double cl_min_y = Math.min(current_line[1], current_line[3]);
				double cl_max_y = Math.max(current_line[1], current_line[3]);
				if (cl_min_y < min_y)
				{
					ret_lines[0] = current_line;
					min_y = cl_min_y;
				}
				if (cl_max_y > max_y)
				{
					ret_lines[1] = current_line;
					max_y = cl_max_y;
				}
			}
			else if (Math.abs(1.0 / slope) <= MAX_SLOPE)
			{
				double cl_min_x = Math.min(current_line[0], current_line[2]);
				double cl_max_x = Math.max(current_line[0], current_line[2]);
				if (cl_min_x < min_x)
				{
					ret_lines[2] = current_line;
					min_x = cl_min_x;
				}
				if (cl_max_x > max_x)
				{
					ret_lines[3] = current_line;
					max_x = cl_max_x;
				}
			}
			
		}
		return ret_lines;
	}
	
	private static final double MAX_SLOPE = 0.3;
	private static final double THRESHOLD0 = 60,
			THRESHOLD1 = 200;
	private static final int APERTURE_SIZE = 3;
	
	private static final int RHO = 1;
	private static final double THETA = Math.PI / 180;
	private static final int HOUGH_THRESHOLD = 60,
			MIN_LINE_LENGTH_FRACTION = 4,
			MAX_LINE_GAP = 10;
	
	private static double TOO_CLOSE_FRACTION = 0.0001;
}
