package org.openuat.util.ip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** This is a helper class to query local and externally visible IPv6 addresses. */
public class IPv6AddressesHelper {
	/** Our logger for this class. */
	private final static Logger logger = LoggerFactory.getLogger(IPv6AddressesHelper.class.getName());

	/** This is the host queried for the externally visible IPv6 address of 
	 * the client when connecting to Internet services. The host name is
	 * resolved to IPv6 addresses (AAAA DNS entries) to make sure that we
	 * connect via IPv6.
	 */
	public final static String GET_OUTBOUND_IP_SERVER = "doc.to";

	public final static String GET_OUTBOUND_IP_URL_PROTOCOL = "https://";
	public final static String GET_OUTBOUND_IP_URL_PATH = "/getip/";

	/** This is the URL queried for the externally visible IPv6 address of
	 * the client when connecting to Internet services.
	 */
	public final static String GET_OUTBOUND_IP_URL = 
		GET_OUTBOUND_IP_URL_PROTOCOL + GET_OUTBOUND_IP_SERVER + GET_OUTBOUND_IP_URL_PATH;

	/** This method tries to retrieve the IPv6 address visible to servers by 
     * querying https://doc.to/getip/.
     * @return the IPv6 address of this host that is used to connect to other
     *         hosts or null if IPv6 connections to https://doc.to are not
     *         possible.
     */
    public static String getOutboundIPv6Address() {
    	try {
    		// first resolve the host's AAAA entries to make sure to connect to the host via IPv6
			InetAddress[] serverAddrs = InetAddress.getAllByName(GET_OUTBOUND_IP_SERVER);
			Inet6Address server = null;
			for (InetAddress addr : serverAddrs) {
				if (addr instanceof Inet6Address) {
					logger.debug("Resolved " + GET_OUTBOUND_IP_SERVER + " to IPv6 address " + addr.getHostAddress());
					if (server == null)
						server = (Inet6Address) addr;
					else
						logger.warn("Found multiple IPv6 addresses for host " + 
								GET_OUTBOUND_IP_SERVER + ", but expected only one. Will use the one found first " +
								server.getHostAddress() + " and ignore the one found now " + addr.getHostAddress());
				}
			}
			if (server == null) {
				logger.warn("Could not resolve host " + GET_OUTBOUND_IP_SERVER + 
						" to IPv6 address, therefore unable to determine externally visible IPv6 address of this client");
				return null;
			}
			
			// now that we have the IPv6 address to connect to, query the URL
			String url = GET_OUTBOUND_IP_URL_PROTOCOL + "[" + server.getHostAddress() + "]" + GET_OUTBOUND_IP_URL_PATH;
			logger.debug("Querying URL " + url + " for outbound IPv6 address");
			return queryServerForOutboundAddress(url);
		} 
    	catch (UnknownHostException e) {
			logger.warn("Unable to resolve host " + GET_OUTBOUND_IP_SERVER, e);
			return null;
		} 
    }
    
    /** This method queries the passed customURL or https://doc.to/getip/ if 
     * null is given and expects to read the outbound IP address of this client
     * in return.
     * 
     * @param customURL The URL to query. If null, GET_OUTBOUND_IP_URL will 
     *                  be used. 
     * @return the outbound IP address of this host as seen be the server.
     */
    public static String queryServerForOutboundAddress(String customURL) {
    	try {
			// setup 1 before querying the URL: enable following HTTP redirects
			HttpURLConnection.setFollowRedirects(true);
			
			// setup 2 before querying the URL: disable certificate checks
			// create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[]{
			    new X509TrustManager() {
			        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
			            return null;
			        }
			        public void checkClientTrusted(
			            java.security.cert.X509Certificate[] certs, String authType) {
			        }
			        public void checkServerTrusted(
			            java.security.cert.X509Certificate[] certs, String authType) {
			        }
			    }
			};
			// install the all-trusting trust manager
		    SSLContext sc = SSLContext.getInstance("TLS");
		    sc.init(null, trustAllCerts, new java.security.SecureRandom());
		    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		    // and also disable hostname verification
		    HttpsURLConnection.setDefaultHostnameVerifier( new HostnameVerifier(){
					@Override
					public boolean verify(String arg0, SSLSession arg1) {
						return true;
					}
		    	});
			
		    // finally query the HTTPS URL
			URLConnection conn = new URL(customURL != null ? customURL : GET_OUTBOUND_IP_URL).openConnection();
			conn.connect();
			String retMimeType = conn.getContentType();
			logger.debug("URL " + GET_OUTBOUND_IP_URL + " returned content type " + retMimeType);
			
			InputStreamReader in = new InputStreamReader((InputStream) conn.getContent());
		    BufferedReader buff = new BufferedReader(in);
		    StringBuffer reply = new StringBuffer();
		    String line = null;
		    do {
		    	line = buff.readLine();
		    	if (line != null) {
		    		if (reply.length() > 0) reply.append("\n");
		    		reply.append(line);
		    	}
		    } while (line != null);
		    
			return reply.toString();
    	}
    	catch (MalformedURLException e) {
			logger.error("Internal error: URL deemed invalid " + GET_OUTBOUND_IP_URL, e);
			return null;
		} catch (IOException e) {
			logger.warn("Unable to connect to URL " + GET_OUTBOUND_IP_URL + 
					" and/or host " + GET_OUTBOUND_IP_SERVER, e);
			return null;
		} catch (NoSuchAlgorithmException e) {
			logger.warn("Unable to install custom TrustManager/SSLContext without certificate validation", e);
			return null;
		} catch (KeyManagementException e) {
			logger.warn("Unable to install custom TrustManager/SSLContext without certificate validation", e);
			return null;
		}
    }

	/** Returns true if this address is an IPv6 address, is globally routeable (i.e.
	 * it is not a link- or site-local address), and has been derived from a MAC
	 * address using the EUI scheme.
	 */
	public static boolean isIPv6GlobalMacDerivedAddress(InetAddress address) {
		if (address == null || ! (address instanceof Inet6Address))
			// only check valid IPv6 addresses
			return false;
		Inet6Address addr6 = (Inet6Address) address;
		
		if (addr6.isLinkLocalAddress())
			// if it's link-local, it may be MAC-derived, but not privacy sensitive
			return false;

		byte[] addrByte = addr6.getAddress();
		// MAC-derivation adds "FFFE" in the middle of the 48 bits MAC
		return addrByte[11] == (byte) 0xff && addrByte[12] == (byte) 0xfe;
	}

    /** This method doesn't work on Android pre-Honeycomb (3.0) systems for getting IPv6 addresses. */ 
    public static Vector<String> getLocalAddresses() {
    	Vector<String> addrs = new Vector<String>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
               	for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
               		InetAddress inetAddress = enumIpAddr.nextElement();
               		if (!inetAddress.isLoopbackAddress()) {
               			logger.debug("Found non-loopback address: " + inetAddress.getHostAddress());
               			addrs.add(inetAddress.getHostAddress());
               		}
               		
               		if (inetAddress instanceof Inet6Address) {
               			logger.debug("Found IPv6 address: " + inetAddress.getHostAddress());
               		}
                }
            }
        } catch (SocketException ex) {
            logger.error(ex.toString());
        }
        return addrs;
    }
    
    /** Dummy main routine to call the helper methods and print on console. */
    public static void main(String[] args) throws UnknownHostException {
    	Vector<String> localAddrs = getLocalAddresses();
    	for (String a : localAddrs)
    		System.out.println("Found local non-loopback address: " + a);
    	
    	String outboundIPv6Addr = getOutboundIPv6Address();
    	System.out.println("Found outbound (externally visible) IPv6 address: " + outboundIPv6Addr);
    	System.out.println("Address is MAC-derived: " + 
    			isIPv6GlobalMacDerivedAddress(InetAddress.getByName(outboundIPv6Addr)));
    }
}
