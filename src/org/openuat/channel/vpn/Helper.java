/* Copyright Rene Mayrhofer
 * File created 2006-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.vpn;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class implements various helper methods that don' really fit elsewhere. 
 *
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class Helper {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger(Helper.class.getName());

	public final static String[] Interface_Names_Blacklist = new String[] { "vmnet", "lo" };
    
	public static LinkedList getAllLocalIps() throws SocketException {
		Enumeration ifaces = NetworkInterface.getNetworkInterfaces();
		LinkedList allAddrs = new LinkedList();
		while (ifaces.hasMoreElements()) {
			NetworkInterface iface = (NetworkInterface) ifaces.nextElement();
			logger.debug("Found local interface " + iface.getName());
			// check if that interface name is blacklisted
			boolean blacklisted = false;
			for (int i=0; i<Interface_Names_Blacklist.length; i++)
				if (iface.getName().startsWith(Interface_Names_Blacklist[i]))
					blacklisted = true;
			
			if (!blacklisted) {
				Enumeration addrs = iface.getInetAddresses();
				while (addrs.hasMoreElements()) {
					InetAddress addr = (InetAddress) addrs.nextElement();
					if (addr instanceof Inet6Address)
						logger.debug("Ignoring IPv6 address " + addr + " for now");
					else {
						logger.debug("Found address " + addr);
						allAddrs.add(addr.getHostAddress());
					}
				}
			}
			else
				logger.debug("Ignoring interface because it is blacklisted");
		}
		
		return allAddrs;
	}

}
