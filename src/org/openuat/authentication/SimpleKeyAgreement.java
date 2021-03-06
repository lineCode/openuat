/* Copyright Rene Mayrhofer
 * File created 2005-09
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication;

import org.openuat.authentication.exceptions.*;
import org.openuat.util.Hash;

import java.math.BigInteger;
import java.security.SecureRandom;

/** This class implements a simple key agreement protocol. Simple refers to the 
 * interface of this class, not its security. For a complete key agreement, the 
 * caller is expected to initialize the object, transmit the public key to the 
 * remote host, receive the remote public key and add it to this agreements and 
 * then get the shared authentication and session keys. Each caller is expected 
 * to handle the transmitted public keys and especially the private keys with 
 * care and not leak it to an outside class. The steps must be done in exactly 
 * this order or a KeyAgreementProtocolException will be thrown.
 * 
 * @author Rene Mayrhofer
 * @version 1.1, changes to 1.0: Now supports re-using the local key pair for
 *               subsequent key agreement runs. The instance needs to be 
 *               constructed for this, though (by setting the flag to true).
 */
public class SimpleKeyAgreement {
	/* These are our states of the key agreement protocol: 
	 - initialized means that the internal nonce has been generated (by the 
	   init method), but that no message has been transmitted yet, i.e. that 
	   we have neither sent our own part to the remote nor that we have 
	   received the remote part yet.
	 - inTransit means that the own key has been sent, but the remote key has 
	   not yet been received and added.
	 - completed means that we have received the remote part and thus completed 
	   the protocol.
	 */
	private static final int STATE_INITIALIZED = 1;
	/** @see #STATE_INITIALIZED */
	private static final int STATE_INTRANSIT = 2;
	/** @see #STATE_INITIALIZED */
	private static final int STATE_COMPLETED = 3;
	
	/** If set to true, the JSSE will be used, if set to false, the 
	 * Bouncycastle Lightweight API. */
	private boolean useJSSE;
	
	/** If set to true, some checks are relaxed. */
	private boolean permanentLocalKeyPair = false;

	/** The current state of the protocol, i.e. one of STATE_INITIALIZED, 
	 * STATE_INTRANSIT, or STATE_COMPLETED. */
	private int state;

	/** The Diffie-Hellman key agreement object used for computing the shared 
	 * key. This object will hold either a javax.crypto.KeyAgreement object of 
	 * an org.bouncycastle.crypto.BasicAgreement object, depending on the
	 * used API. */
	private Object dh;

	/** The local Diffie-Hellman key pair, can be used to get the public and 
	 * private parts. This object will hold either a java.security.KeyPair 
	 * object or an org.bouncycastle.crypto.AsymmetricCipherKeyPair object,
	 * depending on the used API. */
	private Object myKeypair;

	/** We use this algorithm for computing the shared key. It is hard-coded 
	 * for simpler use of this class. */
	private static final String KEYAGREEMENT_ALGORITHM = "DiffieHellman";
	/** This is used for deriving the authentication key from the shared 
	 * session key to ensure that they are different. It's just some random 
	 * text, the exact value really does not matter. */
	private static final String MAGIC_COOKIE = "MAGIC COOKIE FOR AUTHENTICAION";

	/** The 1024 bit Diffie-Hellman modulus values used by SKIP */
	private static final byte skip1024ModulusBytes[] = { (byte) 0xF4,
			(byte) 0x88, (byte) 0xFD, (byte) 0x58, (byte) 0x4E, (byte) 0x49,
			(byte) 0xDB, (byte) 0xCD, (byte) 0x20, (byte) 0xB4, (byte) 0x9D,
			(byte) 0xE4, (byte) 0x91, (byte) 0x07, (byte) 0x36, (byte) 0x6B,
			(byte) 0x33, (byte) 0x6C, (byte) 0x38, (byte) 0x0D, (byte) 0x45,
			(byte) 0x1D, (byte) 0x0F, (byte) 0x7C, (byte) 0x88, (byte) 0xB3,
			(byte) 0x1C, (byte) 0x7C, (byte) 0x5B, (byte) 0x2D, (byte) 0x8E,
			(byte) 0xF6, (byte) 0xF3, (byte) 0xC9, (byte) 0x23, (byte) 0xC0,
			(byte) 0x43, (byte) 0xF0, (byte) 0xA5, (byte) 0x5B, (byte) 0x18,
			(byte) 0x8D, (byte) 0x8E, (byte) 0xBB, (byte) 0x55, (byte) 0x8C,
			(byte) 0xB8, (byte) 0x5D, (byte) 0x38, (byte) 0xD3, (byte) 0x34,
			(byte) 0xFD, (byte) 0x7C, (byte) 0x17, (byte) 0x57, (byte) 0x43,
			(byte) 0xA3, (byte) 0x1D, (byte) 0x18, (byte) 0x6C, (byte) 0xDE,
			(byte) 0x33, (byte) 0x21, (byte) 0x2C, (byte) 0xB5, (byte) 0x2A,
			(byte) 0xFF, (byte) 0x3C, (byte) 0xE1, (byte) 0xB1, (byte) 0x29,
			(byte) 0x40, (byte) 0x18, (byte) 0x11, (byte) 0x8D, (byte) 0x7C,
			(byte) 0x84, (byte) 0xA7, (byte) 0x0A, (byte) 0x72, (byte) 0xD6,
			(byte) 0x86, (byte) 0xC4, (byte) 0x03, (byte) 0x19, (byte) 0xC8,
			(byte) 0x07, (byte) 0x29, (byte) 0x7A, (byte) 0xCA, (byte) 0x95,
			(byte) 0x0C, (byte) 0xD9, (byte) 0x96, (byte) 0x9F, (byte) 0xAB,
			(byte) 0xD0, (byte) 0x0A, (byte) 0x50, (byte) 0x9B, (byte) 0x02,
			(byte) 0x46, (byte) 0xD3, (byte) 0x08, (byte) 0x3D, (byte) 0x66,
			(byte) 0xA4, (byte) 0x5D, (byte) 0x41, (byte) 0x9F, (byte) 0x9C,
			(byte) 0x7C, (byte) 0xBD, (byte) 0x89, (byte) 0x4B, (byte) 0x22,
			(byte) 0x19, (byte) 0x26, (byte) 0xBA, (byte) 0xAB, (byte) 0xA2,
			(byte) 0x5E, (byte) 0xC3, (byte) 0x55, (byte) 0xE9, (byte) 0x2F,
			(byte) 0x78, (byte) 0xC7 };

	/** The SKIP 1024 bit modulus. This is only a BigInterger representation 
	 * of skip1024ModulusBytes, but kept for performance reasons. */
	public static final BigInteger skip1024Modulus = new BigInteger(1,
			skip1024ModulusBytes);

	/** The base used with the SKIP 1024 bit modulus */
	public static final BigInteger skip1024Base = BigInteger.valueOf(2);

	/*static {
	 // Java is not a good language for that....
	 p1024 = new byte[p1024_orig.length];
	 for (int i=0; i<p1024_orig.length; i++) {
	 if (p1024_orig[i] < 0x80)
	 p1024[i] = (byte) (p1024_orig[i] & 0x7f);
	 else {
	 // ok, negative bytes are a very bad idea of language designers
	 // when >= 128, need to manually convert it to a negative 2's complement
	 p1024[i] = (byte) (p1024_orig[i] & 0x7f);
	 p1024[i] += 0x80;
	 }
	 }
	 }*/

	/** This is the shared key computed by the key agreement algorithm 
	 * (Diffie-Hellman at the moment). It is not passed to the caller by any 
	 * function, but the session and authentication keys computed by 
	 * getSessionKey and getAuthenticationKey are only derived from this key 
	 * in a non-reversable way. This provides Perfect Forward Secrecy (PFS), 
	 * i.e. even when some session or authentication key is leaked by improper 
	 * use or side-channel attacks, neither this shared secret nor the private
	 * key used to establish it will be leaked. The authentication and session 
	 * keys are independent in the same way: knowing one does not give any 
	 * knowledge about the other (under the assumption that the hashing 
	 * algorithm specified in DIGEST_ALGORITHM is secure).
	 */
	private byte[] sharedKey;
	
	/** Initialized a fresh key agreement, simply by calling init(). 
	 * @see #init
	 * @param useJSSE If set to true, the JSSE API with the default JCE 
	 *                provider of the JVM will be used for cryptographic 
	 *                operations. If set to false, an internal copy of the 
	 *                Bouncycastle Lightweight API classes will be used.
	 * @param permanentLocalKeyPair Is set to true, then a few checks are
	 *                relaxed and the same key agreement instance may be
	 *                used for multiple Diffie-Hellman rounds with different
	 *                remote public keys. Before a second key agreement round
	 *                can be started, resetRemotePart() <b>must</b> be called.
	 *                This option should be used only in special circumstances
	 *                when ephemeral keys can not be supported!
	 */
	public SimpleKeyAgreement(boolean useJSSE, boolean permanentLocalKeyPair) 
			throws InternalApplicationException {
		this.permanentLocalKeyPair = permanentLocalKeyPair;
		init(useJSSE);
	}

	/** Initialized a fresh key agreement, simply by calling init(). 
	 * @see #init
	 * @param useJSSE If set to true, the JSSE API with the default JCE 
	 *                provider of the JVM will be used for cryptographic 
	 *                operations. If set to false, an internal copy of the 
	 *                Bouncycastle Lightweight API classes will be used.
	 */
	public SimpleKeyAgreement(boolean useJSSE) 
			throws InternalApplicationException {
		this(useJSSE, false);
	}

	public SimpleKeyAgreement(byte[] localKeyPair) {
		// TODO: implement deserializing of local key pair!
	}
	
	public byte[] storeLocalKeyPair() {
		// TODO: implement serializing of local key pair!
		return null;
	}

	/** Initializes the random nonce of this side for generating the shared 
	 * session key. This method can be called in any state and wipes all old 
	 * values (by calling wipe()).
	 * @param useJSSE If set to true, the JSSE API with the default JCE 
	 *                provider of the JVM will be used for cryptographic 
	 *                operations. If set to false, an internal copy of the 
	 *                Bouncycastle Lightweight API classes will be used.
	 * @see #wipe
	 */
	public void init(// TODO: activate me again when J2ME polish can deal with Java5 sources!
			//@SuppressWarnings("hiding") // this is as good as a constructor, so allow variable hiding
			boolean useJSSE) throws InternalApplicationException {
		this.useJSSE = useJSSE;
//#if cfg.includeJSSESupport
		if (useJSSE)
			init_JSSE();
		else
//#endif
			init_BCAPI();
	}

//#if cfg.includeJSSESupport
	/** This is an implementation of init() using the Sun JSSE API. */
	private void init_JSSE() throws InternalApplicationException {
		// before overwriting the object references, wipe the old values in memory to really destroy them
		wipe();

		// this also generates the private value X with a bit length of 256
		try {
			java.security.KeyPairGenerator kg = java.security.KeyPairGenerator
					.getInstance(KEYAGREEMENT_ALGORITHM);

			javax.crypto.spec.DHParameterSpec ps = new javax.crypto.spec.DHParameterSpec(skip1024Modulus,
					skip1024Base);
			kg.initialize(ps);
			myKeypair = kg.generateKeyPair();

			initFromLocalKeyPair_JSSE();
			
			state = STATE_INITIALIZED;
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new InternalApplicationException(
					"Required key agreement algorithm is unknown to the installed cryptography provider(s)",
					e);
		} catch (java.security.InvalidAlgorithmParameterException e) {
			throw new InternalApplicationException(
					"Required parameters are not supported by the key agreement algorithm",
					e);
		}
	}
//#endif

	/** This is an implementation of init() using the Bouncycastle Lightweight 
	 * API. */
	private void init_BCAPI() {
		// before overwriting the object references, wipe the old values in memory to really destroy them
		wipe();

		org.bouncycastle.crypto.generators.DHBasicKeyPairGenerator kg = new org.bouncycastle.crypto.generators.DHBasicKeyPairGenerator();
		kg.init(new org.bouncycastle.crypto.params.DHKeyGenerationParameters(new SecureRandom(), 
				new org.bouncycastle.crypto.params.DHParameters(skip1024Modulus, skip1024Base)));
		myKeypair = kg.generateKeyPair();

		initFromLocalKeyPair_BCAPI();

		state = STATE_INITIALIZED;
	}

	/** (Re-)Initializes the key agreement from the permanent local key pair.
	 * This is an internal helper function called from init and resetRemotePart().
	 */
	private void initFromLocalKeyPair() throws InternalApplicationException {
//#if cfg.includeJSSESupport
		if (useJSSE)
			initFromLocalKeyPair_JSSE();
		else
//#endif
			initFromLocalKeyPair_BCAPI();
	}

//#if cfg.includeJSSESupport
	/** This is an implementation of initFromLocalKeyPair() using the Sun JSSE API. */
	private void initFromLocalKeyPair_JSSE() throws InternalApplicationException {
		try {
			dh = javax.crypto.KeyAgreement.getInstance(KEYAGREEMENT_ALGORITHM);
			((javax.crypto.KeyAgreement) dh).init(((java.security.KeyPair) myKeypair).getPrivate());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new InternalApplicationException(
					"Required key agreement algorithm is unknown to the installed cryptography provider(s)",
					e);
		} catch (java.security.InvalidKeyException e) {
			throw new InternalApplicationException(
					"Generated private key is not accepted by the key agreement algorithm",
					e);
		}
	}
//#endif

	/** This is an implementation of initFromLocalKeyPair() using the Bouncycastle Lightweight 
	 * API. */
	private void initFromLocalKeyPair_BCAPI() {
		// this also generates the private value X with a bit length of 256
		dh = new org.bouncycastle.crypto.agreement.DHBasicAgreement();
		((org.bouncycastle.crypto.agreement.DHBasicAgreement) dh).init(
				((org.bouncycastle.crypto.AsymmetricCipherKeyPair) myKeypair).getPrivate());
	}

	/** This method performs a secure wipe of the cryptographic key material 
	 * held by this class by overwriting the memory regions with zero before 
	 * freeing them (i.e. handing them over to the garbage collector, which 
	 * might free them at an unpredictable time later, marking them for 
	 * overwrite at an even later time). More specifically, it wipes
	 * sharedKey, myKeypair and dh.
	 * 
	 * TODO: This method is not yet secure! It can't access the internal data 
	 * structures of myKeypair and dh, which do not offer wipe methods 
	 * themselves.
	 * 
	 * @see #sharedKey
	 * @see #myKeypair
	 * @see #dh
	 */
	public void wipe() {
		wipeSharedKeys();
		// TODO: can not wipe myKeyPair, because there is no access to its internal data structures!
		myKeypair = null;
		// TODO: can not wipe dh, because there is no access to its internal data structures!
		dh = null;
		// Trigger the garbage collector now. This won't be necessary if we could wipe the values above (which would be a lot more secure).
		System.gc();
		
		// special state: unusable!
		state = 0;
	}
	
	/** A small helper to wipe shared keys from memory. Called by wipe and
	 * resetRemotePart.
	 */
	private void wipeSharedKeys() {
		if (sharedKey != null) {
			for (int i=0; i<sharedKey.length; i++)
				sharedKey[i] = 0;
			sharedKey = null;
		}
	}

	/** This resets the remote parts (if they have already been added) so that
	 * another round can be started with the same local key pair. Calling this
	 * method is only supported when this instance has been constructed with
	 * permanentLocalKeyPair=true.
	 * @throws KeyAgreementProtocolException 
	 * @throws InternalApplicationException 
	 */
	public void resetRemotePart() throws KeyAgreementProtocolException, InternalApplicationException {
		if (!permanentLocalKeyPair)
			throw new KeyAgreementProtocolException(
					"Key agreement can only be reset when it has been constructed" +
					" to support a permanent local key pair. This one has not." +
					" Refusing to reset.");

		wipeSharedKeys();
		initFromLocalKeyPair();

		state = STATE_INTRANSIT;
	}

	/** Get the public key for the key agreement protocol. This byte array 
	 * should be transmitted to the remote side. This method can only be 
	 * called in state initialized and changes it to inTransit. */
	public byte[] getPublicKey() throws KeyAgreementProtocolException {
		if (state != STATE_INITIALIZED && !permanentLocalKeyPair)
			throw new KeyAgreementProtocolException(
					"getPublicKey called in unallowed state! The public key "
							+ "can only transmitted once and immediately after it has been initialized to prevent any kind "
							+ "of replay attacks. To get a new public key for transmit, restart the protocol.");

		state = STATE_INTRANSIT;

		// Do without any special encapsulation, just transfer the raw byte stream. Why should we use X.509 encoding just for transferring the key bytes?
//#if cfg.includeJSSESupport
		if (useJSSE)
			return getPublicKey_JSSE();
		else
//#endif
			return getPublicKey_BCAPI();
	}
	
//#if cfg.includeJSSESupport
	/** This is an implementation of the last part of getPublicKey() using the 
	 * Sun JSSE API. */
	private byte[] getPublicKey_JSSE() {
		return ((javax.crypto.interfaces.DHPublicKey) ((java.security.KeyPair) myKeypair).getPublic()).getY().toByteArray();
	}
//#endif
	
	/** This is an implementation of the last part of getPublicKey() using the 
	 * Bouncycastle Lightweight API. */
	private byte[] getPublicKey_BCAPI() {
		return ((org.bouncycastle.crypto.params.DHPublicKeyParameters) 
				((org.bouncycastle.crypto.AsymmetricCipherKeyPair) myKeypair).getPublic()) .getY().toByteArray();
	}

	/** Add the remote public key.
	 * This method can only be called in state inTransmit and changes it to 
	 * completed.
	 */ 
	public void addRemotePublicKey(byte[] key)
			throws KeyAgreementProtocolException, InternalApplicationException {
		if (state != STATE_INTRANSIT)
			throw new KeyAgreementProtocolException(
					"addRemotePublicKey called in unallowed state! A remote "
							+ "public key can only be added once and only after the own public key has been sent to prevent "
							+ "any kind of replay attacks. To add a remote public key, either first send the own public key "
							+ "to the remote or restart the protocol if you have already added one.");

		if (key == null)
			throw new KeyAgreementProtocolException("addRemotePublicKeyy called with null public key.");
		
		/* check that: 
		 - 1 < key < p
		 - key^q = 1
		 */
		if (new BigInteger(key).compareTo(BigInteger.ONE) <= 0)
			throw new KeyAgreementProtocolException(
					"addRemotePublicKey error: received public key is <= 1");
		//		if (new BigInteger(key).compareTo(new BigInteger(p1024)) > 0)
		if (new BigInteger(key).compareTo(skip1024Modulus) >= 0)
			throw new KeyAgreementProtocolException(
					"addRemotePublicKey error: received public key is >= p");
		// TODO: where is my q ??
		/* if(! new BigInteger(key).pow(q).Equals(BigInteger.ONE))
		 throw new KeyAgreementProtocolException("addRemotePublicKey error: received public key k does not fulfill key^q = 1"); */

//#if cfg.includeJSSESupport
		if (useJSSE)
			addRemotePublicKey_JSSE(key);
		else
//#endif
			addRemotePublicKey_BCAPI(key);
	}
	
//#if cfg.includeJSSESupport
	/** This is an implementation of the last part of getPublicKey() using the 
	 * Sun JSSE API. */
	private void addRemotePublicKey_JSSE(byte[] key)
			throws KeyAgreementProtocolException, InternalApplicationException {
		if (new BigInteger(key).equals(((javax.crypto.interfaces.DHPublicKey) ((java.security.KeyPair) myKeypair).getPublic())
				.getY()))
			throw new KeyAgreementProtocolException(
					"addRemotePublicKey called with a public key equal to our "
							+ "own! This is strictly forbidden since the created random numbers must be different.");

		try {
			javax.crypto.interfaces.DHPublicKey remotePublicKey = 
				(javax.crypto.interfaces.DHPublicKey) java.security.KeyFactory.getInstance(
					KEYAGREEMENT_ALGORITHM).generatePublic(
					new javax.crypto.spec.DHPublicKeySpec(new BigInteger(key), skip1024Modulus,
							skip1024Base));
			//				new DHPublicKeySpec(new BigInteger(key), new BigInteger(p1024), new BigInteger(g1024)));
			((javax.crypto.KeyAgreement) dh).doPhase(remotePublicKey, true);
			sharedKey = ((javax.crypto.KeyAgreement) dh).generateSecret();
			state = STATE_COMPLETED;
		} catch (java.security.spec.InvalidKeySpecException e) {
			throw new InternalApplicationException(
					"Key specification was not accepted for required key agreement protocol",
					e);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new InternalApplicationException(
					"Required key agreement algorithm is unknown to the installed cryptography provider(s)",
					e);
		} catch (java.security.InvalidKeyException e) {
			throw new KeyAgreementProtocolException(
					"Received remote public key is not accepted by the key agreement algorithm",
					e);
		}
	}
//#endif

	/** This is an implementation of the last part of getPublicKey() using the 
	 * Bouncycastle Lightweight API. */
	private void addRemotePublicKey_BCAPI(byte[] key)
			throws KeyAgreementProtocolException {
		if (new BigInteger(key).equals(((org.bouncycastle.crypto.params.DHPublicKeyParameters) 
				((org.bouncycastle.crypto.AsymmetricCipherKeyPair) myKeypair).getPublic()) .getY()))
			throw new KeyAgreementProtocolException(
					"addRemotePublicKey called with a public key equal to our "
							+ "own! This is strictly forbidden since the created random numbers must be different.");

		org.bouncycastle.crypto.params.DHPublicKeyParameters remotePublicKey = new
			org.bouncycastle.crypto.params.DHPublicKeyParameters(new BigInteger(key), 
					new org.bouncycastle.crypto.params.DHParameters(skip1024Modulus, skip1024Base));
		sharedKey = ((org.bouncycastle.crypto.agreement.DHBasicAgreement) dh).calculateAgreement(remotePublicKey).toByteArray();
		/* This is a fix for an interoperability problem between BC and JCE. 
		 * According to BC developer David Hook:
		 * BigInteger.toByteArray() converts the big int to a 2's complement 
		 * number and occasionally this adds a leading zero to the byte array 
		 * to prevent the encoding from being negative. This leading zero needs 
		 * to be dropped if you want to correctly calculate the shared secret.
		 */
		if (sharedKey[0] == 0) {
			byte[] sharedKeyNew = new byte[sharedKey.length-1];
			System.arraycopy(sharedKey, 1, sharedKeyNew, 0, sharedKeyNew.length);
			// be sure to wipe the old shared key....
			for (int i=0; i<sharedKey.length; i++)
				sharedKey[i] = 0;
			sharedKey = sharedKeyNew;
		}
		
		state = STATE_COMPLETED;
	}
	
	/** This method can only be called in state completed.
	 * The returned session key must only be used for deriving authentication 
	 * and encryption keys, e.g. as a PSK for IPSec. It must _not_ be used 
	 * directly for authentication, since this could leak the encryption key 
	 * to any attacker if the authentication function is not strong enough. */
	public byte[] getSessionKey() throws KeyAgreementProtocolException,
			InternalApplicationException {
		if (state != STATE_COMPLETED)
			throw new KeyAgreementProtocolException(
					"getSessionKey called in unallowed state! Must have completed DH before a shared key can be computed.");

		/* the DH secret must be hashed to get 128 bits of entropy from it ! */
		// since a hash is vulnerable to collisions, the key must actually be 256 bits long
		// this should not be too slow, since the shared key generated by DH is rather short
		// what we return is basically sha256(sha256(dhShared) + dhShared) for dhSharedKey being the shared secret generated by DH
		return Hash.doubleSHA256(sharedKey, useJSSE);
	}

	/** This method can only be called in state completed.
	 * The returned key should be used for the initial authentication phase, 
	 * but must _not_ be used for deriving other channel authentication and 
	 * encryption keys. It is derived from the same base as the key returned 
	 * by getSessionKey, and one can thus assume that if this key is equal on 
	 * both sides, then both sides also share the same session key. */  
	public byte[] getAuthenticationKey() throws KeyAgreementProtocolException,
			InternalApplicationException {
		if (state != STATE_COMPLETED)
			throw new KeyAgreementProtocolException(
					"getSessionKey called in unallowed state! Must have completed DH before a shared key can be computed.");

		// what we return is basically sha256(sha256(dhShared) + dhShared) for 
		// dhSharedKey being <the shared secret generated by DH> concatenated with "MAGIC COOKIE FOR RELATE AUTHENTICAION"
		byte[] cookie = MAGIC_COOKIE.getBytes();
		byte[] dhSharedModified = new byte[sharedKey.length + cookie.length];
		System.arraycopy(sharedKey, 0, dhSharedModified, 0, sharedKey.length);
		System.arraycopy(cookie, 0, dhSharedModified, sharedKey.length,
				cookie.length);
		return Hash.doubleSHA256(dhSharedModified, useJSSE);
	}
}
