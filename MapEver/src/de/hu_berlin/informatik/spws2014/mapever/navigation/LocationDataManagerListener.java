/* Copyright (C) 2014,2015  Björn Stelter, Hagen Sparka
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

package de.hu_berlin.informatik.spws2014.mapever.navigation;

import java.util.concurrent.Callable;

/**
 * Dient dazu, die Mitteilung der Lokalisierung, dass eine neue Position für den
 * Nutzer vorliegt zu behandeln.
 */
public class LocationDataManagerListener implements Callable<Void> {
	// Context der Activity
	Navigation navigationContext;
	
	public LocationDataManagerListener(Navigation context) {
		this.navigationContext = context;
	}
	
	/**
	 * Wird vom LocationDataManager aufgerufen, wenn er eine neue Position für
	 * den Nutzer ermittelt hat
	 */
	@Override
	public Void call() throws Exception {
		// An Navigation übergeben
		navigationContext.onNewUserPosition();
		return null;
	}
}
