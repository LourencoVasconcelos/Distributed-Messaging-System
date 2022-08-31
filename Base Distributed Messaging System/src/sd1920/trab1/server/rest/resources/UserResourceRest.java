package sd1920.trab1.server.rest.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.client.ClientConfig;

import sd1920.trab1.api.User;
import sd1920.trab1.api.rest.MessageService;
import sd1920.trab1.api.rest.UserService;

@Singleton
public class UserResourceRest implements UserService {

	private static Logger Log = Logger.getLogger(UserResourceRest.class.getName());
	private static String CREATE_INBOX = "Inbox created";
	private static String DELETE_INBOX = "Inbox deleted";
	public final static int CONNECTION_TIMEOUT = 1000;
	public final static int REPLY_TIMEOUT = 600;
	public static int PORT;
	
	// Map to save the users of local domain
	private final Map<String, User> users;

	public UserResourceRest(int port) {
		users = new HashMap<String, User>();
		PORT = port;
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
		accessInboxes(user.getName(), CREATE_INBOX);

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
				accessInboxes(u.getName(), DELETE_INBOX);
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
	 * Auxiliary method that create or delete an inbox for the given user in local domain
	 * 
	 * @param user the username of the inbox
	 * @param role defines whether to create or delete the inbox
	 */
	private void accessInboxes(String user, String role) {
		ClientConfig config = new ClientConfig();
		Client client = ClientBuilder.newClient(config);
		WebTarget target = null;
		Response r = null;

		target = client.target(getLocalServerURI()).path(MessageService.PATH).path("outuser");

		if (role.equals(CREATE_INBOX)) {
			r = target.path(user).request().post(Entity.json(""));
		}
		if (role.equals(DELETE_INBOX)) {
			r = target.path(user).request().delete();
		}

		if (r.getStatus() != Status.NO_CONTENT.getStatusCode())
			Log.info(role + " with success.");
		else
			Log.info("Error, HTTP error status: " + r.getStatus());

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
		String serverURI = String.format("http://%s:%s/rest", ip, PORT);

		return serverURI;
	}

}
