/* Copyright Lukas Huser
 * File created 2008-12-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import java.util.Timer;
import java.util.TimerTask;

import org.openuat.authentication.OOBChannel;
import org.openuat.util.IntervalList;

/**
 * This channel is a <b>transfer</b> channel. It transmits data
 * between devices by displaying a interval pattern on the screen and
 * a progress bar that keeps growing over the pattern. The user should
 * press and hold the button on the first colored interval, release it on
 * the next interval etc. thus triggering on each interval border a button
 * event (either press or release).<br/>
 * The smallest considered time unit for this channel is set to 600 ms.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class ProgressBarToButtonChannel extends ButtonChannel {

	/**
	 * Creates a new instance of this channel.
	 * 
	 * @param impl A suitable <code>ButtonChannelImpl</code> instance
	 * to handle platform dependent method calls.
	 */
	public ProgressBarToButtonChannel(ButtonChannelImpl impl) {
		this.impl = impl;
		minTimeUnit		= 600;
		inputMode		= MODE_PRESS_RELEASE;
		doRoundDown		= false;
		useCarry		= false;
		messageHandler	= null;
		shortDescription = "Progress Bar";
		
		initInterval	= 2000;
		textDelay		= 5000;
		deltaT			= 40;
		timer			= null; // Note: For every transmission, a new Timer instance is created.
		startTime		= 0L;
		
		String endl = System.getProperty("line.separator");
		if (endl == null) {
			endl = "\n";
		}
		captureDisplayText	= "Please press and hold the button during the bright "
							+ "intervals, release it on dark intervals." + endl
							+ "This device is ready.";
		
		transmitDisplayText	= "This device will display the progress bar. Please press "
							+ "the button on the other device.";
	}
	
	/* The first interval in ms (will be painted in gray). */
	private int initInterval;
	
	/* Wait some time (in ms) to let the user read the 'transmitDisplayText' first. */
	private int textDelay;
	
	/* Repeatedly repaints the gui */
	private Timer timer;
	
	/* The temporal resolution. Update the screen every 'deltaT' milliseconds. */
	private int deltaT;
	
	/* Transmission start timestamp */
	private long startTime;
	
	/* only repaint if progress has actually changed */
	//private int lastProgress;
	
	/**
	 * Transmits provided data over this channel.<br/>
	 * Note: this method does not block the caller and returns
	 * immediately.
	 * 
	 * @param message The Data to be sent over this channel.
	 */
	// @Override
	public void transmit(byte[] message) {
		int intervalCount = MESSAGE_LENGTH / BITS_PER_INTERVAL;
		final IntervalList intervals = bytesToIntervals(message, minTimeUnit, BITS_PER_INTERVAL, intervalCount);
		intervals.addFirst(initInterval);
		impl.setInterval(intervals);
		
		// now run transmission in a separate thread
		final TimerTask task = new TimerTask() {
			public void run() {
				long duration = System.currentTimeMillis() - startTime;
				float progress = (float)(((double)duration / (double)intervals.getTotalIntervalLength()) * 100.0);
				impl.setProgress(progress);
				impl.repaint();
				if (progress > 100f) {
					timer.cancel();
					if (messageHandler != null) {
						messageHandler.handleOOBMessage(OOBChannel.BUTTON_CHANNEL, new byte[]{(byte)1});
					}
				}
			}
		};
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				impl.showTransmitGui(transmitDisplayText, ButtonChannelImpl.TRANSMIT_PLAIN);
				try {
					Thread.sleep(textDelay);
				} catch (InterruptedException e) {
					// TODO: log warning
					// logger.warn("Method transmit(byte[])", e);
				}
				impl.showTransmitGui(null, ButtonChannelImpl.TRANSMIT_BAR);
				// transmit the data (given from 'intervals')
				timer = new Timer();
				startTime = System.currentTimeMillis();
				timer.scheduleAtFixedRate(task, 0, deltaT);
			}
		});
		t.start();
	}

}
