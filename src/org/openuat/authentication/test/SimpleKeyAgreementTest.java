/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.test;

import org.openuat.authentication.*;
import org.openuat.authentication.exceptions.*;

import java.math.BigInteger;
import junit.framework.*;

public class SimpleKeyAgreementTest extends TestCase {
	
	protected boolean useJSSE = true;
	protected boolean useJSSE2 = true;

    public static boolean compareByteArray(byte[] b1, byte[] b2)
    {
        for (int i = 0; i < b1.length && i < b2.length; i++)
        {
            if (b1[i] != b2[i])
                return false;
        }
        return true;
    }

    public static boolean compareByteArray(byte[] b1, short[] b2)
    {
        for (int i = 0; i < b1.length && i < b2.length; i++)
        {
        	short byteUnsigned = b1[i];
        	if (byteUnsigned < 0)
        		byteUnsigned += 0x100;
            if (b2[i] != byteUnsigned)
                return false;
        }
        return true;
    }
    
	public SimpleKeyAgreementTest(String s) {
		super(s);
	}
	
	/*public void testManualShortToByteArrayConversion()
	{
		Assert.assertTrue(compareByteArray(SimpleKeyAgreement.skip1024Modulus, SimpleKeyAgreement.skip1024Modulus));
	}*/

	public void testDHParameters() 
		{
			/* these checks are according to Practical Cryptography, p. 218:
			   check (p, q, g) that:
			   - p and q are prime, p is 256 bits long, p is sufficiently large
			   - q is a divisor of (p - 1)
			   - g != 1 and g^q = 1
			*/
			Assert.assertTrue(SimpleKeyAgreement.skip1024Modulus.isProbablePrime(100));
			// TODO: where is my q ??
			//Assert.True(SimpleKeyAgreement.q.isProbablePrime(100));
			Assert.assertFalse(SimpleKeyAgreement.skip1024Modulus.bitLength() < 1024);
			// TODO: where is my q ??
			//Assert.True(SimpleKeyAgreement.skip1024Modulus.subtract(BigInteger.ONE).mod(q).Equals(BigInteger.ZERO));
			Assert.assertFalse(SimpleKeyAgreement.skip1024Base.equals(BigInteger.ONE));
			// TODO: where is my q ??
			//Assert.True(SimpleKeyAgreement.skip1024Base.pow(q).Equals(BigInteger.ONE));
		}

		public void testStates_Initialize() throws InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);
			
			// we should be able to initialize at every time
			ka.init(useJSSE);
			ka.init(useJSSE);
		}
		
		public void testStates_getPublicKey_correctState() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);
			
			// we can get the key once directly after initialization
			ka.getPublicKey();
		}

		public void testStates_getPublicKey_wrongState() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);
			
			// we can get the key once directly after initialization
			ka.getPublicKey();
			// this should produce an exception
			try {
				ka.getPublicKey();
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}

		public void testStates_addRemotePublicKey_correctState() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);
			// the second object is just for getting a valid public key
			SimpleKeyAgreement ka2 = new SimpleKeyAgreement(useJSSE2);
			
			ka1.getPublicKey();
			byte[] pubKey2 = ka2.getPublicKey();
			
			// this should work since we are in the correct state
			ka1.addRemotePublicKey(pubKey2);
		}

		public void testStates_addRemotePublicKey_wrongState1() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);
			// the second object is just for getting a valid public key
			SimpleKeyAgreement ka2 = new SimpleKeyAgreement(useJSSE2);
			
			byte[] pubKey2 = ka2.getPublicKey();
			
			// this should not work since we are not in the correct state
			try {
				ka1.addRemotePublicKey(pubKey2);
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}

		public void testStates_addRemotePublicKey_wrongState2() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);
			// the second object is just for getting a valid public key
			SimpleKeyAgreement ka2 = new SimpleKeyAgreement(useJSSE2);
			
			ka1.getPublicKey();
			byte[] pubKey2 = ka2.getPublicKey();
			
			// this should work since we are in the correct state
			ka1.addRemotePublicKey(pubKey2);
			// but the second attempt shouldn't
			try {
				ka1.addRemotePublicKey(pubKey2);
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}

     public void testStates_getSessionKey_wrongState1() throws InternalApplicationException
     {
         SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);
         // should not work, we have noto yet complete the agreement
			try {
				ka1.getSessionKey();
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
     }

     public void testStates_getSessionKey_wrongState2() throws KeyAgreementProtocolException, InternalApplicationException
     {
         SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);

         ka1.getPublicKey();

         // should not work, we have noto yet complete the agreement
			try {
				ka1.getSessionKey();
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
     }

     public void testStates_getSessionKey_correctState() throws KeyAgreementProtocolException, InternalApplicationException
     {
         SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);
         // the second object is just for getting a valid public key
         SimpleKeyAgreement ka2 = new SimpleKeyAgreement(useJSSE2);

         ka1.getPublicKey();
         byte[] pubKey2 = ka2.getPublicKey();

         // this should work since we are in the correct state
         ka1.addRemotePublicKey(pubKey2);

         // now it should work
         ka1.getSessionKey();
     }

     public void testStates_getAuthenticationKey_wrongState1() throws InternalApplicationException
     {
         SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);
         // should not work, we have noto yet complete the agreement
			try {
		         ka1.getAuthenticationKey();
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
     }

     public void testStates_getAuthenticationKey_wrongState2() throws KeyAgreementProtocolException, InternalApplicationException
     {
         SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);

         ka1.getPublicKey();

         // should not work, we have noto yet complete the agreement
			try {
		         ka1.getAuthenticationKey();
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
     }

     public void testStates_getAuthenticationKey_correctState() throws KeyAgreementProtocolException, InternalApplicationException
     {
         SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);
         // the second object is just for getting a valid public key
         SimpleKeyAgreement ka2 = new SimpleKeyAgreement(useJSSE2);

         ka1.getPublicKey();
         byte[] pubKey2 = ka2.getPublicKey();

         // this should work since we are in the correct state
         ka1.addRemotePublicKey(pubKey2);

         // now it should work
         ka1.getAuthenticationKey();
     }

		public void testMessages_addRemotePublicKey_equalPublicKeyAdded() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);
			
			byte[] pubKey = ka.getPublicKey();
			// this should produce an exception, since the public key is our own (but the state is correct)
			try {
				ka.addRemotePublicKey(pubKey);
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}

		public void testMessages_addRemotePublicKey_invalidPublicKeyAdded1() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);

			// for the correct state			
			ka.getPublicKey();
			// this should produce an exception, since the public key is 1 (invalid)
			try {
				ka.addRemotePublicKey(BigInteger.ONE.toByteArray());
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}

		public void testMessages_addRemotePublicKey_invalidPublicKeyAdded2() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);

			// for the correct state			
			ka.getPublicKey();
			// this should produce an exception, since the public key is 0 (invalid)
			try {
				ka.addRemotePublicKey(BigInteger.ZERO.toByteArray());
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}

		public void testMessages_addRemotePublicKey_invalidPublicKeyAdded3() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);

			// for the correct state			
			ka.getPublicKey();
			// this should produce an exception, since the public key is >= p (invalid)
			try {
				ka.addRemotePublicKey(SimpleKeyAgreement.skip1024Modulus.toByteArray());
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}

		public void testMessages_addRemotePublicKey_invalidPublicKeyAdded4() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);

			// for the correct state			
			ka.getPublicKey();
			// this should produce an exception, since the public key is >= p (invalid)
			try {
				ka.addRemotePublicKey(SimpleKeyAgreement.skip1024Modulus.add(BigInteger.ONE).toByteArray());
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}

		public void testMessages_addRemotePublicKey_invalidPublicKeyAdded5() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ka = new SimpleKeyAgreement(useJSSE);

			// for the correct state			
			ka.getPublicKey();
			// this should produce an exception, since the public key is null (invalid)
			try {
				ka.addRemotePublicKey(null);
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}
		
		private void testCorrectAgreement(SimpleKeyAgreement ag1, SimpleKeyAgreement ag2) 
				throws KeyAgreementProtocolException, InternalApplicationException {
			byte[] msg1 = ag1.getPublicKey();
			byte[] msg2 = ag2.getPublicKey();
			
			ag1.addRemotePublicKey(msg2);
			ag2.addRemotePublicKey(msg1);
			
			// should be true
			Assert.assertTrue(compareByteArray(ag1.getAuthenticationKey(), ag2.getAuthenticationKey())); 
			Assert.assertTrue(compareByteArray(ag1.getSessionKey(), ag2.getSessionKey())); 
			Assert.assertNotSame(ag1.getAuthenticationKey(), ag2.getAuthenticationKey());
			Assert.assertNotSame(ag1.getSessionKey(), ag2.getSessionKey());

			// should be false
			Assert.assertFalse(compareByteArray(ag1.getAuthenticationKey(), ag1.getSessionKey())); 
			Assert.assertFalse(compareByteArray(ag2.getAuthenticationKey(), ag2.getSessionKey())); 
			
			// should be 32 bytes (32*8 = 256 bits)
			Assert.assertTrue(ag1.getAuthenticationKey().length == 32);
			Assert.assertTrue(ag2.getAuthenticationKey().length == 32);
			Assert.assertTrue(ag1.getSessionKey().length == 32);
			Assert.assertTrue(ag2.getSessionKey().length == 32);
		}
		
		public void testCorrectAgreement() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ag1 = new SimpleKeyAgreement(useJSSE);
			SimpleKeyAgreement ag2 = new SimpleKeyAgreement(useJSSE2);

			testCorrectAgreement(ag1, ag2);
		}
	
		public void testCorrectAgreements_PermanentLocal() throws KeyAgreementProtocolException, InternalApplicationException
		{
			SimpleKeyAgreement ag1 = new SimpleKeyAgreement(useJSSE, true);
			SimpleKeyAgreement ag2 = new SimpleKeyAgreement(useJSSE2);
			SimpleKeyAgreement ag3 = new SimpleKeyAgreement(useJSSE2);

			testCorrectAgreement(ag1, ag2);
			
			// now a second round with ag1 - reset
			ag1.resetRemotePart();
			testCorrectAgreement(ag1, ag3);
		}

		public void testStates_resetRemotePart_notAllowed() throws InternalApplicationException
		{
			SimpleKeyAgreement ka1 = new SimpleKeyAgreement(useJSSE);
			
			// this should not work since we are not in permanent mode
			try {
				ka1.resetRemotePart();
				Assert.fail();
			} catch (KeyAgreementProtocolException e) {
				Assert.assertTrue(true);
			}
		}
}
