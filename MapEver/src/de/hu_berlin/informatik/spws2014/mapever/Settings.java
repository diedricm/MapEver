/* Copyright (C) 2014,2015 Björn Stelter
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

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Settings extends PreferenceActivity {
	
	public static final String key_quickHelp = "pref_quick_help";
	public static final String key_cdCamera = "pref_cdCamera";
	public static final String key_livMultitouch = "pref_liv_multitouch";
	public static final String key_leastsquares = "pref_leastsquares";
	public static final String key_debugMode = "pref_debugmode";
	
	// Ignore deprecation warnings (there are no API 10 compatible alternatives)
	@SuppressWarnings("deprecation")
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.pref_general);
	}
	
	// Preference getters
	
	/**
	 * Returns true if quick help button should be visible in Action Bar.
	 * 
	 * @param context Just use 'this'
	 */
	public static boolean getPreference_quickHelp(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key_quickHelp, false);
	}
	
	/**
	 * Returns true if corner detection camera should be used.
	 * 
	 * @param context Just use 'this'
	 */
	public static boolean getPreference_cdCamera(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key_cdCamera, false);
	}
	
	/**
	 * Returns true if multitouch in LIV should be enabled.
	 * 
	 * @param context Just use 'this'
	 */
	public static boolean getPreference_livMultitouch(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key_livMultitouch, false);
	}

	/**
	 * Returns true if least squares locator should be used
	 * 
	 * @param context Just use 'this'
	 */
	public static boolean getPreference_leastsquares(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key_leastsquares, false);
	}
	
	/**
	 * Returns true if debug mode should be activated.
	 * 
	 * @param context Just use 'this'
	 */
	public static boolean getPreference_debugMode(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key_debugMode, false);
	}
}
