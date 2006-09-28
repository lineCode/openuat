/* Copyright Rene Mayrhofer
 * File created 2006-09-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.accelerometer;

public class MotionAuthenticationParameters {
	public final static int samplerate = 128; // Hz
	
	public final static int cutOffFrequency = samplerate; // Hz
	
	public final static int activityDetectionWindowSize = samplerate/2; // 1/2 s
	
	// this is very robust
	public final static int activityVarianceThreshold = 750; // this depends on samplerate, update when changing!
	
	public final static int activityMinimumSegmentSize = 3*samplerate; // 3 s
	
	public final static int coherenceWindowSize = samplerate;
	
	public final static int coherenceWindowOverlap = 0;
}
