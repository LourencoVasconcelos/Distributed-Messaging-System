package sd1920.trab2.server.soap.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.jws.WebService;

import sd1920.trab2.api.User;
import sd1920.trab2.api.soap.MessagesException;
import sd1920.trab2.api.soap.UserServiceSoap;
import sd1920.trab2.clients.utils.ClientFactory;
import sd1920.trab2.clients.utils.MessageEmailClientSoap;
import sd1920.trab2.server.rest.resources.UserResourceRest;

@WebService(serviceName = UserServiceSoap.NAME, targetNamespace = UserServiceSoap.NAMESPACE, endpointInterface = UserServiceSoap.INTERFACE)
public class UserResourceSoap implements UserServiceSoap {

	private static Logger Log = Logger.getLogger(UserResourceRest.class.getName());
	public final static int CONNECTION_TIMEOUT = 1000;
	public final static int REPLY_TIMEOUT = 600;
	private static final String INTERNAL_SECRET = "secret";
	public static int PORT;
	MessageEmailClientSoap localMessageClient;
	// Map to save the users of local domain
	private final Map<String, User> users;

	public UserResourceSoap(int port) {
		users = new HashMap<String, User>();
		PORT = port;
		localMessageClient = ClientFactory.getMessagesClientSoap(getLocalServerURI(), 2, 1000);
	}

	@Override
	public String postUser(User user) throws MessagesException {
		Log.info("Received request to register a new User with name: " + user.getName());

		if (user.getName() == null || user.getName().isEmpty() || user.getPwd() == null || user.getPwd().isEmpty()
				|| user.getDomain() == null || user.getDomain().isEmpty() || users.containsKey(user.getName())) {

			throw new MessagesException("User was rejected due to lack of recepients.");
		}

		String domain = getLocalDomain();
		if (!domain.equalsIgnoreCase(user.getDomain())) {

			throw new MessagesException("Domain in the user (" + user.getDomain()
					+ ") does not match the domain of the server (" + domain + ").");
		}

		synchronized (this) {
			users.put(user.getName(), user);
		}

		Log.info("Created new user with name: " + user.getName());
		localMessageClient.createInbox(user.getName(), INTERNAL_SECRET);

		return user.getName() + "@" + user.getDomain();
	}

	@Override
	public User getUser(String name, String pwd) throws MessagesException {
		Log.info("nome" + name);

		User u = null;

		synchronized (this) {
			u = users.get(name);
		}

		if (u != null) {
			Log.info("pass " + u.getPwd());
			Log.info("pwd " + pwd);

			if (u.getPwd().equals(pwd)) {
				return u;
			} else {
				Log.info("Wrong Password");
			}
		} else {
			Log.info("User does not exists in the system");
		}

		throw new MessagesException("Not supposed to get here");
	}

	@Override
	public User updateUser(String name, String pwd, User user) throws MessagesException {
		User u = null;

		synchronized (this) {
			u = users.get(name);
		}

		if (u != null) {
			if (u.getPwd().equals(pwd)) {
				if (user.getPwd() != null)
					u.setPwd(user.getPwd());

				if (user.getDisplayName() != null)
					u.setDisplayName(user.getDisplayName());

				return u;
			} else {
				Log.info("Wrong Password");
			}
		} else {
			Log.info("User does not exists in the system");
		}

		throw new MessagesException("Not supposed to get here");
	}

	@Override
	public User deleteUser(String name, String pwd) throws MessagesException {
		User u = null;

		synchronized (this) {
			u = users.get(name);
		}

		if (u != null) {
			if (u.getPwd().equals(pwd)) {
				localMessageClient.deleteInbox(u.getName(), INTERNAL_SECRET);
				return users.remove(name);
			} else {
				Log.info("Wrong Password");
			}
		} else {
			Log.info("User does not exists in the system");
		}
		throw new MessagesException("Not supposed to get here");
	}

	/**
	 * Auxiliary method that returns the local domain
	 * 
	 * @return the local domain
	 */
	private String getLocalDomain() {
		String domain = "";
		try {
			domain = InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return domain;
	}

	/**
	 * Auxiliary method that returns the local server URI
	 * 
	 * @return the local server URI
	 */
	private String getLocalServerURI() {
		String ip = "";
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		String serverURI = String.format("https://%s:%s/soap", ip, PORT);

		return serverURI;
	}

}