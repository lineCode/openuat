/* Copyright Rene Mayrhofer
 * File created 2006-05-03
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.authentication.accelerometer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.eu.mayrhofer.authentication.DHOverTCPWithVerification;
import org.eu.mayrhofer.authentication.InterlockProtocol;
import org.eu.mayrhofer.sensors.Coherence;
import org.eu.mayrhofer.sensors.ParallelPortPWMReader;
import org.eu.mayrhofer.sensors.SegmentsSink;
import org.eu.mayrhofer.sensors.TimeSeriesAggregator;

/** This is the first variant of the motion authentication protocol. It
 * uses Diffie-Hellman key agreement with verification that the shared keys
 * are equal on both hosts by sending the full time series segment through
 * interlock, encrypted with the shared key. THen both hosts compute the
 * coherence between the received time series segment and their own and continue
 * when it exceeds a threshold. 
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class MotionAuthenticationProtocol1 extends DHOverTCPWithVerification implements SegmentsSink {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(MotionAuthenticationProtocol1.class);

	/** The TCP port we use for this protocol. */
	public static final int TcpPort = 54322;

	/** This holds our local segment, as soon as we have received it from the
	 * segment source. It is modified by addSegment and read by AsyncInterlockHelper#run
	 * from two different threads. Synchronization happens via localSegmentLock.
	 * @see #addSegment(double[], int)
	 * @see AsyncInterlockHelper#run
	 * @see #localSegmentLock
	 */
	private double[] localSegment = null;
	/** This is only used as a synchronization lock for accessing localSegment from
	 * different threads, and has no other use.
	 */
	private Object localSegmentLock = new Object();
	
	/** This holds the remote segment, as soon as it has been received via interlock
	 * from the remote host.
	 */
	private double[] remoteSegment = null;
	
	/** The current threshold for the coherence. If it is higher, the two segments
	 * are considered similar enough.
	 */
	private double coherenceThreshold = 0.50;
	
	/** If set to true, the thread started by startVerification will not terminate
	 * but will check continuously, only calling the hook methods in this class
	 * itself. This is mainly used for debugging, and should not be set to true for
	 * "real-world" operation!
	 */
	private boolean continuousChecking = false;
	
	/** This is only used to remember the coherence mean that has been computed last.
	 * It should only be used for debugging, because the decision if verification 
	 * succeeded or not is made within this class.
	 */
	private double lastCoherenceMean = 0;
	
	/** This variable is only used for passing the socket from startVerification to the
	 * thread that does runs the interlock protocol, AsyncInterlockHelper#run.
	 * @see #startVerification(byte[], InetAddress, String, Socket)
	 * @see AsyncInterlockHelper#run
	 */
	private InetAddress remote = null;
	
	/** This variable is only used for passing the socket from startVerification to the
	 * thread that does runs the interlock protocol, AsyncInterlockHelper#run.
	 * @see #startVerification(byte[], InetAddress, String, Socket)
	 * @see AsyncInterlockHelper#run
	 */
	private Socket socketToRemote = null;
	
	/** Holds the thread object that is used to run the interlock protocol asynchronously.
	 * It is initialized and started by startVerification, and executes 
	 * AsyncInterlockHelper#run.
	 * @see #startVerification(byte[], InetAddress, String, Socket)
	 * @see AsyncInterlockHelper
	 */
	private Thread interlockRunner = null;
	
	/** Initializes the object, only setting useJSSE at the moment.
	 * 
	 * @param useJSSE If set to true, the JSSE API with the default JCE provider of the JVM will be used
	 *                for cryptographic operations. If set to false, an internal copy of the Bouncycastle
	 *                Lightweight API classes will be used.
	 */
	public MotionAuthenticationProtocol1(boolean useJSSE) {
		super(TcpPort, false, null, useJSSE);
	}
	
	/** Called by the base class when the object is reset to idle state. Resets 
	 * localSegment and remoteSegment to null. */
	protected void resetHook() {
		// idle again --> no segments to compare
		localSegment = null;
		remoteSegment = null;
	}
	
	/** Called by the base class when the whole authentication protocol succeeded. 
	 * Does nothing. */
	protected void protocolSucceededHook(InetAddress remote, 
			Object optionalRemoteId, String optionalParameterFromRemote, 
			byte[] sharedSessionKey, Socket toRemote) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolSucceededHook called, remote host reported coherence value of " + optionalParameterFromRemote);
		System.out.println("SUCCESS");
	}
	
	/** Called by the base class when the whole authentication protocol failed. 
	 * Does nothing. */
	protected void protocolFailedHook(InetAddress remote, Object optionalRemoteId, 
			Exception e, String message) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolFailedHook called");
		System.out.println("FAILURE");
	}
	
	/** Called by the base class when the whole authentication protocol shows progress. 
	 * Does nothing. */
	protected void protocolProgressHook(InetAddress remote, 
			Object optionalRemoteId, int cur, int max, String message) {
		// nothing special to do, events have already been emitted by the base class
		logger.debug("protocolProgressHook called");
	}
	
	/** Called by the base class when shared keys have been established and should be verified now.
	 * In this implementation, verification is done listening for significant motion segments and
	 * exchanging them via interlock. 
	 * @see #interlockRunner
	 * @see AsyncInterlockHelper
	 */
	protected void startVerification(byte[] sharedAuthenticationKey, 
			InetAddress remote, String param, Socket socketToRemote) {
		logger.info("startVerification hook called with " + remote + ", param " + param);
	
		if (interlockRunner == null) {
			this.remote = remote;
			this.socketToRemote = socketToRemote;
			interlockRunner = new Thread(new AsyncInterlockHelper(sharedAuthenticationKey));
			interlockRunner.start();
		}
		else {
			logger.warn("Interlock thread already running, can not process two interlock " +
					"protocol runs concurrently. Terminating second request.");
		}
	}

	/** This helper function calls Coherence.cohere on localSegment and remoteSegment,
	 * but only on the first part of both with the minimum length. That is, it trims the
	 * larger of the two to have the same length as the smaller. 
	 * @return true if the mean of the coherence function is larger than the threshold,
	 *         false otherwise.
	 * @see #coherenceThreshold
	 */
	private boolean checkCoherence() {
		if (localSegment == null || remoteSegment == null) {
			throw new RuntimeException("Did not yet receive both segments, skipping comparing for now");
		}
		
		int len = localSegment.length <= remoteSegment.length ? localSegment.length : remoteSegment.length;
		System.out.println("Using " + len + " samples for coherence computation");
		double[] s1 = new double[len];
		double[] s2 = new double[len];
		for (int i=0; i<len; i++) {
			s1[i] = localSegment[i];
			s2[i] = remoteSegment[i];
		}
		double[] coherence = Coherence.cohere(s1, s2, 128, 0);
		if (coherence == null) {
			logger.warn("Coherence not computed, no match");
			return false;
		}
		
		lastCoherenceMean = Coherence.mean(coherence);
		System.out.println("Coherence mean: " + lastCoherenceMean);
		
		return lastCoherenceMean > coherenceThreshold;
	}
	
	/** The implementation of SegmentsSink.addSegment. It will be called whenever
	 * a significant active segment has been sampled completely, i.e. when the
	 * source has become quiescent again.
	 * @see #localSegment
	 * @see #localSegmentLock
	 */
	public void addSegment(double[] segment, int startIndex) {
		logger.info("Received segment of size " + segment.length + " starting at index " + startIndex);
		synchronized (localSegmentLock) {
			localSegment = segment;
			localSegmentLock.notify();
		}
	}
	
	/** This method only calls the base class startAuthentication method.
	 * 
	 * @param remoteHost The remote host with which to authentication
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public void startAuthentication(String remoteHost) throws UnknownHostException, IOException {
		logger.info("Starting authentication with " + remoteHost);
		startAuthentication(remoteHost, null);
	}
	
	/** Sets the coherence threshold. 
	 * @param coherenceThreshold The threshold over which a coherence value will be taken
	 *                           as valid (i.e. shaken within the same hand). Must be
	 *                           between 0 and 1.
	 * @see #coherenceThreshold 
	 */
	public void setCoherenceThreshold(double coherenceThreshold) {
		if (coherenceThreshold < 0 || coherenceThreshold > 1)
			throw new IllegalArgumentException("Coherence threshold must be in [0;1].");
		
		logger.debug("Setting coherence threshold to " + coherenceThreshold);
		this.coherenceThreshold = coherenceThreshold;
	}
	
	/** Returns the current value of the coherence threshold. 
	 * @return The current coherence threshold.
	 * @see #coherenceThreshold
	 */
	public double getCoherenceThreshold() {
		return coherenceThreshold;
	}
	
	/** Enable or disable continuous checking.
	 * @param continuousChecking Only set to true after reading the description
	 *                           of the member variable continuousChecking. Generally
	 *                           leave to false (the default).
	 * @see #continuousChecking
	 */
	public void setContinuousChecking(boolean continuousChecking) {
		if (continuousChecking) {
			logger.warn("Enabling continuous checking mode! This should only be used for debugging, and not in production");
		}
		this.continuousChecking = continuousChecking;
	}
	
	/** Returns the current value of continuousChecking.
	 * @return The current value of continuousChecking.
	 * @see #continuousChecking
	 */
	public boolean getContinuousChecking() {
		return continuousChecking;
	}
	
	/** This is a helper class for executing the interlock protocol in the background.
	 * It is started by startVerification.
	 */
	private class AsyncInterlockHelper implements Runnable {
		private byte[] sharedAuthenticationKey;
		
		AsyncInterlockHelper(byte[] authKey) {
			this.sharedAuthenticationKey = authKey;
		}
		
		public void run() {
			try {
				do {
					// first wait for the local segment to be received to start the interlock protocol
					logger.debug("Waiting for local segment");
					synchronized(localSegmentLock) {
						while (localSegment == null) {
							try {
								localSegmentLock.wait();
							}
							catch (InterruptedException e) {}
						}
					}
					logger.debug("Local segment sampled, starting interlock protocol");
			
					// TODO: make configurable??? mabye not necessary
					int rounds = 2;

					// now generate our message for the interlock protocol segments
					// TODO: optimize me for smaller arrays!
					String tmp = "";
					synchronized (localSegmentLock) {
						for (int i=0; i<localSegment.length; i++) {
							tmp += Double.toString(localSegment[i]);
							if (i<localSegment.length-1)
								tmp+=" ";
						}
					}
					byte[] localPlainText = tmp.getBytes();
					logger.debug("My segment is " + localPlainText.length + " bytes long");
			
					// exchange with the remote host
					byte[] remotePlainText = InterlockProtocol.interlockExchange(localPlainText, 
							socketToRemote.getInputStream(), socketToRemote.getOutputStream(), 
							sharedAuthenticationKey, rounds, false, 0, useJSSE);
					if (remotePlainText == null) {
						logger.warn("Interlock protocol failed, can not continue to compare with remote segment");
						if (! continuousChecking) {
							verificationFailure(null, null, null, "Interlock protocol failed");
							return;
						}
						else {
							// in case of checking continously, just call or own hook (for derived classes)
							protocolFailedHook(remote, null, null, "Interlock protocol failed");
							continue;
						}
					}
			
					// and check the received remote segment, compare it with our local segment
					logger.debug("Remote segment is " + remotePlainText.length + " bytes long");
					StringTokenizer st = new StringTokenizer(new String(remotePlainText), " ");
					remoteSegment = new double[st.countTokens()];
					int i=0;
					while (st.hasMoreTokens()) {
						remoteSegment[i++] = Float.parseFloat(st.nextToken());
					}
					logger.debug("remote segment is " + remoteSegment.length + " elements long");
			
					boolean coherence = checkCoherence();
					System.out.println("COHERENCE MATCH: " + coherence + "(computed " + 
							lastCoherenceMean + " and threshold is " + coherenceThreshold + ")");
			
					// final decision
					if (coherence) { 
						if (! continuousChecking)
							verificationSuccess(null, Double.toString(lastCoherenceMean));
						else
							protocolSucceededHook(remote, null, Double.toString(lastCoherenceMean), null, null);
					}
					else {
						if (! continuousChecking)
							verificationFailure(null, null, null, "Coherence is below threshold, time series are not similar enough");
						else
							protocolFailedHook(remote, null, null, "Coherence is below threshold, time series are not similar enough");
					}

					localSegment = remoteSegment = null;
				} while (continuousChecking);
				// HACK HACK HACK to make the application exit
				//stopServer();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			// thread finished, so allow next one to start
			interlockRunner = null;
		}
	}
	
	
	/////////////////// testing code begins here ///////////////
	public static void main(String[] args) throws IOException {
		int samplerate = 128; // Hz
		int windowsize = samplerate/2; // 1/2 second
		int minsegmentsize = windowsize; // 1/2 second
		double varthreshold = 350;
		ParallelPortPWMReader r = new ParallelPortPWMReader(args[0], samplerate);
		TimeSeriesAggregator aggr_a = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		TimeSeriesAggregator aggr_b = new TimeSeriesAggregator(3, windowsize, minsegmentsize);
		r.addSink(new int[] {0, 1, 2}, aggr_a.getInitialSinks());
		r.addSink(new int[] {4, 5, 6}, aggr_b.getInitialSinks());
		aggr_a.setOffset(0);
		aggr_a.setMultiplicator(1/128f);
		aggr_a.setSubtractTotalMean(true);
		aggr_a.setActiveVarianceThreshold(varthreshold);
		aggr_b.setOffset(0);
		aggr_b.setMultiplicator(1/128f);
		aggr_b.setSubtractTotalMean(true);
		aggr_b.setActiveVarianceThreshold(varthreshold);
		
		MotionAuthenticationProtocol1 ma1 = new MotionAuthenticationProtocol1(true); 
		MotionAuthenticationProtocol1 ma2 = new MotionAuthenticationProtocol1(true); 
		aggr_a.addNextStageSegmentsSink(ma1);
		aggr_b.addNextStageSegmentsSink(ma2);
		ma1.startServer();
		ma2.startAuthentication("localhost");
		
		r.simulateSampling();
	}
}
