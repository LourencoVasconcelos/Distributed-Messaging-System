package sd1920.trab2.server.rest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import sd1920.trab2.clients.utils.InsecureHostnameVerifier;
import sd1920.trab2.discovery.Discovery;
import sd1920.trab2.dropbox.ProxyRequests;
import sd1920.trab2.server.rest.resources.MessageResourceRestOut;
import sd1920.trab2.server.rest.resources.UserResourceRestOut;

public class ProxyServer {

	private static Logger Log = Logger.getLogger(ProxyServer.class.getName());
	static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}

	public static final int PORT = 8080;
	public static final String SERVICE = "MessageService";

	public static void main(String[] args) throws UnknownHostException {

		String secret = args[1];
		String apiKey = args[2];
		String apiSecret = args[3];
		String accessTokenStr = args[4];

		String ip = InetAddress.getLocalHost().getHostAddress();

		// This will allow client code executed by this process to ignore hostname
		// verification
		HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

		String domain = InetAddress.getLocalHost().getCanonicalHostName();

		String serverURI = String.format("https://%s:%s/rest", ip, PORT);

		Discovery dis = new Discovery(DISCOVERY_ADDR, domain, serverURI);
		dis.start();

		ProxyRequests pr = new ProxyRequests(apiKey, apiSecret, accessTokenStr);

		ResourceConfig config = new ResourceConfig();
		config.register(new MessageResourceRestOut(dis, PORT, secret, pr));
		config.register(new UserResourceRestOut(PORT, pr));

		String aux = String.format("/%s", domain);
		if (args[0].equalsIgnoreCase("true")) {

			pr.delete(aux);
			boolean success = pr.createDirectory(aux);
			aux = String.format("%s/allMessages", aux);
			pr.createDirectory(aux);

			if (success)
				Log.info("Directory '" + domain + "' created successfuly.");
			else
				Log.info("Failed to create directory '" + domain + "'");
		}

		try {
			JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config, SSLContext.getDefault());
		} catch (NoSuchAlgorithmException e) {
			Log.info("Invalid SSLL/TLS configuration");
			e.printStackTrace();
		}

		Log.info(String.format("%s Server ready @ %s\n", SERVICE, serverURI));

	}
}
