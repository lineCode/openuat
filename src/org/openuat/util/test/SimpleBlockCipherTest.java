/* Copyright Rene Mayrhofer
 * File created 2008-12-16, refactored parts of SimpleBlockCipher
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.test;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.authentication.test.SimpleKeyAgreementTest;
import org.openuat.util.Hash;
import org.openuat.util.SimpleBlockCipher;

import junit.framework.Assert;
import junit.framework.TestCase;

public class SimpleBlockCipherTest extends TestCase {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger("org.openuat.util.test.SimpleBlockCipherTest" /*SimpleBlockCipher.class*/);

	protected boolean useJSSE = true;
	protected boolean useJSSE2 = true;

	public SimpleBlockCipherTest(String s) {
		super(s);
	}

	public void testParameterCheckEncrypt1() {
		SimpleBlockCipher p = new SimpleBlockCipher(useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[16], 128, null);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt2() {
		SimpleBlockCipher p = new SimpleBlockCipher(useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[16], 128, new byte[31]);
			Assert.fail();
		} catch (InternalApplicationException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt3() throws InternalApplicationException {
		SimpleBlockCipher p = new SimpleBlockCipher(useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[15], 128, new byte[32]);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	public void testParameterCheckEncrypt4() throws InternalApplicationException {
		SimpleBlockCipher p = new SimpleBlockCipher(useJSSE);
		try {
			// this should not work with incorrect parameters
			p.encrypt(new byte[0], 127, new byte[32]);
			Assert.fail();
		} catch (IllegalArgumentException e) {
			Assert.assertTrue(true);
		}
	}

	public void testEncryptDecrypt_SingleBlock() throws InternalApplicationException {
		byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		SimpleBlockCipher p1 = new SimpleBlockCipher(useJSSE);
		SimpleBlockCipher p2 = new SimpleBlockCipher(useJSSE2);
		byte[] plainText = new byte[16];
		for (int i=0; i<plainText.length; i++)
			plainText[i] = (byte) (plainText.length-1-i);
		
		byte[] cipherText = p1.encrypt(plainText, 128, sharedKey);
		Assert.assertTrue("cipher text has invalid length", cipherText.length == plainText.length);
		byte[] plainText2 = p2.decrypt(cipherText, 128, sharedKey);
		Assert.assertTrue("decrypted plain text has invalid length", plainText2.length == plainText.length);
		Assert.assertTrue("decrypted plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
	}

	public void testEncryptDecrypt_MultipleBlocks() throws InternalApplicationException {
		byte[] sharedKey = new byte[32];
		for (int i=0; i<sharedKey.length; i++)
			sharedKey[i] = (byte) i;
		SimpleBlockCipher p1 = new SimpleBlockCipher(useJSSE);
		SimpleBlockCipher p2 = new SimpleBlockCipher(useJSSE2);
		// 17 bytes is more than one block, so the protocol should switch from ECB to CBC mode
		byte[] plainText = new byte[17];
		for (int i=0; i<plainText.length; i++)
			plainText[i] = (byte) (plainText.length-1-i);
		
		byte[] cipherText = p1.encrypt(plainText, plainText.length*8, sharedKey);
		byte[] plainText2 = p2.decrypt(cipherText, plainText.length*8, sharedKey);
		Assert.assertTrue("decrypted plain text has invalid length", plainText2.length == plainText.length);
		Assert.assertTrue("decrypted plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plainText, plainText2));
	}
	
	public void testEncryptDecrypt_MultipleBlocks_String() throws InternalApplicationException {
		String plaintext = "The answer is 42.";
		String password = "MC.SMS5";
		
		byte[] sharedKey = Hash.SHA256(password.getBytes(), useJSSE);
		logger.info("Hashed key from password '" + password + "' is '" + new String(Hex.encodeHex(sharedKey)) + "'");
		
		SimpleBlockCipher cbcEncrypt = new SimpleBlockCipher(useJSSE); 
		SimpleBlockCipher cbcDecrypt = new SimpleBlockCipher(useJSSE2);
		logger.info("Encrypting plain text '" + plaintext + "' in binary form '" + new String(Hex.encodeHex(plaintext.getBytes())) + "'");
		
		byte[] cipherText = cbcEncrypt.encrypt(plaintext.getBytes(), plaintext.getBytes().length*8, sharedKey);
		logger.info("Encrypted with CBC: '" + new String(Hex.encodeHex(cipherText)) + "'");
		byte[] plainBytes2 = cbcDecrypt.decrypt(cipherText, plaintext.getBytes().length*8, sharedKey);
		String plaintext2 = new String(plainBytes2);
		logger.info("Decrypted with CBC: '" + new String(Hex.encodeHex(plainBytes2)) + "' in text form '" + plaintext2 + "'");

		Assert.assertTrue("decrypted plain text has invalid length", plainBytes2.length == plaintext.getBytes().length);
		Assert.assertTrue("decrypted plain text does not match original", SimpleKeyAgreementTest.compareByteArray(plaintext.getBytes(), plainBytes2));
		Assert.assertEquals("decrypted and decoded plain text does not match original", plaintext, plaintext2);
	}
}
