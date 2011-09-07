/* Copyright Rene Mayrhofer
 * File created 2008-11-11
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class implements a reader for the data format generated by Carl Fischer's
 * Linux "logger" tool for sampling accelerometer data from Xsens devices.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class XsensLogReader extends AsciiLineReaderBase {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger(XsensLogReader.class.getName());

	public final static int VALUE_RANGE = 65536;

	/** The sample rate in Hz. */
	public final static int SAMPLE_RATE = 256;
	/** The sample width in ms - 1000/SAMPLE_RATE, which translates to nearly 4 */
	private final static int SAMPLE_WIDTH = 4;
	
	/** The time when the last sample was issued, in msec. */
	private long lastSampleAt;
	
	/** Initializes the parallel port PWM log reader. It only saves the
	 * passed parameters and opens the InputStream to read from the specified
	 * file, and thus implicitly to check if the file exists and can be opened.
	 * 
	 * @param filename The log to read from. This may either be a normal log file
	 *                 when simulation is intended or it can be a FIFO/pipe to read
	 *                 online data.
	 * @throws FileNotFoundException When filename does not exist or can not be opened.
	 */
	public XsensLogReader(String filename) throws FileNotFoundException {
		// the maximum number of data lines to read from the port 2x3
		super(filename, 6, 0, false); 

		this.lastSampleAt = 0;
		
		logger.info("Reading from " + filename);
	}

	/** Initializes the parallel port PWM log reader. It only saves the
	 * passed parameters. This is an alternative to @see #ParallelPortPWMReader(String, int)
	 * and should only be used in special cases.
	 * 
	 * @param stream Specifies the InputStream to read from.
	 */
	public XsensLogReader(InputStream stream) {
		// the maximum number of data lines to read from the port - obviously 8
		super(stream, 6); 

		this.lastSampleAt = 0;
		
		logger.info("Reading from input stream");
	}
	
	/** A helper function to parse single line of the format produced by 
	 * parport-pulsewidth. This method creates the samples and emits events.
	 * @param line The line to parse.
	 */
	//@Override
	protected void parseLine(String line) {
		int samplenum;
		long timestamp = 0;
		StringTokenizer st = new StringTokenizer(line, " ", false);
		
		try {
			samplenum = Integer.parseInt(st.nextToken());
			timestamp = Long.parseLong(st.nextToken());
		}
		catch (NumberFormatException e) {
			logger.warn("Unable to decode sample number or timestamp, ignoring line: " + e);
			return;
		}
		
		if (logger.isDebugEnabled())
			logger.debug("Reading sample number " + samplenum + " at timestamp " + timestamp + " ms");
		// sanity check
		if (timestamp < lastSampleAt + SAMPLE_WIDTH-1) {
			logger.error("Reading from the past (read " + timestamp + ", last sample at " + 
					lastSampleAt + ")! Aborting parsing");
			return;
		}

		// special case: first sample
		if (lastSampleAt == 0) {
			if (logger.isDebugEnabled())
				logger.debug("First sample starting at " + timestamp + " us");
			lastSampleAt = timestamp;
		}

		// another sanity check
		if (timestamp > lastSampleAt + SAMPLE_WIDTH) {
			logger.warn("Reading from the future (and jumping forwards by more than "
					+ SAMPLE_WIDTH + "ms: " +
					timestamp + ", last sample at " + lastSampleAt + ")! Aborting parsing");
		}
		lastSampleAt = timestamp;

		double sample[] = new double[maxNumLines];
		for (int i=0; i<maxNumLines; i++) {
			try {
				sample[i] = Integer.parseInt(st.nextToken());
			}
			catch (NumberFormatException e) {
				logger.warn("Unable to decode sample " + i);
				return;
			}
		}
			
		if (logger.isDebugEnabled())
			logger.debug("Emitting sample for timestamp " + lastSampleAt);
		emitSample(sample);
	}
	
	/** Provides appropriate parameters for interpreting the values to 
	 * normalize to the [-1;1] range.
	 */
	//@Override
	public TimeSeries.Parameters getParameters() {
		return new TimeSeries.Parameters() {
			public float getMultiplicator() {
				return 2f/VALUE_RANGE;
			}

			public float getOffset() {
				return -1f;
			}
		};
	}
	/** Instead of to [-1;1], these integer parameters map to [-1024;1024],
	 * i.e. MAXIMUM_RANGE in TimeSeries_Int. */
	public TimeSeries_Int.Parameters getParameters_Int() {
		return new TimeSeries_Int.Parameters() {
			public int getMultiplicator() {
				return 2*TimeSeries_Int.MAXIMUM_VALUE;
			}

			public int getDivisor() {
				return VALUE_RANGE;
			}

			public int getOffset() {
				return -TimeSeries_Int.MAXIMUM_VALUE;
			}
		};
	}

	/////////////////////////// test code begins here //////////////////////////////
	public static void main(String[] args) throws IOException {
		org.openuat.sensors.test.AsciiLineReaderRunner.mainRunner("XsensLogReader", args);
	}
}
