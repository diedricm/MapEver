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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

public class JumbledImage
{
	private JumbledImage() {
	}

	/**
	 * Given a distorted image and four corners marking the "intersting" segment, creates a corrected version
	 * of that segment and returns it.
	 * 
	 * This may take some time and should therefore be started asynchronous if possible ;-)
	 * 
	 * Currently the image size has to be chosen very carefully, because the way this is written requires
	 * Heap Memory for 2 full copies. I am working on a more memory saving version that does some caching
	 * to disk, but its currently unbearably slow (Mostly due to Bitmaps getPixel.
	 * The moment i use getPixels ram usage doubles...(Oh, and i have a slow bilinear filtering version, too)).
	 * Would anyone object to a JNI solution(Let me code C++, pretty please with sugar on top?)?
	 * Whom am i kidding, no one will read this anyway...
	 * 
	 * @param jumbled A Bitmap containing the jumbled fragment. Beware, this will be destroyed!
	 * @param corners The four corners as a one dimensional array with coordinates in the following order:
	 *            {x0,y0,x1,y1,x2,y2,x3,y3}
	 * @return A Bitmap containing the corrected segment
	 **/
	
	public static Bitmap transform(Bitmap jumbled, float corners[])
	{
		Log.d("BITMAP", "" + jumbled.getWidth() + " " + jumbled.getHeight());
		
		Log.d("FLOAT", "pre sort: " + corners[0] + " " + corners[1] + " " + corners[2] + " " + corners[3] + " " + corners[4] + " " + corners[5] + " " + corners[6] + " " + corners[7]);
		
		// Sort corners clockwise
		sort_corners(corners);
		
		Log.d("FLOAT", "post sort: " + corners[0] + " " + corners[1] + " " + corners[2] + " " + corners[3] + " " + corners[4] + " " + corners[5] + " " + corners[6] + " " + corners[7]);
		
		
		//The final image size is average(width) X average(height) of the original image
		int dest_width =  (int)(
				Math.sqrt((corners[0]-corners[2])*(corners[0]-corners[2]) + (corners[1]-corners[3])*(corners[1]-corners[3]))+ //top length
				Math.sqrt((corners[6]-corners[4])*(corners[6]-corners[4]) + (corners[7]-corners[5])*(corners[7]-corners[5])) //bottom length
			)/2;
		int dest_height = (int)(
				Math.sqrt((corners[0]-corners[6])*(corners[0]-corners[6]) + (corners[1]-corners[7])*(corners[1]-corners[7]))+ //left length
				Math.sqrt((corners[2]-corners[4])*(corners[2]-corners[4]) + (corners[3]-corners[5])*(corners[3]-corners[5])) //right length
			)/2;
		
		float mapped_corners[] =
		{
				0, 0,
				dest_width, 0,
				dest_width, dest_height,
				0, dest_height
		};
		
		Matrix m = new Matrix();
		m.setPolyToPoly(mapped_corners, 0, corners, 0, 4);
		
		// get the image pixels as an array for faster processing
		int src_pixels[] = new int[jumbled.getWidth() * jumbled.getHeight()];
		int src_width = jumbled.getWidth();
		jumbled.getPixels(src_pixels, 0, jumbled.getWidth(), 0, 0, jumbled.getWidth(), jumbled.getHeight());
		Bitmap.Config conf = jumbled.getConfig();
		jumbled = null;
		
		int pixels[] = new int[dest_width * dest_height];
		float point[] = new float[2];
		
		for (int y = 0; y < dest_height; ++y)
		{
			for (int x = 0; x < dest_width; ++x)
			{
				point[0] = x;
				point[1] = y;
				m.mapPoints(point);
				pixels[y * dest_width + x] = computeColor(src_pixels, src_width, point);
			}
		}
		
		src_pixels = null;
		return Bitmap.createBitmap(pixels, dest_width, dest_height, conf);
		
		// return null;
	}
	
	private static int computeColor(int src[], int stride, float pos[])
	{
		// trivial, not-interpolated one:
		return src[(int) pos[0] + stride * (int) pos[1]];
	}
	
	public static void sort_corners(float unsorted[])
	{
		assert (unsorted.length == 8);
		
		float center[] = new float[2];
		center[0] = (unsorted[0] + unsorted[2] + unsorted[4] + unsorted[6]) / 4;
		center[1] = (unsorted[1] + unsorted[3] + unsorted[5] + unsorted[7]) / 4;
		
		// clockwise bubble sort, yeahy ;-)
		boolean swapped;
		do
		{
			swapped = false;
			for (int i = 0; i < 3; ++i)
			{
				if (!is_clockwise_turn(
						new float[] { unsorted[2 * i], unsorted[2 * i + 1] },
						new float[] { unsorted[2 * i + 2], unsorted[2 * i + 3] },
					center))
				{
					float tmp = unsorted[2 * i];
					unsorted[2 * i] = unsorted[2 * i + 2];
					unsorted[2 * i + 2] = tmp;
					
					tmp = unsorted[2 * i + 1];
					unsorted[2 * i + 1] = unsorted[2 * i + 3];
					unsorted[2 * i + 3] = tmp;
					
					swapped = true;
				}
			}
		} while (swapped);
		
	}
	
	private static boolean is_clockwise_turn(float first[], float second[], float center[])
	{
		assert ((first.length == second.length) && (second.length == center.length) && (center.length == 2));
		
		return (first[0] - center[0]) * (second[1] - center[1]) - (second[0] - center[0]) * (first[1] - center[1]) > 0;
	}
	
}
