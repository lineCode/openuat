/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

/**
 * This interface defines a listener for authentication events. 
 * @author Rene Mayrhofer
 * @version 1.1, changes since 1.0: added AuthenticationStarted method
 */
public interface AuthenticationProgressHandler {
	/** Upon successful authentication, the established shared key can be used
	 * with the remote host. The type of the remoteHost object depends on the sender
	 * of the event, e.g. an InetAddress object for HostProtocolHandler generated
	 * events, but an Integer for DongleProtocolHandler generated events (encapsulating
	 * the remote relate id).
	 * 
	 * @param sender The object which sent this event.
	 * 
	 * @param remote The remote end with which the authentication is performed. 
	 * Depends on the sender of the event.
	 * 
	 * @param result The result, if any, of the successful authentication. This can
	 * e.g. be a shared key or a set of keys or can even be null if the authentication
	 * event is enough to signal successful authentication.
	 */
	public void AuthenticationSuccess(Object sender, Object remote, Object result);

	/**
	 Upon authentication failure, an exception might have been thrown and a
	 message might have been created.
	 * @param sender The object which sent this event.
	 * 
	 * @param e Reason for the failue, can be null.
	 * @param msg Reaseon for the failue, can be null */
	public void AuthenticationFailure(Object sender, Object remote, Exception e,
			String msg);

	/** This event is raised during the authentication protocol to indicate progress.
	 * 
	 * @param sender The object which sent this event.
	 * 
	 * @param remote The remote end with which the authentication is performed.
	 * @param cur The current stage in the authentication.
	 * @param max The maximum number of stages.
	 * @param msg If not null, a message describing the last successful stage.
	 */
	public void AuthenticationProgress(Object sender, Object remote, int cur, int max,
			String msg);
	
	/** This event is raised when the authentication protocol is started, to 
	 * indicate that further events might follow.
	 *
	 * @param sender The object which sent this event.
	 * 
	 * @param remote The remote end with which the authentication is performed. 
	 * Depends on the sender of the event.
	 * 
	 * @return true if the handler accepts this authentication to be started,
	 *         false to "veto" it. If any of the registered handlers (which are
	 *         called in the order in which they were registered) returns 
	 *         false, the (incoming or outgoing) authentication is aborted.
	 */
	public boolean AuthenticationStarted(Object sender, Object remote);
}
