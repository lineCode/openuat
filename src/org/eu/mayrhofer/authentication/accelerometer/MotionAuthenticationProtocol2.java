/* Copyright Rene Mayrhofer
 * File created 2006-05-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.accelerometer;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.CKPOverUDP;
import org.eu.mayrhofer.authentication.exceptions.InternalApplicationException;
import org.eu.mayrhofer.sensors.FFT;
import org.eu.mayrhofer.sensors.Quantizer;
import org.eu.mayrhofer.sensors.SegmentsSink;

/** This is the first variant of the motion authentication protocol. It 
 * broadcasts candidate keys over UDP and creates shared keys with the
 * candidate key protocol.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class MotionAuthenticationProtocol2 extends CKPOverUDP implements SegmentsSink  {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(MotionAuthenticationProtocol2.class);

	/** The TCP port we use for this protocol. */
	public static final int UdpPort = 54322;
	
	public static final String MulticastGroup = "228.10.10.1";
	
	private int fftPoints = 128;
	private int numQuantLevels = 8;
	private int numCandidates = 6;
	private int cutOffFrequency = 15; // Hz
	private int windowOverlapFactor = 2; // fftpoints/windowOverlapFactor 

	/** Initializes the object, only setting useJSSE at the moment.
	 * 
	 * @param minMatchingParts
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 * @throws IOException 
	 */
	public MotionAuthenticationProtocol2(int sampleRate, int minMatchingParts, boolean useJSSE) throws IOException {
		// TODO: set minimum entropy
		super(UdpPort, UdpPort, MulticastGroup, null, true, false, minMatchingParts, 0, useJSSE);
	}

	/** The implementation of SegmentsSink.addSegment. It will be called whenever
	 * a significant active segment has been sampled completely, i.e. when the
	 * source has become quiescent again. 
	 * 
	 * This implementation immediately computes the sliding FFT windows, quantizes
	 * the coefficients, and sends out candidate key parts. 
	 * @throws IOException 
	 * @throws InternalApplicationException 
	 */
	public void addSegment(double[] segment, int startIndex) {
		logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);

		// TODO: this is actually the other way around....
		int sampleRate = fftPoints;
		
		// only compare until the cutoff frequency
		int max_ind = (int) (((float) (fftPoints * cutOffFrequency)) / sampleRate) + 1;
		System.out.println("Only comparing the first " + max_ind + " FFT coefficients");
		int numMatches = 0, numWindows = 0;
		for (int offset=0; offset<segment.length-fftPoints+1; offset+=fftPoints-fftPoints/windowOverlapFactor) {
			double[] fftCoeff1 = FFT.fftPowerSpectrum(segment, offset, fftPoints);
			// HACK HACK HACK: set DC components to 0
			fftCoeff1[0] = 0;
			int[][] cand = Quantizer.generateCandidates(fftCoeff1, 0, Quantizer.max(fftCoeff1), numQuantLevels, numCandidates, false);
			// and transform to byte array - we certainly use less than 256 quantization stages, so just byte-cast
			byte[][] candBytes = new byte[numCandidates][];
			for (int i=0; i<numCandidates; i++) {
				candBytes[i] = new byte[cand[i].length];
				for (int j=0; j<cand[i].length; j++)
					candBytes[i][j] = (byte) cand[i][j];
			}
			// TODO: estimate entropy
			try {
				addCandidates(candBytes, 0);
			} catch (InternalApplicationException e) {
				logger.error("Could not add candidates: " + e);
			} catch (IOException e) {
				logger.error("Could not add candidates: " + e);
			}
		}
	}

	protected void protocolSucceededHook(InetAddress remote, byte[] sharedSessionKey) {
		
	}

	protected void protocolFailedHook(InetAddress remote, Exception e, String message) {
		
	}

	protected void protocolProgressHook(InetAddress remote, int cur, int max, String message) {
		
	}
}
