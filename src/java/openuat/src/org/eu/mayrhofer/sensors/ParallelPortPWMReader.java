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
import java.util.NoSuchElementException;
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
	
	/** The maximum number of data lines to read from the port - obviously 8. */
	private static final int MAX_LINES = 8;

	/** This represent the file to read from and is opened in the constructor.
	 * @see #ParallelPortPWMReader(String, int)
	 */
	private InputStream port;
	
	/** The length of s sample period, i.e. the sample width, in µsec. */
	private int sampleWidth;
	
	/** Objects of this type are held in sinks. They represent listeners to 
	 * be notified of samples.
	 */
	private class ListenerCombination {
		int[] lines;
		SamplesSink[] sinks;
		public ListenerCombination(int[] lines, SamplesSink[] sinks) {
			this.lines = lines;
			this.sinks = sinks;
		}
		public boolean equals(Object o) {
			return o instanceof ListenerCombination &&
				((ListenerCombination) o).lines.equals(lines) &&
				((ListenerCombination) o).sinks.equals(sinks);
		}
	}
	
	/** This holds all registered sinks in form of ListenerCombination
	 * objects.
	 * @see #addSink(int[], SamplesSink[])
	 * @see #removeSink(int[], SamplesSink[])
	 */
	private LinkedList sinks;
	
	/** The current samples, i.e. all sample values that fall into the current sample period. */
	private ArrayList[] curSample;
	/** The time when the last sample was issued, in usec. */
	private long lastSampleAt;
	/** Remember the last sample values, in case there is a sample period with no samples from
	 * the sensors. In this case the last values are just repeated.
	 */
	private double[] lastSampleValues;
	
	/** The total number of samples read until currently. Changed by parseLine.
	 * @see #parseLine(String) 
	 */
	private int numSamples;
	
	/** The thread that does the actual reading from the port.
	 * @see #start()
	 * @see #stop()
	 */
	private Thread samplingThread = null;
	
	/** Used to signal the sampling thread to terminate itself.
	 * @see #stop()
	 * @see RunHelper#run()
	 */
	boolean alive = true;
	
	/** Initializes the parallel port PWM log reader. It only saves the
	 * passed parameters and opens the InputStream to read from the specified
	 * file, and thus implicitly to check if the file exists and can be opened.
	 * 
	 * @param filename The log to read from. This may either be a normal log file
	 *                 when simulation is intended or it can be a FIFO/pipe to read
	 *                 online data.
	 * @param samplerate The sample rate in Hz.
	 * @throws FileNotFoundException When filename does not exist or can not be opened.
	 */
	public ParallelPortPWMReader(String filename, int samplerate) throws FileNotFoundException {
		this.sampleWidth = 1000000 / samplerate;
		this.curSample = new ArrayList[MAX_LINES];
		for (int i=0; i<MAX_LINES; i++)
			curSample[i] = new ArrayList();
		this.lastSampleValues = new double[MAX_LINES];
		
		this.sinks = new LinkedList();
		this.lastSampleAt = 0;
		this.numSamples = 0;
		
		logger.info("Reading from " + filename +
				" with sample rate " + samplerate + " Hz (sample width " + sampleWidth + " us)");
		
		port = new FileInputStream(new File(filename));
	}
	
	/** Registers a sink, which will receive all new values as they are sampled.
	 * @param sink The time series to fill. This array must have the same number of
	 *             elements as the number of lines specified to the constructor.  
	 * @param lines The set of lines on the parallel port to read. Must be an integer
	 *              array with a minimum length of 1 and a maximum length of 8, containing
	 *              the indices of the lines to read. These indices are counted from 0 to 7.
	 *              for the parallel port data lines DATA0 to DATA7.
	 */
	public void addSink(int[] lines, SamplesSink[] sink) throws IllegalArgumentException {
		if (lines.length < 1 || lines.length > MAX_LINES)
			throw new IllegalArgumentException("Number of lines to read must be between 1 and " +
					MAX_LINES);
		String tmp = "";
		for (int i=0; i<lines.length; i++) {
			if (lines[i] < 0 || lines[i] > MAX_LINES-1)
				throw new IllegalArgumentException("Line index must be between 0 and " +
						(MAX_LINES-1));
			tmp += lines[i] + " ";
		}
		if (sink.length != lines.length)
			throw new IllegalArgumentException("Passed TimeSeries array has " + sink.length 
					+ " elements, but sampling " + lines.length + " parallel port lines");
		logger.debug("Registering new listener for lines " + tmp);
		sinks.add(new ListenerCombination(lines, sink));
	}
	
	/** Removes a previously registered sink.
	 * 
	 * @param sink The time series to stop filling.
	 * @param lines The set of lines on the parallel port to read. Must be an integer
	 *              array with a minimum length of 1 and a maximum length of 8, containing
	 *              the indices of the lines to read. These indices are counted from 0 to 7.
	 *              for the parallel port data lines DATA0 to DATA7.
	 * @return true if removed, false if not (i.e. if they have not been added previously).
	 */
	public boolean removeSink(int[] lines, SamplesSink[] sink) {
		return sinks.remove(new ListenerCombination(lines, sink));
	}
	
	/** Starts a new background thread to read from the file and create sample
	 * values as the lines are read.
	 */
	public void start() {
		if (samplingThread == null) {
			logger.debug("Starting sampling thread");
			samplingThread = new Thread(new RunHelper());
			samplingThread.start();
		}
	}

	/** Stops the background thread, if started previously. */
	public void stop() {
		if (samplingThread != null) {
			logger.debug("Stopping sampling thread: signalling thread to cancel and waiting;");
			alive = false;
			try {
				samplingThread.interrupt();
				samplingThread.join();
			}
			catch (InterruptedException e) {
				if (! System.getProperty("os.name").startsWith("Windows CE")) {
					logger.error("Error waiting for sampling thread to terminate: " + e.toString() + "\n" + e.getStackTrace().toString());
				}
				else {
					// J2ME CLDC doesn't have reflection support and thus no getStackTrace()....
					logger.error("Error waiting for sampling thread to terminate: " + e.toString());
				}
			}
			logger.error("Sampling thread stopped");
			samplingThread = null;
		}
	}
	
	/** Simulate sampling by reading all available lines from the spcified file. */
	public void simulateSampling() throws IOException {
		BufferedReader r = new BufferedReader(new InputStreamReader(port));
		
		String line = r.readLine();
		while (line != null) {
			parseLine(line);
			line = r.readLine();
		}
	}
	
	/** A helper function to parse single line of the format produced by 
	 * parport-pulsewidth. This method creates the samples and emits events.
	 * @param line The line to parse.
	 */
	private void parseLine(String line) {
		StringTokenizer st = new StringTokenizer(line, " .", false);
		// first two columns (with a '.' in between) are the timestamp (sec and usec)
		long timestamp = 0;
		try {
			int timestampSecs = Integer.parseInt(st.nextToken()); 
			int timestampUSecs = Integer.parseInt(st.nextToken());
			timestamp = (long)timestampSecs*1000000 + timestampUSecs;
		}
		catch (NoSuchElementException e) {
			logger.warn("Short line with incomplete timestamp, ignoring line");
			return;
		}
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
				logger.debug("Averaging over " + curSample[0].size() + " values for the last sample");
				for (int i=0; i<MAX_LINES; i++) {
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
				// and put into all sinks
				if (logger.isDebugEnabled()) 
					for (int i=0; i<MAX_LINES; i++)
						logger.debug("Sample for timestamp " + lastSampleAt + ", number " + numSamples +  
								", line " + i + " = " + lastSampleValues[i]);
		    	if (sinks != null)
		    		for (ListIterator j = sinks.listIterator(); j.hasNext(); ) {
		    			ListenerCombination l = (ListenerCombination) j.next();
		    			for (int i=0; i<l.lines.length; i++)
		    				l.sinks[i].addSample(lastSampleValues[l.lines[i]], numSamples);
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
				for (int i=0; i<MAX_LINES; i++) {
					int val = allLines[i]; 
					logger.debug("Read value " + val + " on line " + i);
					curSample[i].add(new Integer(val));
				}
			}
		}
		else
			logger.debug("This is an empty reading containing only a timestamp");
	}
	
	/** This causes the reader to be shut down properly by calling stop() and making
	 * sure that all ressources are freed properly when this object is garbage collected.
	 * #see stop
	 */
	public void dispose() {
		stop();
		try {
			port.close();
		}
		catch (Exception e) {
			logger.error("Could not properly close input stream");
		}
	}

	
	/** This is a helper class that implements the Runnable interface internally. This way, one <b>has</b> to use the
	 * start and stop methods of the outer class to start the thread, which is cleaner from an interface point of view.
	 */
	private class RunHelper implements Runnable {
		public void run() {
			BufferedReader r = new BufferedReader(new InputStreamReader(port));
			
			try {
				String line = r.readLine();
				while (alive && line != null) {
					parseLine(line);
					line = r.readLine();
				}
				if (! alive)
					logger.debug("Background sampling thread terminated regularly due to request");
				else
					logger.warn("Background sampling thread received empty line! This should not happen when reading from a FIFO");
			}
			catch (Exception e) {
				logger.error("Could not read from file: " + e);
			}
		}
	}
	
	// TODO: create a general interface for a sensor source, but keep it simple --> timestamped, using TimeSeries
	
	
	/////////////////////////// test code begins here //////////////////////////////
	static class XYSink implements SamplesSink {
		XYSeries series = new XYSeries("Line", false);
		int num=0;
		ArrayList segment = null;
		XYSeries firstActiveSegment = null;
	
		public void addSample(double s, int index) {
			if (index != num++)
				logger.error("Sample index invalid");
			series.add(index, s);
			if (segment != null)
				segment.add(new Double(s));
		}
		public void segmentStart(int index) {
			if (firstActiveSegment == null)
				segment = new ArrayList();
		}
		public void segmentEnd(int index) {
			if (segment != null) {
				firstActiveSegment = new XYSeries("Segment", false);
				for (int i=0; i<segment.size(); i++)
					firstActiveSegment.add(i, ((Double) segment.get(i)).doubleValue());
			}
		}
	}

	static class SegmentSink implements SegmentsSink {
		static double[][] segs = new double[2][];
		static {
			segs[0] = null;
			segs[1] = null;
		}

		private int index;
		public SegmentSink(int index) {
			this.index = index;
		}
		public void addSegment(double[] segment, int startIndex) {
			logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);

			if (segs[index] == null)
				segs[index] = segment;
			else
				logger.warn("Already received segment " + index + ", this is a second significant one!");
		}
	}

	
	public static void main(String[] args) throws IOException {
		String filename = args[0];
		
		boolean graph = false;
		boolean paramSearch = false;
		if (args.length > 1 && args[1].equals("dographs"))
			graph = true;
		if (args.length > 1 && args[1].equals("paramsearch"))
			paramSearch = true;
		
		/////// test 1: just plot all 8 time series
		if (graph) {
			ParallelPortPWMReader r = new ParallelPortPWMReader(filename, 100);
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
			r.addSink(new int[] {0, 1, 2, 3, 4, 5, 6, 7}, t);
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
			}
		}
		
		/////// test 2: plot the 2 extracted segments from the first and the second device
		int[] samplerates = new int[] {64, 128, 256, 512}; // different sample rates
		double[] windowsizeFactors = new double[] {1 , 1/2f, 1/4f};  // 1 second, 1/2 second or 1/4 second for active detection
		double varthresholdMin = 50; // 50
		double varthresholdMax = 1000; // 1000
		double varthresholdStep = 50; // 10
		int numQuantLevelsMin = 8; // 2
		int numQuantLevelsMax = 8; // 32
		int numQuantLevelsStep = 1;
		int numCandidatesMin = 6; // 1
		int numCandidatesMax = 6; // 16
		int numCandidatesStep = 1;
		int cutOffFrequencyMin = 15; // 5
		int cutOffFrequencyMax = 15; // 50
		int cutOffFrequencyStep = 5;
		double[] windowOverlapFactors = new double[] {/*0, 1/8f, 1/4f, 1/3f,*/ 1/2f/*, 1, 3/2f*/}; 
		
		// this is ugly.....
		for (int i1=0; i1<samplerates.length; i1++) {
			int samplerate = samplerates[i1];
			// these are the defaults when not searching for parameters
			if (!paramSearch) {
				samplerate = 128; // Hz
				i1=samplerates.length; // makes the loop exit after this run
			}

			System.out.println("Sampling input data from " + filename + " with " + samplerate + " Hz");
			// can not sample now, because the time series aggregator needs all samples again...

			for (int i2=0; i2<windowsizeFactors.length; i2++) {
				int windowsize = (int) (samplerate*windowsizeFactors[i2]);
				// this is not yet searched, but restrict the minimum significant segment size to 1/2s
				int minsegmentsize = windowsize;
				// these are the defaults when not searching for parameters
				if (!paramSearch) {
					windowsize = samplerate/2; // 1/2 second
					minsegmentsize = windowsize; // 1/2 second
					i2=windowsizeFactors.length; // makes the loop exit after this run
				}
				
				for (double varthreshold=varthresholdMin; varthreshold<=varthresholdMax; 
						varthreshold+=(paramSearch ? varthresholdStep : varthresholdMax)) {
					// these are the defaults when not searching for parameters
					if (!paramSearch) {
						varthreshold = 350;
					}
					
					System.out.println("Searching for first significant segments with windowsize=" + windowsize + 
							", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold);
					ParallelPortPWMReader r2 = new ParallelPortPWMReader(filename, samplerate);
					TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
					TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
					r2.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
					r2.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
					aggr_a.addNextStageSegmentsSink(new SegmentSink(0));
					aggr_b.addNextStageSegmentsSink(new SegmentSink(1));
					aggr_a.setOffset(0);
					aggr_a.setMultiplicator(1/128f);
					aggr_a.setSubtractTotalMean(true);
					aggr_a.setActiveVarianceThreshold(varthreshold);
					aggr_b.setOffset(0);
					aggr_b.setMultiplicator(1/128f);
					aggr_b.setSubtractTotalMean(true);
					aggr_b.setActiveVarianceThreshold(varthreshold);
					r2.simulateSampling();

					if (SegmentSink.segs[0] != null && SegmentSink.segs[1] != null) {
						if (graph) {
							XYSeries seg1 = new XYSeries("Segment 1", false);
							for (int i=0; i<SegmentSink.segs[0].length; i++)
								seg1.add(i, SegmentSink.segs[0][i]);
							XYDataset dat1 = new XYSeriesCollection(seg1);
							JFreeChart chart1 = ChartFactory.createXYLineChart("Aggregated samples", "Number [100Hz]", 
									"Sample", dat1, PlotOrientation.VERTICAL, true, true, false);
							ChartUtilities.saveChartAsJPEG(new File("/tmp/aggrA.jpg"), chart1, 500, 300);

							XYSeries seg2 = new XYSeries("Segment 2", false);
							for (int i=0; i<SegmentSink.segs[1].length; i++)
								seg2.add(i, SegmentSink.segs[1][i]);
							XYDataset dat2 = new XYSeriesCollection(seg2);
							JFreeChart chart2 = ChartFactory.createXYLineChart("Aggregated samples", "Number [100Hz]", 
									"Sample", dat2, PlotOrientation.VERTICAL, true, true, false);
							ChartUtilities.saveChartAsJPEG(new File("/tmp/aggrB.jpg"), chart2, 500, 300);
						}

						/////// test 3: calculate and plot the coherence between the segments from test 2
						// make sure they have similar length
						int len = SegmentSink.segs[0].length <= SegmentSink.segs[1].length ? SegmentSink.segs[0].length : SegmentSink.segs[1].length;
						System.out.println("Using " + len + " samples for coherence computation");
						double[] s1 = new double[len];
						double[] s2 = new double[len];
						for (int i=0; i<len; i++) {
							s1[i] = SegmentSink.segs[0][i];
							s2[i] = SegmentSink.segs[1][i];
						}
						double[] coherence = Coherence.cohere(s1, s2, windowsize, 0);
						if (coherence != null) {
							if (graph) {
								XYSeries c = new XYSeries("Coefficients", false);
								for (int i=0; i<coherence.length; i++)
									c.add(i, coherence[i]);
								XYDataset c1 = new XYSeriesCollection(c);
								JFreeChart c2 = ChartFactory.createXYLineChart("Coherence", "", 
										"Sample", c1, PlotOrientation.VERTICAL, true, true, false);
								ChartUtilities.saveChartAsJPEG(new File("/tmp/coherence.jpg"), c2, 500, 300);
							}
		
							double coherenceMean = Coherence.mean(coherence);
							System.out.println("Coherence mean: " + coherenceMean + 
									" samplerate=" + samplerate + ", windowsize=" + windowsize + 
									", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold);
						}

						/////// test 4: calculate and compare the quantized FFT power spectra coefficients of the segments from test 2
						for (int i3=0; i3<windowOverlapFactors.length; i3++) {
							int fftpoints = samplerate;
							int windowOverlap = (int) (fftpoints*windowOverlapFactors[i3]);
							// these are the defaults when not searching for parameters
							if (!paramSearch) {
								windowOverlap = fftpoints/2;
								i3=windowOverlapFactors.length;
							}

							for (int numQuantLevels=numQuantLevelsMin; numQuantLevels<=numQuantLevelsMax; 
									numQuantLevels+=(paramSearch ? numQuantLevelsStep : numQuantLevelsMax)) {
								// these are the defaults when not searching for parameters
								if (!paramSearch) {
									numQuantLevels = 8;
								}
								for (int numCandidates=numCandidatesMin; numCandidates<=numCandidatesMax; 
									numCandidates+=(paramSearch ? numCandidatesStep : numCandidatesMax)) {
									// these are the defaults when not searching for parameters
									if (!paramSearch) {
										numCandidates = 6;
									}

									for (int cutOffFrequency=cutOffFrequencyMin; cutOffFrequency<=cutOffFrequencyMax; 
										cutOffFrequency+=(paramSearch ? cutOffFrequencyStep : cutOffFrequencyMax)) {
										// these are the defaults when not searching for parameters
										if (!paramSearch) {
											cutOffFrequency = 15; // Hz
										}
							
										int numMatches=0;
										int numWindows=0;

										for (int offset=0; offset<s1.length-fftpoints+1; offset+=fftpoints-windowOverlap) {
											double[] fftCoeff1 = FFT.fftPowerSpectrum(s1, offset, fftpoints);
											double[] fftCoeff2 = FFT.fftPowerSpectrum(s2, offset, fftpoints);
											// HACK HACK HACK: set DC components to 0
											fftCoeff1[0] = 0;
											fftCoeff2[0] = 0;
								
											int cand1[][] = Quantizer.generateCandidates(fftCoeff1, 0, Quantizer.max(fftCoeff1), numQuantLevels, numCandidates, false);
											int cand2[][] = Quantizer.generateCandidates(fftCoeff2, 0, Quantizer.max(fftCoeff2), numQuantLevels, numCandidates, false);
											// only compare until the cutoff frequency
											int max_ind = (int) (((float) (fftpoints * cutOffFrequency)) / samplerate) + 1;
											System.out.println("Only comparing the first " + max_ind + " FFT coefficients");
					
											boolean match = false;
											for (int i=0; i<cand1.length && !match; i++) {
												for (int j=0; j<cand2.length && !match; j++) {
													boolean equal = true;
													for (int k=0; k<max_ind && equal; k++) {
														if (cand1[i][k] != cand2[j][k])
															equal = false;
													}
													if (equal) {
														System.out.println("Match at i=" + i + ", j=" + j);
														match = true;
														numMatches++;
													}
												}
											}
											numWindows++;
										}
										System.out.println("Match: " + (float) numMatches / numWindows + " (" + numMatches + " out of " + numWindows + ")" + 
												" samplerate=" + samplerate + ", windowsize=" + windowsize + 
												", minsegmentsize=" + minsegmentsize + ", varthreshold=" + varthreshold +
												", windowoverlap=" + windowOverlap + ", numquantlevels=" + numQuantLevels +
												", numcandidates=" + numCandidates + ", cutofffrequ=" + cutOffFrequency);
									}
								}
							}
						}
					}
					else 
						System.err.println("Did not get 2 significant active segments");

				}
			}
		}
	}
}
