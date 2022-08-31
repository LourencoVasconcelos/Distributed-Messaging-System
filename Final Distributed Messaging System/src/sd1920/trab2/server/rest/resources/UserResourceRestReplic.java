package sd1920.trab2.server.rest.resources;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import sd1920.trab2.Operations.Operation;
import sd1920.trab2.api.User;
import sd1920.trab2.api.restReplic.UserServiceReplic;
import sd1920.trab2.clients.utils.ClientFactory;
import sd1920.trab2.clients.utils.MessagesEmailClient;
import sd1920.trab2.clients.utils.UsersEmailClient;
import sd1920.trab2.zookeeper.ZookeeperOps;

@Singleton
public class UserResourceRestReplic implements UserServiceReplic {

	private static Logger Log = Logger.getLogger(UserResourceRestReplic.class.getName());
	public final static int CONNECTION_TIMEOUT = 1000;
	public final static int REPLY_TIMEOUT = 600;
	private static String INTERNAL_SECRET;
	public static int PORT;
	private final ZookeeperOps zo;
	// Map to save the users of local domain
	private final Map<String, User> users;
	private boolean flagUpdating = false;
	MessagesEmailClient localMessageClient;
	UsersEmailClient replicUserClient;

	public UserResourceRestReplic(int port, ZookeeperOps zo, String secret) {
		this.zo = zo;
		users = new HashMap<String, User>();
		PORT = port;
		INTERNAL_SECRET = secret;
		localMessageClient = ClientFactory.getMessagesClient(getLocalServerURI(), 2, 1000);
		replicUserClient = ClientFactory.getUsersClient(getLocalServerURI(), 5, 1000);
	}

	@Override
	public String postUser(User user, Long version) {

		if (isPrimary()) {
			waitForUpdate();
			int numberOfSucesses = 0;

			Log.info("Received request to register a new User with name: " + user.getName());

			if (user.getName() == null || user.getName().isEmpty() || user.getPwd() == null || user.getPwd().isEmpty()
					|| user.getDomain() == null || user.getDomain().isEmpty() || users.containsKey(user.getName())) {
				Log.info("User was rejected due to lack of recepients.");
				throw new WebApplicationException(Status.CONFLICT);
			}

			String domain = getLocalDomain();
			if (!domain.equalsIgnoreCase(user.getDomain())) {
				Log.info("Domain in the user (" + user.getDomain() + ") does not match the domain of the server ("
						+ domain + ").");
				throw new WebApplicationException(Status.FORBIDDEN);
			}

			while (numberOfSucesses < 1) {

				List<String> lst = zo.getAllReplic();

				for (int i = 0; i < lst.size(); i++) {
					if (!lst.get(i).equals(getLocalServerURI())) {
						numberOfSucesses += replicUserClient.postUserInReplic(user, lst.get(i), INTERNAL_SECRET,
								zo.getVersion());
					}
				}
			}
			return postUserFromPrimary(user, version, INTERNAL_SECRET);

		} else {
			if (version != null)
				if (version > zo.getVersion()) {
					zo.syncSecondary();
				}
			throw new WebApplicationException(
					Response.temporaryRedirect(URI.create(zo.getPrimaryURI() + UserServiceReplic.PATH)).build());
		}
	}

	@Override
	public String postUserFromPrimary(User user, Long version, String secret) {
		if (secret == null || !secret.equals(INTERNAL_SECRET))
			throw new WebApplicationException(Response.Status.FORBIDDEN);
		if (!isPrimary()) {
			if (version != null)
				if (version > zo.getVersion()) {
					zo.syncSecondary();
				}
		}

		zo.addOperation(Operation.createPostUserOp(user));

		synchronized (this) {
			users.put(user.getName(), user);
		}

		Log.info("Created new user with name: " + user.getName());
		localMessageClient.createInbox(user.getName(), INTERNAL_SECRET);
		return user.getName() + "@" + user.getDomain();
	}

	@Override
	public User getUser(String name, String pwd, Long version) {

		if (version != null)
			if (version > zo.getVersion()) {
				zo.syncSecondary();
			}

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
	public User updateUser(String name, String pwd, User user, Long version) {
		if (isPrimary()) {
			waitForUpdate();

			User u = null;

			synchronized (this) {
				u = users.get(name);
			}
			if (u != null) {
				if (u.getPwd().equals(pwd)) {
					int numberOfSucesses = 0;
					while (numberOfSucesses < 1) {
						List<String> lst = zo.getAllReplic();

						for (int i = 0; i < lst.size(); i++) {
							if (!lst.get(i).equals(getLocalServerURI())) {
								numberOfSucesses += replicUserClient.updateInReplic(name, pwd, user, lst.get(i),
										INTERNAL_SECRET, zo.getVersion());
							}
						}
					}
					return updateUserFromPrimary(name, pwd, user, version, INTERNAL_SECRET);
				} else {
					Log.info("Wrong Password");
				}
			} else {
				Log.info("User does not exists in the system");
			}

			throw new WebApplicationException(Status.FORBIDDEN);

		} else {

			if (version != null)
				if (version > zo.getVersion()) {
					zo.syncSecondary();
				}

			throw new WebApplicationException(Response
					.temporaryRedirect(
							URI.create(zo.getPrimaryURI() + UserServiceReplic.PATH + "/" + name + "?pwd=" + pwd))
					.build());
		}
	}

	@Override
	public User updateUserFromPrimary(String name, String pwd, User user, Long version, String secret) {
		if (secret == null || !secret.equals(INTERNAL_SECRET))
			throw new WebApplicationException(Response.Status.FORBIDDEN);
		if (version != null)
			if (version > zo.getVersion()) {
				zo.syncSecondary();
			}

		zo.addOperation(Operation.updateUserOp(name, pwd, user));

		User u;
		synchronized (this) {
			u = users.get(name);
		}
		if (user.getPwd() != null)
			u.setPwd(user.getPwd());

		if (user.getDisplayName() != null)
			u.setDisplayName(user.getDisplayName());
		return u;
	}

	@Override
	public User deleteUser(String user, String pwd, Long version) {
		if (isPrimary()) {
			waitForUpdate();
			User u = null;

			synchronized (this) {
				u = users.get(user);
			}
			if (u != null) {
				if (u.getPwd().equals(pwd)) {

					int numberOfSucesses = 0;
					while (numberOfSucesses < 1) {
						List<String> lst = zo.getAllReplic();

						for (int i = 0; i < lst.size(); i++) {
							if (!lst.get(i).equals(getLocalServerURI())) {
								numberOfSucesses += replicUserClient.deleteInReplic(user, pwd, lst.get(i),
										INTERNAL_SECRET, zo.getVersion());
							}
						}
					}
					return deleteUserFromPrimary(user, pwd, version, INTERNAL_SECRET);

				} else {
					Log.info("Wrong Password");
				}
			} else {
				Log.info("User does not exists in the system");
			}
			throw new WebApplicationException(Status.FORBIDDEN);
		} else {

			if (version != null)
				if (version > zo.getVersion()) {
					zo.syncSecondary();
				}

			throw new WebApplicationException(Response
					.temporaryRedirect(
							URI.create(zo.getPrimaryURI() + UserServiceReplic.PATH + "/" + user + "?pwd=" + pwd))
					.build());
		}

	}

	@Override
	public User deleteUserFromPrimary(String user, String pwd, Long version, String secret) {
		if (secret == null || !secret.equals(INTERNAL_SECRET))
			throw new WebApplicationException(Response.Status.FORBIDDEN);
		if (version != null)
			if (version > zo.getVersion()) {
				zo.syncSecondary();
			}

		zo.addOperation(Operation.deleteUserOp(user, pwd));

		User u;
		synchronized (this) {
			u = users.get(user);
		}
		if (u != null) {
			if (u.getPwd().equals(pwd)) {
				localMessageClient.deleteInbox(user, INTERNAL_SECRET);
				return users.remove(user);
			}
		}
		return null;
	}

	/**
	 * 
	 * @return
	 */
	private boolean isPrimary() {
		System.out.println("Check if is primary");
		System.out.println(zo.getPrimaryURI());
		System.out.println(getLocalServerURI());

		return zo.getPrimaryURI().equals(getLocalServerURI());
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

	@Override
	public List<Operation> getOperations(Long versNumber) {
		System.out.println("versNumber" + versNumber);
		List<Operation> a = zo.getOperations(versNumber);
		System.out.println(a.size());

		return a;
	}

	public void setFlag() {
		flagUpdating = !flagUpdating;
	}

	public void waitForUpdate() {
		while (flagUpdating) {
			;
			;
		}
	}
}
