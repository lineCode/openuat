/* Copyright Rene Mayrhofer
 * File created 2006-03-20
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.test;

import java.io.*;

import org.openuat.apps.IPSecConfigHandler;

import junit.framework.*;

public class IPSecConfigHandlerTest extends TestCase {
	private IPSecConfigHandler handler = null;

	public IPSecConfigHandlerTest(String s) {
		super(s);
	}

	// TODO: activate me again when J2ME polish can deal with Java5 sources!
	//@Override
	public void setUp() {
		handler = new IPSecConfigHandler();
	}

	public void testGatewayProperty(){
		Assert.assertNull("Should be initialized with null", handler.getGateway());
		String test1 = "test 1";
		handler.setGateway(test1);
		Assert.assertEquals("Property not working properly", test1, handler.getGateway());
		String test2 = "test 2";
		handler.setGateway(test2);
		Assert.assertEquals("Property not working properly", test2, handler.getGateway());
	}

	public void testRemoteNetworkProperty(){
		Assert.assertNull("Should be initialized with null", handler.getRemoteNetwork());
		String test1 = "test 1";
		handler.setRemoteNetwork(test1);
		Assert.assertEquals("Property not working properly", test1, handler.getRemoteNetwork());
		String test2 = "test 2";
		handler.setRemoteNetwork(test2);
		Assert.assertEquals("Property not working properly", test2, handler.getRemoteNetwork());
	}

	public void testRemoteNetmaskProperty(){
		Assert.assertEquals("Should be initialized with 0", 0, handler.getRemoteNetmask());
		int test1 = 24;
		handler.setRemoteNetmask(test1);
		Assert.assertEquals("Property not working properly", test1, handler.getRemoteNetmask());
		int test2 = 32;
		handler.setRemoteNetmask(test2);
		Assert.assertEquals("Property not working properly", test2, handler.getRemoteNetmask());
	}
	
	public void testEnforceGatewaySet() {
		Assert.assertNull("Should be initialized with null", handler.getGateway());
		Assert.assertFalse("Should not work without a gateway set", handler.writeConfig(null));
	}
	
	public void testEnforceNoOverwriteGateway() {
		handler.setGateway("test");
		Assert.assertFalse("Should not work with a gateway set", handler.parseConfig(null));
	}

	public void testEnforceNoOverwriteRemoteNetwork() {
		handler.setRemoteNetwork("test");
		Assert.assertFalse("Should not work with a gateway set", handler.parseConfig(null));
	}
	
	public void testWriteAndParseGateway() throws IOException {
		String gate = "test gateway";
		handler.setGateway(gate);
		File temp = File.createTempFile("configFileTest", ".xml");
		temp.deleteOnExit();
		Assert.assertTrue("Could not write to temporary test file", temp.canWrite());
		Assert.assertTrue("Could not write config", handler.writeConfig(new FileWriter(temp)));
		
		handler = new IPSecConfigHandler();
		Assert.assertTrue("Could not read config", handler.parseConfig(new FileReader(temp)));
		Assert.assertEquals("Gateway read is different from gateway written", handler.getGateway(), gate);
	}

	public void testWriteAndParseGatewayAndNetwork() throws IOException {
		String gate = "test gateway 2";
		String net = "test network";
		int mask = 24;
		handler.setGateway(gate);
		handler.setRemoteNetwork(net);
		handler.setRemoteNetmask(mask);
		File temp = File.createTempFile("configFileTest", ".xml");
		temp.deleteOnExit();
		Assert.assertTrue("Could not write to temporary test file", temp.canWrite());
		Assert.assertTrue("Could not write config", handler.writeConfig(new FileWriter(temp)));
		
		handler = new IPSecConfigHandler();
		Assert.assertTrue("Could not read config", handler.parseConfig(new FileReader(temp)));
		Assert.assertEquals("Gateway read is different from gateway written", gate, handler.getGateway());
		Assert.assertEquals("Remote network read is different from remote network written", net, handler.getRemoteNetwork());
		Assert.assertEquals("Remote netmask read is different from remote netmask written", mask, handler.getRemoteNetmask());
	}

	public void testWriteAndParseGatewayAndNetworkAndCaDn() throws IOException {
		String gate = "test gateway 2";
		String net = "test network";
		String caDn = "my super duper CA";
		int mask = 24;
		handler.setGateway(gate);
		handler.setRemoteNetwork(net);
		handler.setRemoteNetmask(mask);
		handler.setCaDistinguishedName(caDn);
		File temp = File.createTempFile("configFileTest", ".xml");
		temp.deleteOnExit();
		Assert.assertTrue("Could not write to temporary test file", temp.canWrite());
		Assert.assertTrue("Could not write config", handler.writeConfig(new FileWriter(temp)));
		
		handler = new IPSecConfigHandler();
		Assert.assertTrue("Could not read config", handler.parseConfig(new FileReader(temp)));
		Assert.assertEquals("Gateway read is different from gateway written", gate, handler.getGateway());
		Assert.assertEquals("Remote network read is different from remote network written", net, handler.getRemoteNetwork());
		Assert.assertEquals("Remote netmask read is different from remote netmask written", mask, handler.getRemoteNetmask());
		Assert.assertEquals("CA DN read is different from CA DN written", caDn, handler.getCaDistinguishedName());
	}
}
