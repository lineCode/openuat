/* Copyright Rene Mayrhofer
 * File created 2007-01-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.j2me;

import java.io.IOException;
import java.util.Vector;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.bluetooth.*;

import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.BluetoothPeerManager;
import org.openuat.util.BluetoothRFCOMMServer;
import org.openuat.util.BluetoothSupport;

public class BluetoothDemo extends MIDlet implements CommandListener,
		BluetoothPeerManager.PeerEventsListener {
	List main_list;

	List dev_list;

	List serv_list;

	Command exit;

	Command back;

	Display display;
	
	BluetoothPeerManager peerManager;
	
	BluetoothRFCOMMServer rfcommServer;
	
	public BluetoothDemo() {
		display = Display.getDisplay(this);

		if (! BluetoothSupport.init()) {
			do_alert("Could not initialize Bluetooth API", Alert.FOREVER);
			return;
		}

		try {
			do_alert("1", 1000);
			rfcommServer = new BluetoothRFCOMMServer(null, new UUID("b76a37e5e5404bf09c2a1ae3159a02d8", false), "J2ME Test Service", false, false);
			do_alert("2", 1000);
			rfcommServer.startListening();
			do_alert("Registered SDP service at " + rfcommServer.getRegisteredServiceURL(), Alert.FOREVER);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}
		} catch (IOException e) {
			do_alert("Error initializing BlutoothRFCOMMServer: " + e, Alert.FOREVER);
		}

		try {
			peerManager = new BluetoothPeerManager();
			peerManager.addListener(this);
		} catch (IOException e) {
			do_alert("Error initializing BlutoothPeerManager: " + e, Alert.FOREVER);
			return;
		}

			main_list = new List("Select Operation", Choice.IMPLICIT); //the main menu
			dev_list = new List("Select Device", Choice.IMPLICIT); //the list of devices
			serv_list = new List("Available Services", Choice.IMPLICIT); //the list of services
			exit = new Command("Exit", Command.EXIT, 1);
			back = new Command("Back", Command.BACK, 1);

			main_list.addCommand(exit);
			main_list.setCommandListener(this);
			dev_list.addCommand(exit);
			dev_list.setCommandListener(this);
			serv_list.addCommand(exit);
			serv_list.addCommand(back);
			serv_list.setCommandListener(this);

			main_list.append("Find Devices", null);
	}

	public void startApp() {
		display.setCurrent(main_list);
	}

	public void commandAction(Command com, Displayable dis) {
		if (com == exit) { //exit triggered from the main form
			if (rfcommServer != null)
				try {
					rfcommServer.stopListening();
				} catch (InternalApplicationException e) {
					do_alert("Could not de-register SDP service: " + e, Alert.FOREVER);
				}
			destroyApp(false);
			notifyDestroyed();
		}
		if (com == List.SELECT_COMMAND) {
			if (dis == main_list) { //select triggered from the main from
				if (main_list.getSelectedIndex() >= 0) { //find devices
					if (!peerManager.startInquiry(false)) {
						this.do_alert("Error in initiating search", 4000);
					}
					do_alert("Searching for devices...", Alert.FOREVER);
				}
			}
			if (dis == dev_list) { //select triggered from the device list
				if (dev_list.getSelectedIndex() >= 0) { //find services
					RemoteDevice[] devices = peerManager.getPeers();
					
					serv_list.deleteAll(); //empty the list of services in case user has pressed back
					UUID uuid = new UUID(0x1002); // publicly browsable services
					if (!peerManager.startServiceSearch(devices[dev_list.getSelectedIndex()], uuid)) {
						this.do_alert("Error in initiating search", 4000);
					}
					do_alert("Inquiring device for services...", Alert.FOREVER);
				}
			}
		}
		if (com == back) {
			if (dis == serv_list) { //back button is pressed in devices list
				display.setCurrent(dev_list);
			}
		}

	}

	public void do_alert(String msg, int time_out) {
		if (display.getCurrent() instanceof Alert) {
			((Alert) display.getCurrent()).setString(msg);
			((Alert) display.getCurrent()).setTimeout(time_out);
		} else {
			Alert alert = new Alert("Bluetooth");
			alert.setString(msg);
			alert.setTimeout(time_out);
			display.setCurrent(alert);
		}
	}

	public void pauseApp() {
	}

	public void destroyApp(boolean unconditional) {
	}

	public void inquiryCompleted(Vector newDevices) {
		for (int i=0; i<newDevices.size(); i++) {
			String device_name = BluetoothPeerManager.resolveName((RemoteDevice) newDevices.elementAt(i));
			this.dev_list.append(device_name, null);
			display.setCurrent(dev_list);
		}
	}

	public void serviceListFound(RemoteDevice remoteDevice, Vector services) {
		for (int x = 0; x < services.size(); x++)
			try {
				DataElement ser_de = ((ServiceRecord) services.elementAt(x))
						.getAttributeValue(0x100);
				String service_name = (String) ser_de.getValue();
				serv_list.append(service_name, null);
				display.setCurrent(serv_list);
			} catch (Exception e) {
				do_alert("Error in adding services ", 1000);
			}
	}
}
