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

import sd1920.trab2.zookeeper.ZookeeperOps;
import sd1920.trab2.clients.utils.InsecureHostnameVerifier;
import sd1920.trab2.discovery.*;
import sd1920.trab2.server.rest.resources.MessageResourceRestReplic;
import sd1920.trab2.server.rest.resources.UserResourceRestReplic;

public class ReplicServer {

	private static Logger Log = Logger.getLogger(ReplicServer.class.getName());
	static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}

	public static final int PORT = 8080;
	public static final String SERVICE = "MessageService";

	public static void main(String[] args) throws UnknownHostException, Exception {

		String secret = args[0];

		String ip = InetAddress.getLocalHost().getHostAddress();

		// This will allow client code executed by this process to ignore hostname
		// verification
		HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

		String domain = InetAddress.getLocalHost().getCanonicalHostName();

		String serverURI = String.format("https://%s:%s/rest", ip, PORT);

		Discovery dis = new Discovery(DISCOVERY_ADDR, domain, serverURI);
		dis.start();

		ZookeeperOps zo = new ZookeeperOps(domain, serverURI);
		ResourceConfig config = new ResourceConfig();
		MessageResourceRestReplic msgs = new MessageResourceRestReplic(dis, PORT, zo, secret);
		UserResourceRestReplic users = new UserResourceRestReplic(PORT, zo, secret);

		config.register(msgs);
		config.register(users);

		zo.regist(msgs, users);

		try {
			JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config, SSLContext.getDefault());
		} catch (NoSuchAlgorithmException e) {
			System.out.println("Invalid SSLL/TLS configuration");
			e.printStackTrace();
		}

		if (!zo.getPrimaryURI().equals(serverURI)) {
			zo.updateState();
		}

		Log.info(String.format("%s Server ready @ %s\n", SERVICE, serverURI));

	}

}