package sd1920.trab2.server.rest.resources;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import sd1920.trab2.api.User;
import sd1920.trab2.api.restOut.UserServiceOut;
import sd1920.trab2.dropbox.ProxyRequests;

@Singleton
public class UserResourceRestOut implements UserServiceOut {

	private final static String ADD = "add";
	private final static String OVERWRITE = "overwrite";

	private static Logger Log = Logger.getLogger(UserResourceRest.class.getName());
	public final static int CONNECTION_TIMEOUT = 1000;
	public final static int REPLY_TIMEOUT = 600;
	public static int PORT;

	private static ProxyRequests pr;

	public UserResourceRestOut(int port, ProxyRequests pr) {
		PORT = port;
		UserResourceRestOut.pr=pr;
	}

	@Override
	public String postUser(User user) {
		Log.info("Received request to register a new User with name: " + user.getName());

		if (user.getName() == null || user.getName().isEmpty() || user.getPwd() == null || user.getPwd().isEmpty()
				|| user.getDomain() == null || user.getDomain().isEmpty()) {
			Log.info("User was rejected due to lack of recepients.");
			throw new WebApplicationException(Status.CONFLICT);
		}

		String domain = getLocalDomain();
		if (!domain.equalsIgnoreCase(user.getDomain())) {
			Log.info("Domain in the user (" + user.getDomain() + ") does not match the domain of the server (" + domain
					+ ").");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		String directory = String.format("/%s/%s", domain, user.getName());
		boolean success = pr.createDirectory(directory);
		
		if (success) {
			
			String fileName = String.format("%s/info.txt", directory);
			success = pr.contentUpload(fileName, user, ADD);
			
			if (success) {
				
				String subDirectory = String.format("%s/received", directory);
				success = pr.createDirectory(subDirectory);	
				
				subDirectory = String.format("%s/sended", directory);
				success = pr.createDirectory(subDirectory);
				
			}
			
			Log.info("Created new user with name: " + user.getName());

		} else {
			throw new WebApplicationException(Status.CONFLICT);
		}

		return user.getName() + "@" + user.getDomain();
	}

	@Override
	public User getUser(String name, String pwd) {
		String path = String.format("/%s/%s/info.txt", getLocalDomain(), name);
		User u = pr.getUser(path);
		
		if (u != null) {
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

		String path = String.format("/%s/%s/info.txt", getLocalDomain(), name);
		User u = pr.getUser(path);
		if (u != null) {
			if (u.getPwd().equals(pwd)) {


				if (user.getPwd() != null)
					u.setPwd(user.getPwd());
				
				if (user.getDisplayName() != null)
					u.setDisplayName(user.getDisplayName());

				path = String.format("/%s/%s/info.txt", getLocalDomain(), name);
				pr.contentUpload(path, u, OVERWRITE);

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

		String path = String.format("/%s/%s/info.txt", getLocalDomain(), user);
		User u = pr.getUser(path);

		if (u != null) {
			if (u.getPwd().equals(pwd)) {

				path = String.format("/%s/%s", getLocalDomain(), user);
				boolean success = pr.delete(path);
				
				if (success)
					System.out.println("User " + user + " deleted successfuly.");
				else
					System.out.println("Failed to delete user eith " + user + ".");

				return u;

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

}
