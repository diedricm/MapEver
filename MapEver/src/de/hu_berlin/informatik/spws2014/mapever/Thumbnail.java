/* Copyright (C) 2014,2015 Philipp Lenk
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

package de.hu_berlin.informatik.spws2014.mapever;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;

import java.io.FileOutputStream;

public class Thumbnail
{
	private Thumbnail(){}
		
	/**
	 * Given the filename and path of an existing image, creates a scaled down
	 * version of it and saves it as output_filename
	 * (when output_filename parameter is missing this will be /path/to/image/name_thumb.png)
	 * (Filetype is fixed to png right now, I could adjust this if desired)
	 * 
	 * @param input_filename Path and filename of an existing image
	 * @param output_filename Path and filename of the thumbnail(defaults to /path/to/image/name_thumb.png)
	 * @param thumb_width Desired thumbnail width
	 * @param thumb_height Desired thumbnail height
	**/
	public static void generate(String input_filename, String output_filename, int thumb_width, int thumb_height) throws java.io.IOException
	{
		int sample_size = get_best_sample_size(input_filename,thumb_width,thumb_height);
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = false;
		options.inSampleSize = sample_size;
		
		Bitmap thumb = ThumbnailUtils.extractThumbnail(
			BitmapFactory.decodeFile(input_filename,options),
			thumb_width,thumb_height
		);
		
		FileOutputStream fos = new FileOutputStream(output_filename);
		thumb.compress(Bitmap.CompressFormat.PNG,100,fos);
		fos.close();
	}
	
	public static void generate(String input_filename, int thumb_width, int thumb_height) throws java.io.IOException
	{
		generate(input_filename,get_thumbnail_filename(input_filename),thumb_width,thumb_height);
	}

	private static int get_best_sample_size(String filename, int thumb_width, int thumb_height)
	{
		//Decode to get image width/height.
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(filename, options);
		
		int current_width=options.outWidth, current_height=options.outHeight;
		int current_sample_size=1;
		
		//increase sample size until it produces an image that is too small
		while(current_width>=thumb_width && current_height>=thumb_height)
		{
			current_width/=2;
			current_height/=2;
			current_sample_size*=2;
		}
		
		return current_sample_size/2; //last size producing a image >= the desired
	}
	
	private static String get_thumbnail_filename(String original_filename)
	{
		return strip_extension(original_filename)+"_thumb.png";
	}
	
	private static String strip_extension(String filename)
	{
		return filename.lastIndexOf(".")==-1?
			filename:
			filename.substring(0, filename.lastIndexOf("."));
	}
	
}
