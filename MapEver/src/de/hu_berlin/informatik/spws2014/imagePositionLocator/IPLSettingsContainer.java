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

/**
 * Contains values for customizing the ImagePositionLocator
 */
public class IPLSettingsContainer {
	public static IPLSettingsContainer DefaultContainer = new IPLSettingsContainer(3, 4.2, 0.1, 0.5, true);
	
	private double fallofExponent;
	private double minTriAngleSize;
	private double badTriWeightPenalty;
	private double maxDissimilarityPercent;
	private boolean debugOutputEnabled;
	
	public IPLSettingsContainer(double fallofExponent, double minTriAngleSize, double badTriWeightPenalty, double maxDissimilarityPercent, boolean debugOutputEnabled) {
		setFallofExponent(fallofExponent);
		setMinTriAngleSize(minTriAngleSize);
		setBadTriWeightPenalty(badTriWeightPenalty);
		setMaxDissimilarityPercent(maxDissimilarityPercent);
		setDebugOutputEnabled(debugOutputEnabled);
	}

	public double getFallofExponent() {
		return fallofExponent;
	}

	public void setFallofExponent(double fallofExponent) {
		this.fallofExponent = fallofExponent;
	}

	public double getMinTriAngleSize() {
		return minTriAngleSize;
	}

	public void setMinTriAngleSize(double minTriAngleSize) {
		this.minTriAngleSize = minTriAngleSize;
	}

	public double getBadTriWeightPenalty() {
		return badTriWeightPenalty;
	}

	public void setBadTriWeightPenalty(double badTriWeightPenalty) {
		this.badTriWeightPenalty = badTriWeightPenalty;
	}

	public double getMaxDissimilarityPercent() {
		return maxDissimilarityPercent;
	}

	public void setMaxDissimilarityPercent(double maxDissimilarityPercent) {
		this.maxDissimilarityPercent = maxDissimilarityPercent;
	}

	public boolean isDebugOutputEnabled() {
		return debugOutputEnabled;
	}

	public void setDebugOutputEnabled(boolean debugOutputEnabled) {
		this.debugOutputEnabled = debugOutputEnabled;
	}
}
