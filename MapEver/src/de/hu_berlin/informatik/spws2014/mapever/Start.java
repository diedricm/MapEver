/* Copyright (C) 2014,2015 Hendryk Köppel, Florian Kempf, Hauke Heinrichs
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import de.hu_berlin.informatik.spws2014.ImagePositionLocator.TrackDB;
import de.hu_berlin.informatik.spws2014.ImagePositionLocator.TrackDBEntry;
import de.hu_berlin.informatik.spws2014.mapever.camera.CornerDetectionCamera;
import de.hu_berlin.informatik.spws2014.mapever.entzerrung.Entzerren;
import de.hu_berlin.informatik.spws2014.mapever.navigation.Navigation;

public class Start extends BaseActivity {
	
	private FrameLayout layout;
	
	// Neue Karte Popup
	private AlertDialog newMapPopup;
	
	// Requestcodes für Neue Karte-Aktionen
	private static final int CHOOSE_FILE_REQUESTCODE = 1337;
	private static final int TAKE_PICTURE_REQUESTCODE = 42;
	
	// Requestcode für Aufruf der Navigation (Rückgabewert bei Fehler)
	private static final int NAVIGATION_REQUESTCODE = 413;
	
	private static final String IMAGE_TARGET_FILENAME = MapEverApp.TEMP_IMAGE_FILENAME;
	
	// image path
	public static final String INTENT_IMAGEPATH = "de.hu_berlin.informatik.spws2014.mapever.Start.NewImagePath";
	public static final String INTENT_EXIT = "de.hu_berlin.informatik.spws2014.mapever.Start.Exit";
	
	// OpenCV initialisieren
	static {
		if (!OpenCVLoader.initDebug()) {
			Log.e("OpenCV", "Could not load OpenCV!");
		}
	}
	
	// keeps track of the maps and their respective positions in the grid
	private Map<Integer, TrackDBEntry> positionIdList = new HashMap<Integer, TrackDBEntry>();
	private List<Bitmap> bitmapList = new ArrayList<Bitmap>();
	
	// track the state of some GUI-elements for orientation changes
	private boolean isPopupOpen = false;
	private boolean isHelpShown = false;
	private boolean isContextMenuOpen = false;
	
	private int noMaps = 0;
	private double[] intentPos = null;
	private static int tileSize = 0;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d("Start", "onCreate..." + (savedInstanceState != null ? " with savedInstanceState" : ""));
		super.onCreate(savedInstanceState);
		
		// We do not want to have multiple instances, as that ends up with race
		// conditions on reading/writing the data files.
		// Using singleTask for this is simply broken.
		// So instead forward the intent to a "clean" task if necessary
		if (!isTaskRoot() && (getIntent().getFlags() & (Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK)) == 0) {
			getIntent().setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(getIntent());
			finish();
			return;
		}

		if (savedInstanceState == null) {
			Intent intent = getIntent();
			if (intent != null && intent.getBooleanExtra(INTENT_EXIT, false)) {
				finish();
				return;
			}
			if (intent != null && intent.getData() != null &&
				intent.getData().getScheme() != null &&
				intent.getData().getScheme().equals("geo")) {
				String pos = intent.getData().getSchemeSpecificPart();
				Pattern p = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?),([+-]?\\d+(?:\\.\\d+)?).*");
				Matcher m = p.matcher(pos);
				if (m.find()) {
					intentPos = new double[2];
					intentPos[0] = Double.valueOf(m.group(1));
					intentPos[1] = Double.valueOf(m.group(2));
				}
			}
		}
		PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
		
		layout = new FrameLayout(getBaseContext());
		setContentView(layout);
		getLayoutInflater().inflate(R.layout.activity_start, layout);
		
		// number of columns for the gridview
		int column = 3;
		Resources resources = getResources();
		
		// get a list of all maps from the databse
		if (!TrackDB.loadDB(new File(MapEverApp.getAbsoluteFilePath("")))) {
			Log.e("Start", "Could not start DB!");
			System.exit(-1);
		}
		
		ArrayList<TrackDBEntry> mapList = new ArrayList<TrackDBEntry>(TrackDB.main.getAllMaps());
		Collections.sort(mapList, new Comparator<TrackDBEntry>() {
			public int compare(TrackDBEntry t1, TrackDBEntry t2) {
				if (t1.getIdentifier() > t2.getIdentifier()) return -1;
				if (t1.getIdentifier() < t2.getIdentifier()) return 1;
				return 0;
			}
		});
		
		// Debug
		System.out.println("getAllEntries: " + mapList);
		System.out.println("isContextMenuOpen: " + isContextMenuOpen);
		
		final GridView gridview = (GridView) findViewById(R.id.start);
		gridview.setNumColumns(column);
		gridview.setAdapter(new ImageAdapter(this));
		
		if (mapList.isEmpty()) {
			noMaps = 1;
		}
		
		// uses null tiles to enforce the respective orientation layouts
		if (noMaps == 1) {
			// no maps present, landscape orientation
			if (getResources().getConfiguration().orientation == 2) {
				bitmapList.add(null);
				bitmapList.add(BitmapFactory.decodeResource(resources, R.drawable.neue_karte));
				bitmapList.add(null);
			}
			// no maps present, portrait orientation
			else {
				bitmapList.add(null);
				bitmapList.add(null);
				bitmapList.add(null);
				bitmapList.add(null);
				bitmapList.add(BitmapFactory.decodeResource(resources, R.drawable.neue_karte));
				bitmapList.add(null);
			}
		}
		// if maps are present, get them from the list and assign them to the positions in the grid
		else {
			bitmapList.add(BitmapFactory.decodeResource(resources, R.drawable.neue_karte));
			
			int position = 0;
			for (TrackDBEntry d : mapList) {
				position++;
				positionIdList.put(position, d);
				
				// get the ID of the map
				String id_string = Long.toString(positionIdList.get(position).getIdentifier());
				File thumbFile = new File(MapEverApp.getAbsoluteFilePath(id_string + "_thumb"));
				Bitmap thumbBitmap = null;
				
				// try to load bitmap of thumbnail if it exists
				if (thumbFile.exists()) {
					thumbBitmap = BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
				}
				
				// add the thumbnail of the map to the bitmapList if it exists, a dummy picture otherwise
				if (thumbBitmap != null) {
					bitmapList.add(thumbBitmap);
				}
				else {
					bitmapList.add(BitmapFactory.decodeResource(resources, R.drawable.map_dummy));
				}
				
				// (((Debug
				System.out.println("mapID: " + d.getIdentifier());
				System.out.println("ID String:" + MapEverApp.getAbsoluteFilePath(id_string));
				System.out.println("ID map (position , ID)");
				for (Map.Entry<Integer, TrackDBEntry> entry : positionIdList.entrySet()) {
					System.out.println("ID map: " + "(" + entry.getKey() + " , " + entry.getValue() + ")");
				}
				// )))Debug
			}
		}
		
		
		// ////////// hier wird das popup fenster erstellt ///////////////
		newMapPopup = new AlertDialog.Builder(Start.this).create();
		// /////////sobald man irgendwo ausserhalb den bildschirm beruehrt
		// /////////wird das popup geschlossen
		newMapPopup.setCanceledOnTouchOutside(true);
		newMapPopup.setOnCancelListener(
				new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						isPopupOpen = false;
					}
				}
				);
		
		gridview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				// clicks auf die Karten ignorieren, wenn das Hilfe-Overlay angezeigt wird
				if (isHelpShown) {
					return;
				}
				
				// Erster Button: Neue Karte
				// Wenn keine Karten vorhanden sind, ist die Position des Buttons egal
				if (noMaps == 1 || position == 0) {
					// //////sobald neue karte button geklickt erscheint schon das popup///////
					showNewMapPopup();
				}
				else {
					// Toast.makeText(Start.this, "" + position, Toast.LENGTH_SHORT).show();
					Intent intent = new Intent(getApplicationContext(), Navigation.class);
					intent.putExtra(Navigation.INTENT_LOADMAPID,  positionIdList.get(position).getIdentifier());
					intent.putExtra(Navigation.INTENT_POS, intentPos);
					startActivityForResult(intent, NAVIGATION_REQUESTCODE);
				}
			}
		});
		registerForContextMenu(gridview);
	}
	
	public class ImageAdapter extends BaseAdapter {
		private Context mContext;
		
		public ImageAdapter(Context c) {
			mContext = c;
		}
		
		public int getCount() {
			return bitmapList.size();
		}
		
		public Object getItem(int position) {
			return null;
		}
		
		public long getItemId(int position) {
			return 0;
		}
		
		public int getPx(int dimensionDp) {
			float density = getResources().getDisplayMetrics().density;
			return (int) (dimensionDp * density + 0.5f);
		}
		
		// handles, if tiles can be clicked
		@Override
		public boolean isEnabled(int position) {
			if (noMaps == 0) {
				return true;
			}
			else {
				if (getResources().getConfiguration().orientation == 1 && position == 4) {
					return true;
				}
				else if (getResources().getConfiguration().orientation == 2 && position != 1) {
					return false;
				}
				else if (getResources().getConfiguration().orientation == 2 && position == 1) {
					return true;
				}
				else {
					return false;
				}
			}
		}
		
		DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

		public View getView(int position, View convertView, ViewGroup parent) {
			ImageView imageView = new ImageView(mContext);
			
			if (convertView == null) {
				// About 3/4 inch seems like a good size
				tileSize = (int)(120 * displayMetrics.density);
				imageView.setLayoutParams(new GridView.LayoutParams(tileSize, tileSize));
				imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
				imageView.setPadding(getPx(8), getPx(8), getPx(8), getPx(8));
			}
			else {
				imageView = (ImageView) convertView;
			}
			
			// adds bitmaps of the map-thumbnails to the grid
			Bitmap[] bitmapArray = new Bitmap[bitmapList.size()];
			bitmapList.toArray(bitmapArray);
			
			if (position == 0) {
				imageView.setFocusable(false);
			}
			
			if (bitmapArray[position] != null) {
				imageView.setImageBitmap(bitmapArray[position]);
			}
			return imageView;
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		Log.d("Start", "onStart...");
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		Log.d("Start", "onStop...");
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Log.d("Start", "onResume...");
		
		if (isPopupOpen) {
			showNewMapPopup();
		}
	}
	
	public void onPause() {
		super.onPause();
		Log.d("Start", "onPause...");
		
		if (newMapPopup != null) {
			newMapPopup.dismiss();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.start, menu);
		
		// If in debug mode, activate debug options
		if (MapEverApp.isDebugModeEnabled(this)) {
			menu.findItem(R.id.action_load_testmap).setVisible(true);
		}
		
		return super.onCreateOptionsMenu(menu);
	}
	
	// application-state handling
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		
		savedInstanceState.putBoolean("isPopupOpen", isPopupOpen);
		savedInstanceState.putBoolean("isHelpShown", isHelpShown);
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		isPopupOpen = savedInstanceState.getBoolean("isPopupOpen");
		isHelpShown = savedInstanceState.getBoolean("isHelpShown");
		
		if (isHelpShown) {
			isHelpShown = false;
			toggleQuickHelp();
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		
		if (id == R.id.action_quick_help) {
			toggleQuickHelp();
			return true;
		}
		if (id == R.id.action_load_testmap) {
			// load test map (ID 0)
			Intent intent = new Intent(getApplicationContext(), Navigation.class);
			intent.putExtra(Navigation.INTENT_LOADMAPID, 0L);
			startActivityForResult(intent, NAVIGATION_REQUESTCODE);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void toggleQuickHelp() {
		if (isHelpShown) {
			hideHelp();
		}
		else {
			getLayoutInflater().inflate(R.layout.start_help, layout);
			findViewById(R.id.start_help_layout).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					hideHelp();
				}
			});
			isHelpShown = true;
		}
	}
	
	private boolean hideHelp() {
		if (!isHelpShown) {
			return false;
		}
		layout.removeViewAt(layout.getChildCount() - 1);
		isHelpShown = false;
		return true;
	}
	
	public static int getStatusBarHeight(Context context) {
		int result = 0;
		int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
			result = context.getResources().getDimensionPixelSize(resourceId);
		}
		return result;
	}
	
	@Override
	public void onBackPressed() {
		if (isHelpShown) {
			hideHelp();
			return;
		}
		// beendet offiziell die App
		finish();
	}
	
	// ////// for getting the images
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CHOOSE_FILE_REQUESTCODE && resultCode == RESULT_OK) {
			Intent EntzerrenActivity = new Intent(getApplicationContext(), Entzerren.class);
			
			// Quell-Uri lesen
			Uri srcUri = data.getData();
			
			try {
				// InputStream für Quelldatei erzeugen
				Log.d("Neue_Karte/onActivityResult", "Copying file '" + srcUri.toString() + "'");
				InputStream inStream = this.getContentResolver().openInputStream(srcUri);
				
				// Zieldatei erstellen
				String destFilename = IMAGE_TARGET_FILENAME;
				File destFile = new File(MapEverApp.getAbsoluteFilePath(destFilename));
				
				// Kopiere Daten von InputStream zu OutputStream
				FileUtils.copyStreamToFile(inStream, destFile);
				
				// Stream auf Quelldatei schließen
				inStream.close();
				
				EntzerrenActivity.putExtra(INTENT_IMAGEPATH, destFilename);
			}
			catch (IOException e) {
				showErrorAlert(R.string.new_map_copy_error);
				e.printStackTrace();
				
			}
			
			startActivity(EntzerrenActivity);
		}
		else if (requestCode == TAKE_PICTURE_REQUESTCODE && resultCode == RESULT_OK) {
			Intent EntzerrenActivity = new Intent(getApplicationContext(), Entzerren.class);
			EntzerrenActivity.putExtra(INTENT_IMAGEPATH, IMAGE_TARGET_FILENAME);
			startActivity(EntzerrenActivity);
		}
		else if (requestCode == NAVIGATION_REQUESTCODE && resultCode != RESULT_OK) {
			// Die Navigation hat einen Fehler zurückgegeben.
			
			// Öffne einen AlertDialog
			showErrorAlert(resultCode == 0 ? R.string.navigation_map_not_found : resultCode);
		}
	}
	
	private void showNewMapPopup() {
		newMapPopup.show();
		isPopupOpen = true;
		Window win = newMapPopup.getWindow();
		win.setContentView(R.layout.newmap);
		
		// camera
		ImageButton camera = (ImageButton) win.findViewById(R.id.camera);
		camera.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent photoIntent;
				
				if (Settings.getPreference_cdCamera(Start.this)) {
					// Intent erzeugen, der die CornerDetectionCamera startet
					photoIntent = new Intent(getApplicationContext(), CornerDetectionCamera.class);
					
					// (In MediaStore.EXTRA_OUTPUT wird hier ein String statt einer Uri übergeben.)
					photoIntent.putExtra(MediaStore.EXTRA_OUTPUT, MapEverApp.getAbsoluteFilePath(IMAGE_TARGET_FILENAME));
					photoIntent.putExtra(CornerDetectionCamera.NO_CONFIRM, true);
				}
				else {
					// Intent erzeugen, der Standard-Android-Kamera startet
					photoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
					File destFile = new File(MapEverApp.getAbsoluteFilePath(IMAGE_TARGET_FILENAME));
					photoIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(destFile));
				}
				
				// Activity starten und auf Ergebnis (Bild) warten
				startActivityForResult(photoIntent, TAKE_PICTURE_REQUESTCODE);
			}
		});
		
		// filechooser
		ImageButton filechooser = (ImageButton) win.findViewById(R.id.filechooser);
		filechooser.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("image/*");
				Intent i = Intent.createChooser(intent, "File");
				startActivityForResult(i, CHOOSE_FILE_REQUESTCODE);
			}
		});
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		int position = info.position;
		String header = "";
		if (position != 0 && noMaps == 0) {
			header = positionIdList.get(position).getMapname();
			if (header.isEmpty()) {
				header = getResources().getString(R.string.navigation_const_name_of_unnamed_maps);
			}
		}
		if (position != 0 && noMaps == 0) {
			String rename = getString(R.string.start_context_rename);
			String delete = getString(R.string.start_context_delete);
			menu.add(0, v.getId(), 0, rename);
			menu.add(0, v.getId(), 0, delete);
			menu.setHeaderTitle(header);
		}
	}
	
	public void deleteMap(TrackDBEntry map) {
		TrackDB.main.delete(map);
		String basefile = MapEverApp.getAbsoluteFilePath(Long.toString(map.getIdentifier())); 
		new File(basefile).delete();
		new File(basefile + MapEverApp.THUMB_EXT).delete();
	}
	
	public void renameMap(TrackDBEntry map, String newName) {
		map.setMapname(newName);
	}
	
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		int position = info.position;
		String rename = getString(R.string.start_context_rename);
		String delete = getString(R.string.start_context_delete);
		
		String mapName = positionIdList.get(position).getMapname();
		if (mapName.isEmpty()) {
			mapName = getResources().getString(R.string.navigation_const_name_of_unnamed_maps);
		}
		
		if (item.getTitle() == rename) {
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			alert.setTitle(R.string.navigation_rename_map);
			final EditText input = new EditText(this);
			input.setText(mapName);
			alert.setView(input);
			alert.setPositiveButton(R.string.navigation_rename_map_rename, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					String newName = input.getEditableText().toString();
					renameMap(positionIdList.get(info.position), newName);
					showToast(getResources().getString(R.string.start_context_rename_success));
				}
			});
			
			alert.setNegativeButton(R.string.navigation_rename_map_cancel,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							dialog.cancel();
						}
					});
			AlertDialog alertDialog = alert.create();
			alertDialog.show();
		}
		else if (item.getTitle() == delete) {
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			
			alert.setIcon(android.R.drawable.ic_dialog_alert);
			alert.setTitle(R.string.start_context_delete);
			alert.setMessage(R.string.start_context_delete_confirmation_msg);
			alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					deleteMap(positionIdList.get(info.position));
					Intent intent = getIntent();
					finish();
					startActivity(intent);
				}
			});
			alert.setNegativeButton(android.R.string.no, null);
			alert.show();
		}
		else {
			return false;
		}
		return true;
	}
	
	private void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}
	
	private void showErrorAlert(int stringResId) {
		// Erstelle entsprechenden AlertDialog
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.general_error_title);
		builder.setMessage(stringResId);
		AlertDialog dialog = builder.create();
		
		// Dialog anzeigen
		dialog.show();
		
		// Wenn der Dialog geschlossen wird
		// dialog.setOnCancelListener(new OnCancelListener() {
		// public void onCancel(DialogInterface dialog) {
		// }
		// });
	}
	
	// returns the size of the grid's tiles in px
	public static int getThumbnailSize() {
		return tileSize;
	}
}
