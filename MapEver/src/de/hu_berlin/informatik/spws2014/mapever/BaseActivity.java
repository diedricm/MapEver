/* Copyright (C) 2014,2015 Björn Stelter, Florian Kempf
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
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

public class BaseActivity extends ActionBarActivity {
	// ÜberUns popup
	private AlertDialog aboutUsPopup;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// ////////// hier wird das popup fenster erstellt ///////////////
		aboutUsPopup = new AlertDialog.Builder(BaseActivity.this).create();
		
		// /////////sobald man irgendwo ausserhalb den bildschirm beruehrt
		// /////////wird das popup geschlossen
		aboutUsPopup.setCanceledOnTouchOutside(true);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Show quick help button only in Action Bar if the corresponding setting is activated
		MenuItem item = menu.findItem(R.id.action_quick_help);
		
		if (item != null) {
			if (Settings.getPreference_quickHelp(this)) {
				MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
			}
			else {
				MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_NEVER);
			}
		}
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		
		if (id == R.id.action_about) {
			showAboutUsPopup();
			return true;
		}
		else if (id == R.id.action_settings) {
			// open settings
			Intent settings = new Intent(getApplicationContext(), Settings.class);
			startActivity(settings);
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	
	private void showAboutUsPopup() {
		aboutUsPopup.show();
		Window win = aboutUsPopup.getWindow();
		win.setContentView(R.layout.aboutus);
	}
	
}
