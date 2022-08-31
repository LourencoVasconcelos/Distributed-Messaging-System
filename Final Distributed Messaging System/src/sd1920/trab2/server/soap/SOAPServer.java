package sd1920.trab2.server.soap;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.ws.Endpoint;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;

import sd1920.trab2.clients.utils.InsecureHostnameVerifier;
import sd1920.trab2.discovery.Discovery;
import sd1920.trab2.server.soap.resources.MessageResourceSoap;
import sd1920.trab2.server.soap.resources.UserResourceSoap;

@SuppressWarnings("restriction")
public class SOAPServer {

	private static Logger Log = Logger.getLogger(SOAPServer.class.getName());
	static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s\n");
	}

	public static final int PORT = 8080;
	public static final String SERVICE = "MessageService";
	public static final String SOAP_MESSAGES_PATH = "/soap/messages";
	public static final String SOAP_USERS_PATH = "/soap/users";

	public static void main(String[] args) throws Exception {
		String ip = InetAddress.getLocalHost().getHostAddress();
		String serverURI = String.format("https://%s:%s/soap", ip, PORT);

		// This will allow client code executed by this process to ignore hostname
		// verification
		HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

		// Create a http configurator to define the SSL/TLS context
		HttpsConfigurator configurator = new HttpsConfigurator(SSLContext.getDefault());

		// Create an HTTP server, accepting requests at PORT (from all local interfaces)
		HttpsServer server = HttpsServer.create(new InetSocketAddress(ip, PORT), 0);

		// Associate the SSL/TLS context to the HTTPServer
		server.setHttpsConfigurator(configurator);

		// Provide an executor to create threads as needed...
		server.setExecutor(Executors.newCachedThreadPool());

		String domain = InetAddress.getLocalHost().getCanonicalHostName();
		// Discovery
		Discovery dis = new Discovery(DISCOVERY_ADDR, domain, serverURI);
		dis.start();

		// Create a SOAP Endpoint (you need one for each service)
		Endpoint soapMessagesEndpoint = Endpoint.create(new MessageResourceSoap(dis, PORT));
		Endpoint soapUserEndpoint = Endpoint.create(new UserResourceSoap(PORT));

		// Publish a SOAP webservice, under the "http://<ip>:<port>/soap"
		soapMessagesEndpoint.publish(server.createContext(SOAP_MESSAGES_PATH));
		soapUserEndpoint.publish(server.createContext(SOAP_USERS_PATH));

		server.start();

		Log.info(String.format("\n%s Server ready @ %s\n", SERVICE, serverURI));
	}

}
