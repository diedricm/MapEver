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

package de.hu_berlin.informatik.spws2014.mapever.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.ExifInterface;
import android.util.AttributeSet;
import android.view.OrientationEventListener;

import org.opencv.android.JavaCameraView;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class CornerDetectionView extends JavaCameraView implements PictureCallback
{
	interface PictureCallback
	{
		void onPictureTaken();
	}
	
	public CornerDetectionView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		
		orientation_listener = new OrientationEventListener(context)
		{
			@Override
			public void onOrientationChanged(int orientation)
			{
				device_orientation=(orientation + 45) / 90 * 90;
			}	
		};
	}
	
	public void startMonitoringOrientation()
	{
		orientation_listener.enable();
	}
	
	public void stopMonitoringOrientation()
	{
		orientation_listener.disable();
	}
	
	public List<Size> get_preview_resolutions()
	{
		return mCamera == null ?
				new ArrayList<Size>() :
				mCamera.getParameters().getSupportedPreviewSizes();
	}
	
	public void set_preview_resolution(Size new_resolution)
	{
		disconnectCamera();
		mMaxHeight = new_resolution.height;
		mMaxWidth = new_resolution.width;
		connectCamera(getWidth(), getHeight());
	}
	
	public Size get_preview_resolution()
	{
		return mCamera.getParameters().getPreviewSize();
	}
	
	public List<Size> get_picture_resolutions()
	{
		return mCamera == null ?
				new ArrayList<Size>() :
				mCamera.getParameters().getSupportedPictureSizes();
	}
	
	public List<String> get_flash_modes()
	{
		return mCamera == null ?
			null :
			mCamera.getParameters().getSupportedFlashModes();
	}
	
	public void set_flash_mode(String mode)
	{
		Parameters params = mCamera.getParameters();
		params.setFlashMode(mode);
		mCamera.setParameters(params);
	}
	
	public void set_picture_resolution(Size new_resolution)
	{
		Parameters params = mCamera.getParameters();
		params.setPictureSize(new_resolution.width, new_resolution.height);
		mCamera.setParameters(params);
	}
	
	public Size get_picture_resolution()
	{
		return mCamera.getParameters().getPictureSize();
	}
	
	public void take_picture(String filename, PictureCallback callback)
	{
		picture_filename = filename;
		picture_callback = callback;
		mCamera.setPreviewCallback(null);
		updateOrientation();
		
		final CornerDetectionView this_ref=this;
		mCamera.autoFocus(new Camera.AutoFocusCallback()
		{
			@Override
			public void onAutoFocus(boolean success, Camera cam)
			{
				cam.takePicture(null, null, null, this_ref);
			}
		});
	}
	
	@Override
	public void onPictureTaken(byte[] data, Camera camera)
	{
		mCamera.startPreview();
		mCamera.setPreviewCallback(this);
		
		try
		{
			FileOutputStream fos = new FileOutputStream(picture_filename);
			
			fos.write(data);
			fos.close();
			
		}
		catch (java.io.IOException e)
		{
			e.printStackTrace();
		}
		
		correctImage();
		
		picture_callback.onPictureTaken();
	}
	
	private void updateOrientation()
	{
		if(mCamera == null || device_orientation == OrientationEventListener.ORIENTATION_UNKNOWN)
			return;
		CameraInfo info = new CameraInfo();
		Camera.getCameraInfo(getCameraId(),info);
		int rotation = 0;
		if(info.facing == CameraInfo.CAMERA_FACING_FRONT)
		{
			rotation = (info.orientation - device_orientation +360) % 360;
		}
		else
		{
			rotation = (info.orientation + device_orientation) % 360;
		}
		Parameters params = mCamera.getParameters();
		params.setRotation(rotation);
		mCamera.setParameters(params);
	}
	
	//needed because some devices save rotation as exif information only, but dont rotate the pixels
	//that wouldnt be a problem if bitmapfactory would actually use that exif information, but well...
	private void correctImage()
	{
		try
		{
			ExifInterface exif = new ExifInterface(picture_filename);
			int rotation_value = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
			switch(rotation_value)
			{
				case ExifInterface.ORIENTATION_ROTATE_90:
					rotateImage(90);
					break;
				case ExifInterface.ORIENTATION_ROTATE_180:
					rotateImage(180);
					break;
				case ExifInterface.ORIENTATION_ROTATE_270:
					rotateImage(270);
					break;
				default:
					break;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	//pretty ugly and uses way too much memory, better suggestions more than welcome...
	private void rotateImage(int degree)
	{
		try
		{
			Bitmap btm = BitmapFactory.decodeFile(picture_filename);
			Matrix mat = new Matrix();
			mat.postRotate(degree);
			
			btm = Bitmap.createBitmap(btm, 0, 0, btm.getWidth(), btm.getHeight(), mat, true);
			
			FileOutputStream fos = new FileOutputStream(picture_filename);
			btm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
			fos.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	//needed because opencv opens a camera and does not remember which...
	//and android is not wise enough to tell me...
	//luckily i happen to know we use the first backfacing camera and if none of those is found we use the first frontfacing one
	//this is incredibly ugly so, therefore if anyone has a better idea feel free to use it ;_;
	private int getCameraId()
	{
		
		CameraInfo info = new CameraInfo();
		for(int idx=0;idx<Camera.getNumberOfCameras();++idx)
		{
			Camera.getCameraInfo(idx,info);
			if(info.facing==CameraInfo.CAMERA_FACING_BACK)
				return idx;
		}
		return 0;
	}
	
	private String picture_filename;
	private PictureCallback picture_callback;
	private OrientationEventListener orientation_listener;
	private int device_orientation;
}
