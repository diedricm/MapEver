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

/**
 * Enum zur einfacheren Beschreibung des aktuellen Zustands der Navigation
 */
public enum NavigationStates {
	/**
	 * normaler Betrieb, angezeigte Buttons: RefPoint Setzen
	 */
	RUNNING,
	
	/**
	 * RefPoint Setzen betätigt, Warte auf Setzen des Referenzpunkts durch Nutzer
	 */
	MARK_REFPOINT,
	
	/**
	 * RefPoint gesetzt, Nutzer muss ihn noch bestätigen, angezeigte Buttons: akzeptieren, verwerfen
	 */
	ACCEPT_REFPOINT,
	
	/**
	 * RefPoint wurde angewählt, Nutzer muss entscheiden, ob er ihn löschen
	 * möchte oder aber nicht, angezeigte Buttons: RefPoint löschen, zurück
	 */
	DELETE_REFPOINT,
	
	/**
	 * Die Karte kann nun umbenannt werden, es wird ein EditText Feld angezeigt.
	 */
	RENAME_MAP,
	
	/**
	 * Der Hilfebildschirm für RUNNING wird angezeigt
	 */
	HELP_RUNNING,
	
	/**
	 * Der Hilfebildschirm für MARK_REFPOINT wird angezeigt
	 */
	HELP_MARK_REFPOINT,
	
	/**
	 * Der Hilfebildschirm für ACCEPT_REFPOINT wird angezeigt
	 */
	HELP_ACCEPT_REFPOINT,
	
	/**
	 * Der Hilfebildschirm für DELETE_REFPOINT wird angezeigt
	 */
	HELP_DELETE_REFPOINT;
	
	/**
	 * gehört dieser Zustand zur Schnellhilfe?
	 * 
	 * @return
	 */
	public boolean isHelpState() {
		switch (this) {
			case HELP_ACCEPT_REFPOINT:
			case HELP_DELETE_REFPOINT:
			case HELP_MARK_REFPOINT:
			case HELP_RUNNING:
				return true;
				
			default:
				return false;
		}
	}
}
