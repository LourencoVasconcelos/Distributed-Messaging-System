package sd1920.trab1.server.soap.resources;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab1.api.User;
import sd1920.trab1.api.soap.MessageServiceSoap;
import sd1920.trab1.api.soap.MessagesException;
import sd1920.trab1.api.soap.UserServiceSoap;
import sd1920.trab1.server.rest.resources.UserResourceRest;

@WebService(serviceName = UserServiceSoap.NAME, targetNamespace = UserServiceSoap.NAMESPACE, endpointInterface = UserServiceSoap.INTERFACE)
public class UserResourceSoap implements UserServiceSoap {

	private static Logger Log = Logger.getLogger(UserResourceRest.class.getName());
	private static String CREATE_INBOX = "Inbox created";
	private static String DELETE_INBOX = "Inbox deleted";
	public final static int CONNECTION_TIMEOUT = 1000;
	public final static int REPLY_TIMEOUT = 600;
	public static int PORT;
	
	// Map to save the users of local domain
	private final Map<String, User> users;

	public UserResourceSoap(int port) {
		users = new HashMap<String, User>();
		PORT = port;
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
		accessInboxes(user.getName(), CREATE_INBOX);

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
				accessInboxes(u.getName(), DELETE_INBOX);
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
	 * Auxiliary method that create or delete an inbox for the given user in local domain
	 * 
	 * @param user the username of the inbox
	 * @param role defines whether to create or delete the inbox
	 */
	private void accessInboxes(String user, String role) {
		MessageServiceSoap messages = null;

		try {
			QName QNAME = new QName(MessageServiceSoap.NAMESPACE, MessageServiceSoap.NAME);
			Service service = Service.create(new URL(getLocalServerURI() + MessageServiceSoap.MESSAGES_WSDL), QNAME);
			messages = service.getPort(sd1920.trab1.api.soap.MessageServiceSoap.class);
		} catch (WebServiceException wse) {
			System.err.println("Could not conntact the server: " + wse.getMessage());
			System.exit(1);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		((BindingProvider) messages).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
				CONNECTION_TIMEOUT);
		((BindingProvider) messages).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT, REPLY_TIMEOUT);

		try {
			if (role.equals(CREATE_INBOX))
				messages.createInbox(user);
			if (role.equals(DELETE_INBOX))
				messages.deleteInbox(user);
		} catch (WebServiceException wse) {
			wse.printStackTrace();
		}
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
		String serverURI = String.format("http://%s:%s/soap", ip, PORT);

		return serverURI;
	}

}