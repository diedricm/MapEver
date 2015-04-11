/* Copyright (C) 2014,2015  Maximilian Diedrich
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

package de.hu_berlin.informatik.spws2014.imagePositionLocator;

import java.io.Serializable;

/**
 * Holds metadata for a track file
 * May only be created by TrackDB!
 */
public class TrackDBEntry implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private Long identifier;
	private String mapname;
	
	TrackDBEntry(Long identifier) {
		this.identifier = identifier;
		mapname = "";
	}
	
	public Long getIdentifier() {
		return identifier;
	}
	
	public String getMapname() {
		return mapname;
	}
	
	public void setMapname(String mapname) {
		this.mapname = mapname;
		TrackDB.main.save();
	}
}