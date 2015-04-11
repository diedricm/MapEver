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

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;

import java.util.List;

import de.hu_berlin.informatik.spws2014.mapever.R;
import de.hu_berlin.informatik.spws2014.mapever.entzerrung.CornerDetector;

@SuppressWarnings("deprecation")
public class CornerDetectionCamera extends Activity implements CvCameraViewListener2
{
	private class AsyncCornerDetection extends AsyncTask<Void, Void, Void>
	{
		public AsyncCornerDetection(CornerDetectionCamera parent)
		{
			this.parent = parent;
		}
		
		protected Void doInBackground(Void... unused)
		{
			while (parent != null && parent.run_detection)
			{
				if (parent.last_frame != null)
				{
					parent.last_corners = CornerDetector.guess_corners(parent.last_frame);
				}
			}
			
			return null;
		}
		
		private CornerDetectionCamera parent;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_cornerdetectioncamera);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		camera_view = (CornerDetectionView) findViewById(R.id.corner_detection_java_surface_view);
		camera_view.setVisibility(SurfaceView.VISIBLE);
		camera_view.setCvCameraViewListener(this);
		camera_view.startMonitoringOrientation();
		
		last_corners = new Point[] { new Point(), new Point(), new Point(), new Point() };
		run_detection = true;
		corner_detection_task = new AsyncCornerDetection(this);
		corner_detection_task.execute();		
	}
	
	public void onCameraViewStarted(int width, int height)
	{
	}
	
	public void onCameraViewStopped()
	{
	}
	
	public Mat onCameraFrame(CvCameraViewFrame inputFrame)
	{
		last_frame = inputFrame.gray().clone();
		Mat rgba_img = inputFrame.rgba();
		
		Point[] corners = last_corners;
		
		if (corners != null) {
			for (Point p : corners)
				Core.circle(rgba_img, p, 10, CIRCLE_COLOR);
			
			Core.line(rgba_img, corners[0], corners[1], LINE_COLOR);
			Core.line(rgba_img, corners[1], corners[2], LINE_COLOR);
			Core.line(rgba_img, corners[2], corners[3], LINE_COLOR);
			Core.line(rgba_img, corners[3], corners[0], LINE_COLOR);
		}
		
		return rgba_img;
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		if (camera_view != null)
		{
			camera_view.disableView();
			camera_view.stopMonitoringOrientation();
		}
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		// OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, loader_callback);
		camera_view.enableView();
		camera_view.startMonitoringOrientation();
	}
	
	public void onDestroy()
	{
		super.onDestroy();
		if (camera_view != null)
		{
			camera_view.disableView();
			camera_view.stopMonitoringOrientation();
		}
		run_detection = false;
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case CONFIRM_RESULT_CODE: {
				if (resultCode == Activity.RESULT_OK)
				{
					Intent result_data = new Intent();
					setResult(Activity.RESULT_OK, result_data);
					
					camera_view.stopMonitoringOrientation();
					run_detection = false;
					finish();
				}
				break;
			}
		}
	}
	
	public void onTakePictureClick(View v)
	{
		// Dateinamen aus Intent lesen, oder Default verwenden
		final String filename = this.getIntent().hasExtra(MediaStore.EXTRA_OUTPUT)
				? this.getIntent().getStringExtra(MediaStore.EXTRA_OUTPUT)
				: Environment.getExternalStorageDirectory().getPath() + "/temp.jpg";
		
		
		Toast.makeText(this,
			"Saving picture as " + filename,
			Toast.LENGTH_SHORT
		).show();
		
		final CornerDetectionCamera this_ref = this;
		camera_view.take_picture(filename, new CornerDetectionView.PictureCallback()
		{
			@Override
			public void onPictureTaken()
			{
				if(this_ref.getIntent().hasExtra(NO_CONFIRM))
				{
					Intent result_data = new Intent();
					this_ref.setResult(Activity.RESULT_OK, result_data);
					this_ref.run_detection = false;
					this_ref.finish();
				}
				else
				{
					Intent intent = new Intent(this_ref, de.hu_berlin.informatik.spws2014.mapever.camera.ConfirmImageActivity.class);
					intent.putExtra("filename", filename);
					startActivityForResult(intent, CONFIRM_RESULT_CODE);
				}
			}
		});
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		preview_resolution_menu = menu.addSubMenu("PreviewResolution");
		List<Size> available_resolutions = camera_view.get_preview_resolutions();
		preview_resolution_items = new MenuItem[available_resolutions.size()];
		int i = 0;
		for (Size res : available_resolutions)
		{
			preview_resolution_items[i] = preview_resolution_menu.add(
				PREVIEW_MENU_ID,
				i,
				Menu.NONE,
				res.width + "x" + res.height
			);
			++i;
		}
		
		picture_resolution_menu = menu.addSubMenu("PictureResolution");
		available_resolutions = camera_view.get_picture_resolutions();
		picture_resolution_items = new MenuItem[available_resolutions.size()];
		i = 0;
		for (Size res : available_resolutions)
		{
			picture_resolution_items[i] = picture_resolution_menu.add(
				PICTURE_MENU_ID,
				i,
				Menu.NONE,
				res.width + "x" + res.height
			);
			++i;
		}
		
		List<String> flash_modes=camera_view.get_flash_modes();
		if(flash_modes!=null)
		{
			flash_menu = menu.addSubMenu("FlashModes");
			flash_menu_items = new MenuItem[flash_modes.size()];

			i = 0;
			for (String mode : flash_modes)
			{
				flash_menu_items[i] = flash_menu.add(
					FLASH_MENU_ID,
					i,
					Menu.NONE,
					mode
				);
				++i;
			}
		}
		
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getGroupId() == PREVIEW_MENU_ID)
		{
			List<Size> available_resolutions = camera_view.get_preview_resolutions();
			Size requested_resolution = available_resolutions.get(item.getItemId());
			camera_view.set_preview_resolution(requested_resolution);
			Size new_resolution = camera_view.get_preview_resolution();
			Toast.makeText(this,
				"Set preview resolution to " + new_resolution.width + "x" + new_resolution.height,
				Toast.LENGTH_SHORT
			).show();
		}
		else if (item.getGroupId() == PICTURE_MENU_ID)
		{
			List<Size> available_resolutions = camera_view.get_picture_resolutions();
			Size requested_resolution = available_resolutions.get(item.getItemId());
			camera_view.set_picture_resolution(requested_resolution);
			Size new_resolution = camera_view.get_picture_resolution();
			Toast.makeText(this,
				"Set picture resolution to " + new_resolution.width + "x" + new_resolution.height,
				Toast.LENGTH_SHORT
			).show();
		}
		else if (item.getGroupId() == FLASH_MENU_ID)
		{
			List<String> flash_modes=camera_view.get_flash_modes();
			String requested_mode = flash_modes.get(item.getItemId());
			camera_view.set_flash_mode(requested_mode);
			Toast.makeText(this,
				"Set flash mode to "+requested_mode,
				Toast.LENGTH_SHORT
			).show();
		}
		
		return true;
	}
	
	private AsyncCornerDetection corner_detection_task;
	private Mat last_frame;
	private Point[] last_corners;
	private boolean run_detection;
	
	private CornerDetectionView camera_view;
	private SubMenu preview_resolution_menu, picture_resolution_menu, flash_menu;
	private MenuItem[] preview_resolution_items, picture_resolution_items, flash_menu_items;
	private static final int PREVIEW_MENU_ID = 1, PICTURE_MENU_ID = 2, FLASH_MENU_ID = 3;
	private static final int CONFIRM_RESULT_CODE = 100;
	private static final Scalar CIRCLE_COLOR = new Scalar(0, 255, 0, 255);
	private static final Scalar LINE_COLOR = new Scalar(255, 0, 0);

	public static final String NO_CONFIRM = "de.hu_berlin.informatik.spws2014.mapever.camera.CornerDetectionCamera.NoConfirm";
}
