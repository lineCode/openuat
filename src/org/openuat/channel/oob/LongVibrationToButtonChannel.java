/* Copyright Lukas Huser
 * File created 2008-12-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import org.openuat.util.IntervalList;

/**
 * This channel is a <b>transfer</b> channel. It transmits data
 * between devices by giving a vibration signal to the user. The user
 * should press and hold the button while the device vibrates, and 
 * release the button when it doesn't vibrate.<br/>
 * The smallest considered time unit for this channel is set to 1000 ms.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class LongVibrationToButtonChannel extends ButtonChannel {

	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public LongVibrationToButtonChannel(ButtonChannelImpl impl) {
		this.impl = impl;
		minTimeUnit		= 1000;
		inputMode		= MODE_PRESS_RELEASE;
		doRoundDown		= true;
		useCarry		= true;
		messageHandler	= null;
		
		initInterval	= 6500;
		endInterval		= 600;
		
		String endl = System.getProperty("line.separator");
		captureDisplayText	= "Please press and hold the button while the other device "
							+ "vibrates, release it, when it doesn't." + endl
							+ "This device is ready.";
		
		transmitDisplayText	= "This device will send vibration signals. Please press"
							+ "the button on the other device.";
	}
	
	/* The first interval in ms (before the first signal will be sent). */
	private int initInterval;
	
	/* The last interval in ms. This is only needed if the number of intervals
	 * (which actually contain the data) is even, and hence the number of button
	 * events is odd. This situation leads to an empty (non-vibrating) last
	 * interval and an additional vibration signal is needed to make it clear
	 * when this interval end. */
	private int endInterval;
	
	/**
	 * Transmits provided data over this channel.<br/>
	 * Note: this method blocks the caller and will return when
	 * the transmission process has finished.
	 * 
	 * @param message The Data to be sent over this channel.
	 */
	// @Override
	public void transmit(byte[] message) {
		int intervalCount = MESSAGE_LENGTH / BITS_PER_INTERVAL;
		final IntervalList intervals = bytesToIntervals(message, minTimeUnit, BITS_PER_INTERVAL, intervalCount);
		intervals.addFirst(initInterval);
		if (intervalCount % 2 == 0) {
			intervals.add(endInterval);
		}
		
		// start transmission
		impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_PLAIN);
		
		// transmit the data (given from 'intervals')
		// note: the first interval is always an empty (non-vibrating)
		// interval ('initInterval') to give the user some time to prepare.
		// It follows that all vibrating intervals have odd indices in the interval list.
		for (int i = 0; i < intervals.size(); i++) {
			int interval = intervals.item(i);
			if (i % 2 == 0) {
				impl.vibrate(interval);
			}
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				// TODO: log warning
				// logger.warn("Method transmit(byte[]) in transmission thread", e);
			}
		}		
	}

}