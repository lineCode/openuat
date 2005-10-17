package org.eu.mayrhofer.authentication;

import org.eu.mayrhofer.authentication.exceptions.*;
import java.net.*;
import java.io.*;
import java.util.*;

import org.apache.commons.codec.*;
import org.apache.commons.codec.binary.*;

public class HostProtocolHandler {
    public static final String Protocol_Hello = "HELO RelateAuthentication";
    public static final String Protocol_AuthenticationRequest = "AUTHREQ ";
    public static final String Protocol_AuthenticationAcknowledge = "AUTHACK ";

    public static final int AuthenticationStages = 4;

    private Socket socket;
    private PrintWriter toRemote;
    private BufferedReader fromRemote;
    
    static private LinkedList eventsHandlers = new LinkedList();

    // / <summary>
    // / This class should only be instantiated by HostServerSocket for incoming
	// connections or with the
    // / static StartAuthenticatingWith method for outgoing connections.
    // / </summary>
    // / <param name="soc">The socket to use for communication. It must already
	// be connected to the
    // / other side, but will be shut down and closed before the protocol
	// handler methods return. The
    // / reason for this asymmetry (the socket must be connected by the caller,
	// but is closed by the
    // / methods of this class) lies in the asynchronity: the protocol handler
	// methods are called in
    // / background threads and must therefore dispose the objects before
	// exiting.</param>
    HostProtocolHandler(Socket soc) 
	{
		socket = soc;
    }
    
    static public void addAuthenticationProgressHandler(AuthenticationProgressHandler h) {
    	if (! eventsHandlers.contains(h))
    		eventsHandlers.add(h);
    }

    static public boolean removeAuthenticationProgressHandler(AuthenticationProgressHandler h) {
   		return eventsHandlers.remove(h);
    }
    
    static private void raiseAuthenticationSuccessEvent(InetAddress remote, byte[] sharedSessionKey, byte[] sharedAuthenticationKey) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationSuccess(remote, sharedSessionKey, sharedAuthenticationKey);
    }

    static private void raiseAuthenticationFailureEvent(InetAddress remote, Exception e, String msg) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationFailure(remote, e, msg);
    }

    static private void raiseAuthenticationProgressEvent(InetAddress remote, int cur, int max, String msg) {
    	if (eventsHandlers != null)
    		for (ListIterator i = eventsHandlers.listIterator(); i.hasNext(); )
    			((AuthenticationProgressHandler) i.next()).AuthenticationProgress(remote, cur, max, msg);
    }

    void shutdownSocketsCleanly()
    {
    	try {
    	if (fromRemote != null)
    		fromRemote.close();
    	if (toRemote != null) {
    		toRemote.flush();
    		toRemote.close();
    	}
        if (socket != null && socket.isConnected())
        {
        	if (! socket.isInputShutdown() && !socket.isClosed())
        		socket.shutdownInput();
        	if (! socket.isOutputShutdown() && !socket.isClosed())
        		socket.shutdownOutput();
        	socket.close();
        }
    	}
    	catch (IOException e) {
    		throw new RuntimeException("Unable to close sockets cleanly", e);
    	}
    }
    
    // / <summary>
    // / Tries to receive a properly formatted public key from the remote host.
	// If decoding fails, an
    // / OnAuthenticationFailure event is raised.
    // / </summary>
    // / <param name="expectedMsg">Gives the message that is expected to be
	// received: for server mode,
    // / a Protocol_AuthenticationRequest message is expected, while for client
	// mode, a
    // / Protocol_AuthenticationAcknowledge is expected.</param>
    // / <param name="remotePubKey">The extracted public key is returned in this
	// array. If decoding failed,
    // / null will be returned instead of the (meaningless) parts that might
	// have been decoded.</param>
    // / <param name="remote">The remote socket. This is only needed for raising
	// events and is passed
    // / unmodified to the event method.</param>
    // / <returns>true if a properly formatted public key was found within the
	// expected message,
    // / false otherwise.</returns>
    private byte[] Helper_ExtractPublicKey(String expectedMsg, InetAddress remote) throws IOException
    {
    	String msg = fromRemote.readLine();
    	byte[] remotePubKey;
        if (msg == null)
        {
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: no message received");
            return null;
        }

        // try to extract the remote key from it
        if (!msg.startsWith(expectedMsg))
        {
            toRemote.println("Protocol error: unknown message: '" + msg + "'");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: unknown message");
            return null;
        }
        String remotePubKeyStr = msg.substring(expectedMsg.length());
        try {
        	remotePubKey = Hex.decodeHex(remotePubKeyStr.toCharArray());
        }
        catch (DecoderException e) {
            toRemote.println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            raiseAuthenticationFailureEvent(remote, e, "Protocol error: can not decode remote public key");
            return null;
        }
        if (remotePubKey.length < 128)
        {
            toRemote.println("Protocol error: could not parse public key, expected 128 Bytes hex-encoded.");
            raiseAuthenticationFailureEvent(remote, null, "Protocol error: remote key too short (only " + remotePubKey.length + " bytes instead of 128)");
            return null;
        }
        return remotePubKey;
    }
    
    /*** This method depends on prior initialization and assumes to be launched in an independent thread, i.e. it performs blocking operations.
     * It assumes that the socket variable already contains a valid, connected socket that can be used for communication with the remote
     * authentication partner. fromRemote and toRemote will be initialized as streams connected to this socket.
     * @param serverSide true for server side ("authenticator"), false for client side ("authenticatee")
     */
    private void performAuthenticationProtocol(boolean serverSide) {
    	SimpleKeyAgreement ka = null;
        // remember whom we are communication with: when the socket gets closed,
		// we can no longer access it
        InetAddress remote = socket.getInetAddress();
        String inOrOut, serverToClient, clientToServer;

        if (serverSide) {
        	inOrOut = "Incoming";
        	serverToClient = "sent";
        	clientToServer = "received";
        } else {
        	inOrOut = "Outgoing";
        	serverToClient = "received";
        	clientToServer = "sent";
        }
        
        //System.out.println(inOrOut + " connection to authentication service with " + remote);
        
        try
        {
            fromRemote = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // this enables auto-flush
            toRemote = new PrintWriter(socket.getOutputStream(), true);

            if (serverSide) {
            	toRemote.println(Protocol_Hello);
            }
            else {
                String msg = fromRemote.readLine();
                if (!msg.equals(Protocol_Hello))
                {
                	HostProtocolHandler.raiseAuthenticationFailureEvent(remote, null, "Protocol error: did not get greeting from server");
                    shutdownSocketsCleanly();
                    return;
                }
        	}
            HostProtocolHandler.raiseAuthenticationProgressEvent(remote, 1, AuthenticationStages, inOrOut + " authentication connection, " + serverToClient + " greeting");

            byte[] remotePubKey = null;
            if (serverSide) {
                remotePubKey = Helper_ExtractPublicKey(Protocol_AuthenticationRequest, remote);
                if (remotePubKey == null)
                {
                    shutdownSocketsCleanly();
                    return;
                }
            }
            else {
            	// now send my first message, but already need the public key for it
            	ka = new SimpleKeyAgreement();
            	toRemote.println(Protocol_AuthenticationRequest + new String(Hex.encodeHex(ka.getPublicKey())));
            }
        	HostProtocolHandler.raiseAuthenticationProgressEvent(remote, 2, AuthenticationStages, inOrOut + " authentication connection, " + clientToServer + " public key");

        	if (serverSide) {
                // for performance reasons: only now start the DH phase
                ka = new SimpleKeyAgreement();
                toRemote.println(Protocol_AuthenticationAcknowledge + new String(Hex.encodeHex(ka.getPublicKey())));
        	}
        	else {
                remotePubKey = Helper_ExtractPublicKey(Protocol_AuthenticationAcknowledge, remote);
                if (remotePubKey == null)
                {
                    shutdownSocketsCleanly();
                    return;
                }
        	}
            HostProtocolHandler.raiseAuthenticationProgressEvent(remote, 3, AuthenticationStages, inOrOut + " authentication connection, " + serverToClient + " public key");

            ka.addRemotePublicKey(remotePubKey);
            HostProtocolHandler.raiseAuthenticationProgressEvent(remote, 4, AuthenticationStages, inOrOut + " authentication connection, computed shared secret");

            HostProtocolHandler.raiseAuthenticationSuccessEvent(remote, ka.getSessionKey(), ka.getAuthenticationKey());
        }
        catch (InternalApplicationException e)
        {
            System.out.println(e);
            // also communicate any application exception to interested
			// listeners
            raiseAuthenticationFailureEvent(remote, e, null);
        }
        catch (IOException e)
        {
            //System.out.println(e);
            // even if we ignore the exception and not treat it as an error
			// case, report it to listeners
            // so that they can clean up their state of this authentication
			// (identified by the remote
            raiseAuthenticationFailureEvent(remote, null, "Client closed connection unexpectedly\n");
        }
        catch (Exception e)
        {
            System.out.println("UNEXPECTED EXCEPTION: " + e);
        }
        finally {
            shutdownSocketsCleanly();
            if (ka != null)
                ka.wipe();
            //System.out.println("Ended " + inOrOut + " authentication connection with " + remote);
        }
            }
    
    // Hack to just allow one method to be called asynchronously while still having access to the outer class
    private abstract class AsynchronousCallHelper implements Runnable {
    	protected HostProtocolHandler outer;
    	
    	protected AsynchronousCallHelper(HostProtocolHandler outer) {
    		this.outer = outer;
    	}
    }

	void startIncomingAuthenticationThread()
	{
		//System.out.println("Starting incoming authentication thread handler");
		new Thread(new AsynchronousCallHelper(this) 
	{ public void run() {
		outer.performAuthenticationProtocol(true);
    }}).start();
		//System.out.println("Started incoming authentication thread handler");
	}

    // / <summary>
    // / Outgoing authentication connections are done asynchronously just like
	// the incoming
    // / connections. This method starts a new thread that tries to authenticate
	// with the host
    // / given as remote. Callers need to subscribe to the Authentication*
	// events to get notifications
    // / of authentication success, failure and progress.
    // / </summary>
    static public void startAuthenticationWith(String remoteAddress, int remotePort) throws UnknownHostException, IOException
    {
        Socket clientSocket = new Socket(remoteAddress, remotePort);

        HostProtocolHandler tmpProtocolHandler = new HostProtocolHandler(clientSocket);
        new Thread(tmpProtocolHandler.new AsynchronousCallHelper(tmpProtocolHandler) {
    public void run()
    {
    	outer.performAuthenticationProtocol(false);
    	} }).start();
    }

}