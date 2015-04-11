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
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.File;

import de.hu_berlin.informatik.spws2014.mapever.R;

public class ConfirmImageActivity extends Activity
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera_confirmimageactivity);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		configureImageView();
		Intent intent = getIntent();
		String filename = intent.getStringExtra("filename");
		setImage(filename);
	}
	
	private void configureImageView()
	{
		ImageView image = (ImageView) findViewById(R.id.imageView);
		image.setOnTouchListener(new View.OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent evt)
			{
				switch (evt.getAction())
				{
					case MotionEvent.ACTION_DOWN:
						mx = evt.getX();
						my = evt.getY();
						break;
					case MotionEvent.ACTION_UP:
						v.scrollBy((int) (mx - evt.getX()), (int) (my - evt.getY()));
						break;
					case MotionEvent.ACTION_MOVE:
						v.scrollBy((int) (mx - evt.getX()), (int) (my - evt.getY()));
						mx = evt.getX();
						my = evt.getY();
						break;
				}
				return true;
			}
			
			private float mx, my;
		});
	}
	
	private void setImage(String filename)
	{
		ImageView image_view = (ImageView) findViewById(R.id.imageView);
		
		image_view.setImageURI(Uri.fromFile(new File(filename)));
		// (setScrollX/Y gibt es erst ab API 14)
		image_view.scrollTo(0, 0);
	}
	
	public void onConfirmPictureClick(View v)
	{
		Intent intent = new Intent();
		setResult(Activity.RESULT_OK, intent);
		finish();
	}
	
	public void onCancelPictureClick(View v)
	{
		Intent intent = new Intent();
		setResult(Activity.RESULT_CANCELED, intent);
		finish();
	}
	
	@Override
	public void onStart()
	{
		super.onStart();
	}
	
	@Override
	public void onStop()
	{
		super.onStop();
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}
}
