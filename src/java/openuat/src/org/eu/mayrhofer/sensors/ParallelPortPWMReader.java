/* Copyright Rene Mayrhofer
 * File created 2006-04-27
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/** This class implements a reader for the data format generated by the small
 * Linux native code (a C command line program) to sample pulse-width modulated
 * sensors (e.g. accelerometers) using a standard parallel port. 
 * 
 * Because the output format reports a new value whenever a change on the 
 * parallel port occurs, this class implements sampling with a defined sample
 * rate to generate equidistant sample points.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ParallelPortPWMReader {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(ParallelPortPWMReader.class);

	/** This represent the file to read from and is opened in the constructor.
	 * @see #ParallelPortPWMReader(String, int[], int)
	 */
	private InputStream port;
	
	/** The set of lines of the parallel port to read. Set by the constructor.
	 * @see #ParallelPortPWMReader(String, int[], int)
	 */
	private int[] lines;
	
	/** The length of s sample period, i.e. the sample width, in µsec. */
	private int sampleWidth;
	
	/** This holds all registered sinks, which must be arrays of time series. 
	 * @see #addSink(TimeSeries[])
	 * @see #removeSink(TimeSeries[])
	 */
	private LinkedList sinks;
	
	/** The current samples, i.e. all sample values that fall into the current sample period. */
	private ArrayList[] curSample;
	/** The time when the last sample was issued, in usec. */
	private long lastSampleAt;
	/** Remember the last sample values, in case there is a sample period with no samples from
	 * the sensors. In this case the last values are just repeated.
	 */
	private float[] lastSampleValues;
	
	/** The total number of samples read until currently. Changed by parseLine.
	 * @see #parseLine(String) 
	 */
	private int numSamples;
	
	/** Initializes the parallel port PWM log reader. It only saves the
	 * passed parameters and opens the InputStream to read from the specified
	 * file, and thus implicitly to check if the file exists and can be opened.
	 * 
	 * @param filename The log to read from. This may either be a normal log file
	 *                 when simulation is intended or it can be a FIFO/pipe to read
	 *                 online data.
	 * @param lines The set of lines on the parallel port to read. Must be an integer
	 *              array with a minimum length of 1 and a maximum length of 8, containing
	 *              the indices of the lines to read. These indices are counted from 0 to 7.
	 *              for the parallel port data lines DATA0 to DATA7.
	 * @param samplerate The sample rate in Hz.
	 * @throws FileNotFoundException When filename does not exist or can not be opened.
	 */
	public ParallelPortPWMReader(String filename, int[] lines, int samplerate) throws FileNotFoundException {
		if (lines.length < 1 || lines.length > 8)
			throw new IllegalArgumentException("Number of lines to read must be between 1 and 8");
		String tmp = "";
		for (int i=0; i<lines.length; i++) {
			if (lines[i] < 0 || lines[i] > 7)
				throw new IllegalArgumentException("Line index must be between 0 and 7");
			tmp += lines[i] + " ";
		}
		
		this.lines = lines;
		this.sampleWidth = 1000000 / samplerate;
		this.curSample = new ArrayList[lines.length];
		for (int i=0; i<lines.length; i++)
			curSample[i] = new ArrayList();
		this.lastSampleValues = new float[lines.length];
		
		this.sinks = new LinkedList();
		this.lastSampleAt = 0;
		this.numSamples = 0;
		
		logger.info("Reading from " + filename + ", data lines: " + tmp + 
				" with sample rate " + samplerate + " Hz (sample width " + sampleWidth + " us)");
		
		port = new FileInputStream(new File(filename));
	}
	
	/** Registers a sink, which will receive all new values as they are sampled.
	 * @param sink The time series to fill. This array must have the same number of
	 *             elements as the number of lines specified to the constructor.  
	 */
	public void addSink(SamplesSink[] sink) throws IllegalArgumentException {
		if (sink.length != lines.length)
			throw new IllegalArgumentException("Passed TimeSeries array has " + sink.length 
					+ " elements, but sampling " + lines.length + " parallel port lines");
		sinks.add(sink);
	}
	
	/** Removes a previously registered sink.
	 * 
	 * @param sink The time series to stop filling.
	 * @return true if removed, false if not (i.e. if they have not been added previously).
	 */
	public boolean removeSink(SamplesSink[] sink) {
		return sinks.remove(sink);
	}
	
	public void startSampling() {
		
	}
	
	public void stopStampling() {
		
	}
	
	public void simulateSampling() throws IOException {
		BufferedReader r = new BufferedReader(new InputStreamReader(port));
		
		String line = r.readLine();
		while (line != null) {
			parseLine(line);
			line = r.readLine();
		}
	}
	
	private void parseLine(String line) {
		StringTokenizer st = new StringTokenizer(line, " .", false);
		// first two columns (with a '.' in between) are the timestamp (sec and usec)
		int timestampSecs = Integer.parseInt(st.nextToken()); 
		int timestampUSecs = Integer.parseInt(st.nextToken());
		long timestamp = (long)timestampSecs*1000000 + timestampUSecs;
		logger.debug("Reading at timestamp " + timestamp + " us");
		// sanity check
		if (timestamp <= lastSampleAt) {
			logger.error("Reading from the past! Aborting parsing");
			return;
		}
		
		// special case: first sample
		if (lastSampleAt == 0) {
			logger.debug("First sample starting at " + timestamp + " us");
			lastSampleAt = timestamp;
		}
		
		// check if this sample creates a new sample period
		if (timestamp > lastSampleAt + sampleWidth) {
			logger.debug("Current reading creates new sample");
			
			// get the average over the last period's samples (if there are any, if not, just use the last period's samples)
			if (curSample[0].size() > 0) {
				logger.info("Averaging over " + curSample[0].size() + " values for the last sample");
				for (int i=0; i<lines.length; i++) {
					lastSampleValues[i] = 0;
					for (int j=0; j<curSample[i].size(); j++)
						lastSampleValues[i] += ((Integer) curSample[i].get(j)).intValue();
					lastSampleValues[i] /= curSample[i].size();
					// prepare for the next (i.e. the currently starting) sample period
					curSample[i].clear();
				}
			}
			
			while (timestamp > lastSampleAt + sampleWidth) {
				lastSampleAt += sampleWidth;
				for (int i=0; i<lines.length; i++) {
					// and put into all sinks
					logger.debug("Sample for timestamp " + lastSampleAt + ", number " + numSamples +  
							", line " + lines[i] + " = " + lastSampleValues[i]);
			    	if (sinks != null)
			    		for (ListIterator j = sinks.listIterator(); j.hasNext(); ) {
			    			SamplesSink[] s = (SamplesSink[]) j.next();
			    			s[i].addSample(lastSampleValues[i], numSamples);
			    		}
				}
				numSamples++;
			}
		}
		
		// only continue to extract values when there are more tokens in the line
		if (st.hasMoreElements()) {
			// then the 8 data lines
			int[] allLines = new int[8];
			int elem=0;
			while (elem<8 && st.hasMoreElements()) {
				allLines[elem] = Integer.parseInt(st.nextToken());
				elem++;
			}
			if (elem<8) {
				logger.warn("Short line, only got " + elem + " line values instead of " + 8 + ", ignoring line");
			}
			else {
				// extract the lines we want and remember the values
				for (int i=0; i<lines.length; i++) {
					int val = allLines[lines[i]]; 
					logger.debug("Read value " + val + " on line " + lines[i]);
					curSample[i].add(new Integer(val));
				}
			}
		}
		else
			logger.debug("This is an empty reading containing only a timestamp");
	}
	
	// TODO: create a general interface for a sensor source, but keep it simple --> timestamped, using TimeSeries
	
	
	/////////////////////////// test code begins here //////////////////////////////
	public static void main(String[] args) throws IOException {
		/*class XYSink implements SamplesSink {
			XYSeries series = new XYSeries("Line", false);
			int num=0;
			ArrayList segment = null;
			XYSeries firstActiveSegment = null;
			
			public void addSample(float s, int index) {
				if (index != num++)
					logger.error("Sample index invalid");
				series.add(index, s);
				if (segment != null)
					segment.add(new Float(s));
			}
			public void segmentStart(int index) {
				if (firstActiveSegment == null)
					segment = new ArrayList();
			}
			public void segmentEnd(int index) {
				if (segment != null) {
					firstActiveSegment = new XYSeries("Segment", false);
					for (int i=0; i<segment.size(); i++)
						firstActiveSegment.add(i, ((Float) segment.get(i)).floatValue());
				}
			}
		}
		
		ParallelPortPWMReader r = new ParallelPortPWMReader(args[0], new int[] {0, 1, 2, 3, 4, 5, 6, 7}, 100);
		TimeSeries[] t = new TimeSeries[8];
		XYSink[] s = new XYSink[8];
		for (int i=0; i<8; i++) {
			t[i] = new TimeSeries(50);
			t[i].setOffset(0);
			t[i].setMultiplicator(1/128f);
			t[i].setSubtractTotalMean(true);
			t[i].setActiveVarianceThreshold(350);
			s[i] = new XYSink();
			t[i].addNextStageSink(s[i]);
		}
		r.addSink(t);
		r.simulateSampling();
		
		for (int i=0; i<8; i++) {
			XYDataset data = new XYSeriesCollection(s[i].series);
			JFreeChart chart = ChartFactory.createXYLineChart("Line " + i, "Number [100Hz]", 
					"Sample", data, PlotOrientation.VERTICAL, true, true, false);
			ChartUtilities.saveChartAsJPEG(new File("/tmp/line" + i + ".jpg"), chart, 500, 300);

			XYDataset segData = new XYSeriesCollection(s[i].firstActiveSegment);
			JFreeChart segChart = ChartFactory.createXYLineChart("Segment at line " + i, "Number [100Hz]", 
					"Sample", segData, PlotOrientation.VERTICAL, true, true, false);
			ChartUtilities.saveChartAsJPEG(new File("/tmp/seg" + i + ".jpg"), segChart, 500, 300);
		}*/

		
		class SegmentSink implements SegmentsSink {
			private String filename;
			public SegmentSink(String filename) {
				this.filename = filename;
			}
			public void addSegment(float[] segment, int startIndex) {
				logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);

				if (filename != null) {
					XYSeries series = new XYSeries("Segment", false);
					for (int i=0; i<segment.length; i++)
						series.add(i, segment[i]);
					XYDataset data = new XYSeriesCollection(series);
					JFreeChart chart = ChartFactory.createXYLineChart("Aggregated samples", "Number [100Hz]", 
						"Sample", data, PlotOrientation.VERTICAL, true, true, false);
					try {
						ChartUtilities.saveChartAsJPEG(new File(filename), chart, 500, 300);
					} 
					catch (Exception e) {
						e.printStackTrace();
						return;
					}
					filename = null;
				}
			}
		}
		
		int samplerate = 128;
		int windowsize = samplerate/2; // 1/2 second
		int minsegmentsize = windowsize; // 1/2 second
		float varthreshold = 350;
		ParallelPortPWMReader r2_a = new ParallelPortPWMReader(args[0], new int[] {0, 1, 2}, samplerate);
		ParallelPortPWMReader r2_b = new ParallelPortPWMReader(args[0], new int[] {4, 5, 6}, samplerate);
		TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		r2_a.addSink(aggr_a.getInitialSinks());
		r2_b.addSink(aggr_b.getInitialSinks());
		aggr_a.addNextStageSink(new SegmentSink("/tmp/aggrA.jpg"));
		aggr_b.addNextStageSink(new SegmentSink("/tmp/aggrB.jpg"));
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1/128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(varthreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1/128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(varthreshold);
		r2_a.simulateSampling();
		r2_b.simulateSampling();
	}
}
