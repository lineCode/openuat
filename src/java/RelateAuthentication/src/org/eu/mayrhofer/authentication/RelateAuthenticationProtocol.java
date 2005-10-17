package org.eu.mayrhofer.authentication;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

/// <summary>
/// This is the main class of the relate authentication software: it ties together
/// the host and dongle protocol handlers. Since both handlers work asynchronously
/// in their own threads, this class must also handle the synchronisation between 
/// all events coming in from them.
/// </summary>
public class RelateAuthenticationProtocol implements AuthenticationProgressHandler {
    public void AuthenticationSuccess(InetAddress remote, byte[] sharedSessionKey, byte[] sharedAuthenticationKey)
    {
        System.out.println("Received authentication success event with " + remote);
        System.out.println("Shared session key is now '" + sharedSessionKey + "', shared authentication key is now '" + sharedAuthenticationKey + "'");
    }

    public void AuthenticationFailure(InetAddress remote, Exception e, String msg)
    {
        System.out.println("Received authentication failure event with " + remote);
        if (e != null)
            System.out.println("Exception: " + e);
        if (msg != null)
            System.out.println("Message: " + msg);
    }

    public void AuthenticationProgress(InetAddress remote, int cur, int max, String msg)
    {
        System.out.println("Received authentication progress event with " + remote + " " + cur + " out of " + max + ": " + msg);
    }

    public static void main(String[] args) throws Exception
	{
    	HostProtocolHandler.addAuthenticationProgressHandler(new RelateAuthenticationProtocol());
        
        if (args.length > 0 && args[0].equals("server"))
        {
            HostServerSocket h1 = new HostServerSocket(54321);
            h1.startListening();
            new BufferedReader(new InputStreamReader(System.in)).readLine();
            h1.stopListening();
        }
        if (args.length > 0 && args[0].equals("client"))
        {
            HostProtocolHandler.startAuthenticationWith("localhost", 54321);
        }
        //Environment.Exit(0);
        //System.Diagnostics.Process.GetCurrentProcess().Kill();

        DongleProtocolHandler h = new DongleProtocolHandler("/dev/ttyUSB0");
        h.startAuthenticationWith(0, null, null, 2);
        
        /*try
        {
            DongleProtocolHandler.StartAuthenticationWith("COM3", -1, null, null);
        }
        catch (ConfigurationErrorException e)
        {
            Console.WriteLine("Caught exception, is the device present and not yet in use?");
            Console.WriteLine(e.Message);
            Console.WriteLine(e.StackTrace);
            Exception f = e.InnerException;
            while (f != null)
            {
                Console.WriteLine(f.Message);
                Console.WriteLine(f.StackTrace);
                f = f.InnerException;
            }
        }*/
	}

}