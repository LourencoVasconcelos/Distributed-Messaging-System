package sd1920.trab2.server.soap.resources;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.jws.WebService;
import javax.net.ssl.HttpsURLConnection;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.User;
import sd1920.trab2.api.rest.MessageService;
import sd1920.trab2.api.soap.MessageServiceSoap;
import sd1920.trab2.api.soap.MessagesException;
import sd1920.trab2.clients.utils.ClientFactory;
import sd1920.trab2.clients.utils.InsecureHostnameVerifier;
import sd1920.trab2.clients.utils.SendMessInfo;
import sd1920.trab2.clients.utils.UsersEmailClientSoap;
import sd1920.trab2.discovery.Discovery;
import sd1920.trab2.server.rest.resources.MessageResourceRest;

@WebService(serviceName = MessageServiceSoap.NAME, targetNamespace = MessageServiceSoap.NAMESPACE, endpointInterface = MessageServiceSoap.INTERFACE)
public class MessageResourceSoap implements MessageServiceSoap {

	public final static int CONNECTION_TIMEOUT = 1000;
	public final static int REPLY_TIMEOUT = 10000;
	private static final String INTERNAL_SECRET = "secret";
	public static int PORT;

	private static Logger Log = Logger.getLogger(MessageServiceSoap.class.getName());
	private static Random randomNumberGenerator;
	private Discovery dis;

	private static Map<Long, Message> allMessages;
	private static Map<String, Set<Long>> userInboxs;
	private final Map<String, BlockingQueue<SendMessInfo>> information;
	private final List<Thread> threads;
	UsersEmailClientSoap localUserClient;
	
	public MessageResourceSoap() {
		MessageResourceSoap.randomNumberGenerator = new Random(System.currentTimeMillis());
		MessageResourceSoap.allMessages = new HashMap<Long, Message>();
		MessageResourceSoap.userInboxs = new HashMap<String, Set<Long>>();
		this.information = new HashMap<String, BlockingQueue<SendMessInfo>>();
		this.threads = new LinkedList<Thread>();
	}

	public MessageResourceSoap(Discovery dis, int port) {
		MessageResourceSoap.randomNumberGenerator = new Random(System.currentTimeMillis());
		this.dis = dis;
		MessageResourceSoap.allMessages = new HashMap<Long, Message>();
		MessageResourceSoap.userInboxs = new HashMap<String, Set<Long>>();
		information = new HashMap<String, BlockingQueue<SendMessInfo>>();
		threads = new LinkedList<Thread>();
		PORT = port;
		localUserClient =  ClientFactory.getUsersClientSoap(getLocalServerURI(), 5, 1000);
	}

	/**
	 * Class thread to send a message of one server to another
	 *
	 */
	static class ThreadRun implements Runnable {

		
		BlockingQueue<SendMessInfo> info;
		private Discovery discovery;
		SendMessInfo toSend;
		String server;

		ThreadRun(BlockingQueue<SendMessInfo> info, Discovery dis, String server) {
			this.info = info;
			this.discovery = dis;
			toSend = null;
			this.server = server;
		}

		public void run() {
			try {
				for (;;) {

					if (toSend == null)
						toSend = info.take();

					URI[] ola = discovery.knownUrisOf(server);
					String serverUrl = ola[ola.length - 1].toURL().toString();

					if (serverUrl.contains("rest")) {
						ClientConfig config = new ClientConfig();
						config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
						config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMEOUT);
						Client client = ClientBuilder.newClient(config);
						WebTarget target = client.target(serverUrl).path(MessageService.PATH).path("out");

						Message m = null;
						//This will allow client code executed by this process to ignore hostname verification
						HttpsURLConnection.setDefaultHostnameVerifier( new InsecureHostnameVerifier());
						
						synchronized (this) {
							m = toSend.getMess();
						}

						try {
							if (toSend.toPostMessage()) {

								Response r = target.queryParam("secret", INTERNAL_SECRET).request().accept(MediaType.APPLICATION_JSON)
										.post(Entity.entity(m, MediaType.APPLICATION_JSON));

								if (r.getStatus() == Status.OK.getStatusCode() && r.hasEntity()) {
									String[] usersFailed = r.readEntity(String[].class);
									putErrorMessage(m, usersFailed);
									toSend = null;
								} else {
									System.out.println("Error, HTTP error status: " + r.getStatus());
								}

							} else {

								Response r = target.path(("" + m.getId())).queryParam("secret", INTERNAL_SECRET).request().delete();

								if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
									Log.info("Success, message deleted with id: " + ("" + m.getId()));
									toSend = null;
								} else {
									Log.info("Error, HTTP error status: " + r.getStatus());
								}
							}

						} catch (ProcessingException e) {
							e.printStackTrace();
						}
					} else if (serverUrl.contains("soap")) {
						
						MessageServiceSoap messages = null;
						//This will allow client code executed by this process to ignore hostname verification
						HttpsURLConnection.setDefaultHostnameVerifier( new InsecureHostnameVerifier());
						messages = soapRequest(serverUrl, messages);
						Message m = null;
						
						synchronized (this) {
							m = toSend.getMess();
						}

						if (toSend.toPostMessage()) {
							String[] received = null;
							try {
								received = messages.receiveMessageFromOut(m, INTERNAL_SECRET);
								if (received.length != 0) {
									toSend = null;
								}
								putErrorMessage(m, received);
							} catch (WebServiceException wse) {
								wse.printStackTrace();
							}

						} else {
							try {
								messages.deleteMessageFromOut(("" + m.getId()), INTERNAL_SECRET);
								toSend = null;
							} catch (WebServiceException wse) {
								wse.printStackTrace();
							}
						}
					}
				}
			} catch (Exception e) {
				System.out.println("Receiver done.");
			}
		}

		/**
		 * Auxiliary method to contact a soap server
		 * 
		 * @param serverUrl serverUrl of the server to be contacted
		 * @param messages  soap server to be contacted
		 * @return soap server to make a request
		 */
		private MessageServiceSoap soapRequest(String serverUrl, MessageServiceSoap messages) {
			boolean connected = true;
			try {
				while (messages == null) {
					connected = true;
					QName QNAME = new QName(MessageServiceSoap.NAMESPACE, MessageServiceSoap.NAME);

					URL url = new URL(serverUrl + MessageServiceSoap.MESSAGES_WSDL);
					URLConnection con;

					try {
						con = url.openConnection();
						con.setConnectTimeout(CONNECTION_TIMEOUT);
						con.connect();
					} catch (IOException e) {
						connected = false;
					}

					if (connected) {
						Service service = Service.create(url, QNAME);
						messages = service.getPort(sd1920.trab2.api.soap.MessageServiceSoap.class);
					}
				}
			} catch (WebServiceException wse) {
				wse.printStackTrace();
				System.err.println("Could not conntact the server: " + wse.getMessage());

			} catch (MalformedURLException e) {
				e.printStackTrace();
			}

			((BindingProvider) messages).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
					CONNECTION_TIMEOUT);
			((BindingProvider) messages).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
					REPLY_TIMEOUT);

			return messages;
		}

	}

	@Override
	public long postMessage(String pwd, Message msg) throws MessagesException {

		Log.info("Received request to register a new message (Sender: " + msg.getSender() + "; Subject: "
				+ msg.getSubject() + ")");

		String[] sender = msg.getSender().split("@");

		if (msg.getSender() == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			throw new MessagesException("Message was rejected due to lack of recepients.");
		}

		User u = localUserClient.getUser(sender[0], pwd);
		if (u == null) {
			throw new MessagesException("User null does not exist");
		}

		Message message = msg;
		String sendFormat = String.format("%s <%s@%s>", u.getDisplayName(), u.getName(), u.getDomain());
		message.setSender(sendFormat);

		long newID = 0;
		synchronized (this) {
			newID = Math.abs(randomNumberGenerator.nextLong());
			while (allMessages.containsKey(newID)) {
				newID = Math.abs(randomNumberGenerator.nextLong());
			}
			message.setId(newID);
			allMessages.put(newID, message);
		}


		synchronized (this) {
			List<String> serverSend = new ArrayList<String>();
			for (String receiver : msg.getDestination()) {
				String[] receive = receiver.split("@");
				if (!receive[1].equals(getLocalDomain()) && !serverSend.contains(receive[1])) {
					serverSend.add(receive[1]);

					SendMessInfo e = new SendMessInfo(message);
					e.setBoolean(true);

					if (information.containsKey(receive[1])) {
						BlockingQueue<SendMessInfo> aux = information.get(receive[1]);
						aux.add(e);
					} else {
						BlockingQueue<SendMessInfo> aux = new LinkedBlockingQueue<SendMessInfo>();
						aux.add(e);
						information.put(receive[1], aux);
						Thread thread = new Thread(new ThreadRun(aux, dis, receive[1]));
						thread.start();
						threads.add(thread);
					}

				} else {
					if (receive[1].equals(getLocalDomain())) {
						if (userInboxs.containsKey(receive[0])) {
							userInboxs.get(receive[0]).add(newID);
						} else {
							String[] err = new String[] { receiver };
							putErrorMessage(message, err);
						}
					}
				}
			}
		}

		Log.info("Recorded message with identifier: " + newID);
		return newID;
	}

	@Override
	public Message getMessage(String user, String pwd, long mid) throws MessagesException {

		Log.info("Received request for message with id: " + mid + ".");
		Message m = null;

		if (localUserClient.getUser(user, pwd) == null) {
			throw new MessagesException("The sender does not exist or if the pwd is not correct");
		}

		synchronized (this) {
			m = allMessages.get(mid);
		}

		if (m == null) {
			throw new MessagesException("Requested message does not exists.");
		}

		boolean found = false;
		String[] receive = null;
		for (String receiver : m.getDestination()) {
			receive = receiver.split("@");
			if (receive[0].equals(user)) {
				found = true;
				break;
			}
		}
		String[] u = m.getSender().split("<");
		u = u[1].split("@");
		if (user.equals(u[0])) {
			found = true;
			receive[0] = u[0];
		}

		if (found && userInboxs.get(receive[0]).contains(mid)) {
			Log.info("Returning requested message to user.");
			return m;
		} else {
			throw new MessagesException("Message does not exist");
		}
	}

	@Override
	public List<Long> getMessages(String user, String pwd) throws MessagesException {

		if (localUserClient.getUser(user, pwd) == null) {
			throw new MessagesException("The sender does not exist or if the pwd is not correct");
		}

		Log.info("Received request for messages with optional user parameter set to: '" + user + "'");
		List<Long> messages = new ArrayList<Long>();
		if (user == null) {
			Log.info("Collecting all messages in server");
			synchronized (this) {
				messages.addAll(userInboxs.get(user));
			}

		} else {
			Log.info("Collecting all messages in server for user " + user);
			synchronized (this) {
				Set<Long> mids = userInboxs.getOrDefault(user, Collections.emptySet());
				for (Long l : mids) {
					Log.info("Adding messaeg with id: " + l + ".");
					messages.add(l);
				}
			}

		}
		Log.info("Returning message list to user with " + messages.size() + " messages.");
		return messages;
	}

	@Override
	public void removeFromUserInbox(String user, String pwd, long mid) throws MessagesException {

		if (localUserClient.getUser(user, pwd) == null) {
			throw new MessagesException("The sender does not exist or if the pwd is not correct");
		}

		synchronized (this) {
			if (!allMessages.containsKey(mid)) {
				throw new MessagesException("The message does not exist in the server");
			}

			if (!userInboxs.get(user).contains(mid)) {
				throw new MessagesException("The message does not exist in the user inbox");
			}

			userInboxs.get(user).remove(mid);

		}

	}

	@Override
	public void deleteMessage(String user, String pwd, long mid) throws MessagesException {
		synchronized (this) {
			if (!userInboxs.containsKey(user) || localUserClient.getUser(user, pwd) == null) {
				throw new MessagesException("The sender does not exist or if the pwd is not correct");
			}

			if (allMessages.containsKey(mid)) {
				String[] u = allMessages.get(mid).getSender().split("<");
				u = u[1].split("@");


				if (!u[0].equals(user)) {
					Log.info("Only the message's sender can delete this message");
					return;
				}

				List<String> serverSend = new ArrayList<String>();
				for (String dest : allMessages.get(mid).getDestination()) {
					String[] receive = dest.split("@");
					if (!receive[1].equals(getLocalDomain()) && !serverSend.contains(receive[1])) {

						serverSend.add(receive[1]);
						SendMessInfo e = new SendMessInfo(allMessages.get(mid));
						e.setBoolean(false);

						if (information.containsKey(receive[1])) {
							BlockingQueue<SendMessInfo> aux = information.get(receive[1]);
							aux.add(e);
						} else {
							BlockingQueue<SendMessInfo> aux = new LinkedBlockingQueue<SendMessInfo>();
							aux.add(e);
							information.put(receive[1], aux);
							Thread thread = new Thread(new ThreadRun(aux, dis, receive[1]));
							thread.start();
							threads.add(thread);
						}

					} else {
						if (userInboxs.containsKey(receive[0]))
							userInboxs.get(receive[0]).remove(mid);
					}
				}
				allMessages.remove(mid);
			}
		}
	}

	@Override
	public void deleteMessageFromOut(String id, String secret) {
        if (secret == null || !secret.equals(INTERNAL_SECRET))
            throw new WebApplicationException(Response.Status.FORBIDDEN);
		long mid = Long.parseLong(id);
		synchronized (this) {
			if (allMessages.containsKey(mid)) {
				for (String dest : allMessages.get(mid).getDestination()) {
					String[] receiver = dest.split("@");
					if (receiver[1].equals(getLocalDomain()) && userInboxs.get(receiver[0]) != null) {
						userInboxs.get(receiver[0]).remove(mid);
					}
				}
				allMessages.remove(mid);
			}
		}
	}

	@Override
	public String[] receiveMessageFromOut(Message message, String secret) {

        if (secret == null || !secret.equals(INTERNAL_SECRET))
            throw new WebApplicationException(Response.Status.FORBIDDEN);
		String[] notAccept = new String[message.getDestination().size()];
		int i = 0;

		synchronized (this) {
			for (String receiver : message.getDestination()) {
				String[] receive = receiver.split("@");
				if (receive[1].equals(getLocalDomain())) {
					if (!allMessages.containsKey(message.getId())) {
						allMessages.put(message.getId(), message);
					}
					if (userInboxs.containsKey(receive[0]))
						userInboxs.get(receive[0]).add(message.getId());
					else
						notAccept[i++] = receiver;
				}
			}
		}

		return notAccept;
	}

	@Override
	public void createInbox(String user, String secret) {
        if (secret == null || !secret.equals(INTERNAL_SECRET))
            throw new WebApplicationException(Response.Status.FORBIDDEN);
		synchronized (this) {
			userInboxs.put(user, new HashSet<Long>());
		}
		Log.info("Inbox created for user: " + user + ".");
	}

	@Override
	public void deleteInbox(String user, String secret) {
        if (secret == null || !secret.equals(INTERNAL_SECRET))
            throw new WebApplicationException(Response.Status.FORBIDDEN);
		synchronized (this) {
			userInboxs.remove(user);
		}
		Log.info("Inbox of user: " + user + ", was deleted.");
	}

	/**
	 * Puts an error message in the sender inbox
	 * 
	 * @param m           message that sending failed
	 * @param usersFailed array of users' username that sending failed
	 */
	private static void putErrorMessage(Message m, String[] usersFailed) {
		System.out.println(usersFailed[0]);
		for (int i = 0; i < usersFailed.length; i++) {
			if (usersFailed[i] != null) {

				String subject = String.format("FALHA NO ENVIO DE %d PARA %s", m.getId(), usersFailed[i]);
				Message error = new Message(m.getId(), m.getSender(), m.getDestination(), subject, m.getContents());

				String[] u = m.getSender().split("<");
				u = u[1].split("@");

				long newID = 0;
				synchronized (MessageResourceRest.class) {
					newID = Math.abs(randomNumberGenerator.nextLong());
					while (allMessages.containsKey(newID)) {
						newID = Math.abs(randomNumberGenerator.nextLong());
					}
					error.setId(newID);
					allMessages.put(newID, error);
					userInboxs.get(u[0]).add(newID);
				}
			}
		}
	}


	/**
	 * Auxiliary method to get the local domain
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
	 * Auxiliary method to get the local serverURI
	 * 
	 * @return the local serverURI
	 */
	private String getLocalServerURI() {
		String serverUrl = "";
		try {
			serverUrl = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		serverUrl = String.format("https://%s:%s/soap", serverUrl, PORT);
		return serverUrl;
	}

}