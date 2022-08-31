package sd1920.trab2.server.rest.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;


import sd1920.trab2.api.User;
import sd1920.trab2.api.rest.UserService;
import sd1920.trab2.clients.utils.ClientFactory;
import sd1920.trab2.clients.utils.MessagesEmailClient;

@Singleton
public class UserResourceRest implements UserService {

	private static Logger Log = Logger.getLogger(UserResourceRest.class.getName());
	private static String INTERNAL_SECRET;
	public static int PORT;
	MessagesEmailClient localMessageClient;
	// Map to save the users of local domain
	private final Map<String, User> users;

	public UserResourceRest(int port, String secret) {
		users = new HashMap<String, User>();
		PORT = port;
		INTERNAL_SECRET = secret;
		localMessageClient = ClientFactory.getMessagesClient(getLocalServerURI(), 2, 1000);
	}

	@Override
	public String postUser(User user) {
		Log.info("Received request to register a new User with name: " + user.getName());

		if (user.getName() == null || user.getName().isEmpty() || user.getPwd() == null || user.getPwd().isEmpty()
				|| user.getDomain() == null || user.getDomain().isEmpty() || users.containsKey(user.getName())) {
			Log.info("User was rejected due to lack of recepients.");
			throw new WebApplicationException(Status.CONFLICT);
		}

		String domain = getLocalDomain();
		if (!domain.equalsIgnoreCase(user.getDomain())) {
			Log.info("Domain in the user (" + user.getDomain() + ") does not match the domain of the server (" + domain
					+ ").");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		synchronized (this) {
			users.put(user.getName(), user);
		}

		Log.info("Created new user with name: " + user.getName());
		localMessageClient.createInbox(user.getName(), INTERNAL_SECRET);

		return user.getName() + "@" + user.getDomain();
	}

	@Override
	public User getUser(String name, String pwd) {
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

		throw new WebApplicationException(Status.FORBIDDEN);
	}

	@Override
	public User updateUser(String name, String pwd, User user) {
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

		throw new WebApplicationException(Status.FORBIDDEN);
	}

	@Override
	public User deleteUser(String user, String pwd) {
		User u = null;

		synchronized (this) {
			u = users.get(user);
		}

		if (u != null) {
			if (u.getPwd().equals(pwd)) {
				localMessageClient.deleteInbox(user, INTERNAL_SECRET);
				return users.remove(user);
			} else {
				Log.info("Wrong Password");
			}
		} else {
			Log.info("User does not exists in the system");
		}
		throw new WebApplicationException(Status.FORBIDDEN);
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
		String serverURI = String.format("https://%s:%s/rest", ip, PORT);

		return serverURI;
	}
}
