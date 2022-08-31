package sd1920.trab2.server.rest.resources;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.inject.Singleton;
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
import sd1920.trab2.api.restOut.MessageServiceOut;
import sd1920.trab2.api.soap.MessageServiceSoap;
import sd1920.trab2.clients.utils.ClientFactory;
import sd1920.trab2.clients.utils.InsecureHostnameVerifier;
import sd1920.trab2.clients.utils.SendMessInfo;
import sd1920.trab2.clients.utils.UsersEmailClient;
import sd1920.trab2.discovery.Discovery;
import sd1920.trab2.dropbox.ProxyRequests;

@Singleton
public class MessageResourceRestOut implements MessageServiceOut {

	public final static int CONNECTION_TIMEOUT = 30000;
	public final static int REPLY_TIMEOUT = 20000;
	private static String INTERNAL_SECRET;
	public static int PORT;

	private static Logger Log = Logger.getLogger(MessageResourceRestOut.class.getName());
	private static Random randomNumberGenerator;
	private Discovery dis;
	private static ProxyRequests pr;

	private final Map<String, BlockingQueue<SendMessInfo>> information;
	private final List<Thread> threads;
	UsersEmailClient localUserClient;

	public MessageResourceRestOut() {
		MessageResourceRestOut.randomNumberGenerator = new Random(System.currentTimeMillis());
		this.information = new HashMap<String, BlockingQueue<SendMessInfo>>();
		this.threads = new LinkedList<Thread>();
	}

	public MessageResourceRestOut(Discovery dis, int port, String secret, ProxyRequests pr) {
		MessageResourceRestOut.randomNumberGenerator = new Random(System.currentTimeMillis());
		this.dis = dis;
		information = new HashMap<String, BlockingQueue<SendMessInfo>>();
		threads = new LinkedList<Thread>();
		PORT = port;
		MessageResourceRestOut.pr = pr;
		INTERNAL_SECRET = secret;
		localUserClient = ClientFactory.getUsersClient(getLocalServerURI(), 5, 1000);
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
					String serverUrl = ola[0].toURL().toString();

					if (serverUrl.contains("rest")) {
						ClientConfig config = new ClientConfig();
						config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
						config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMEOUT);
						Client client = ClientBuilder.newClient(config);
						WebTarget target = client.target(serverUrl).path(MessageServiceOut.PATH).path("out");

						Message m = null;

						// This will allow client code executed by this process to ignore hostname
						// verification
						HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

						synchronized (this) {
							m = toSend.getMess();
						}

						try {
							if (toSend.toPostMessage()) {

								Response r = target.queryParam("secret", INTERNAL_SECRET).request()
										.accept(MediaType.APPLICATION_JSON)
										.post(Entity.entity(m, MediaType.APPLICATION_JSON));

								if (r.getStatus() == Status.OK.getStatusCode() && r.hasEntity()) {
									String[] usersFailed = r.readEntity(String[].class);
									putErrorMessage(m, usersFailed);
									toSend = null;
								} else {
									Log.info("Error, HTTP error status: " + r.getStatus());
								}
							} else {

								Response r = target.path(("" + m.getId())).queryParam("secret", INTERNAL_SECRET)
										.request().delete();

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
						// This will allow client code executed by this process to ignore hostname
						// verification
						HttpsURLConnection.setDefaultHostnameVerifier(new InsecureHostnameVerifier());

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
	public long postMessage(String pwd, Message msg) {

		Log.info("Received request to register a new message (Sender: " + msg.getSender() + "; Subject: "
				+ msg.getSubject() + ")");

		String[] sender = msg.getSender().split("@");

		if (msg.getSender() == null || msg.getDestination() == null || msg.getDestination().size() == 0) {
			Log.info("Message was rejected due to lack of recepients.");
			throw new WebApplicationException(Status.CONFLICT);
		}
		User u = localUserClient.getUser(sender[0], pwd);
		if (u == null) {
			Log.info("Authentication Problems");
			throw new WebApplicationException(Status.FORBIDDEN);
		}
		Message message = msg;
		String sendFormat = String.format("%s <%s@%s>", u.getDisplayName(), u.getName(), u.getDomain());
		message.setSender(sendFormat);

		long newID = 0;
		synchronized (this) {
			newID = Math.abs(randomNumberGenerator.nextLong());
			message.setId(newID);
			String aux = String.format("/%s/allMessages/%d.txt", getLocalDomain(), newID);
			while (pr.messageUpload(aux, message) == 409) {
				newID = Math.abs(randomNumberGenerator.nextLong());
				message.setId(newID);
				aux = String.format("/%s/allMessages/%d.txt", getLocalDomain(), newID);
			}
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
						String aux = String.format("/%s/%s/received/%d.txt", getLocalDomain(), receive[0], newID);
						int response = pr.messageUpload(aux, message);
						if (response == -1) {
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
	public Message getMessage(String user, long mid, String pwd) {

		Log.info("Received request for message with id: " + mid + ".");
		Message m = null;

		if (localUserClient.getUser(user, pwd) == null) {
			Log.info("The sender does not exist or if the pwd is not correct");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		synchronized (this) {
			String path = String.format("/%s/allMessages/%d.txt", getLocalDomain(), mid);
			m = pr.getMessage(path);
		}

		if (m == null) {
			Log.info("Requested message does not exists.");
			throw new WebApplicationException(Status.NOT_FOUND);
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

		String path = String.format("/%s/%s/received/%d.txt", getLocalDomain(), receive[0], mid);
		Message msg = pr.getMessage(path);
		if (found && msg != null) {
			Log.info("Returning requested message to user.");
			return m;
		} else {
			throw new WebApplicationException(Status.NOT_FOUND);
		}
	}

	@Override
	public List<Long> getMessages(String user, String pwd) {

		if (localUserClient.getUser(user, pwd) == null) {
			Log.info("The sender does not exist or if the pwd is not correct");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		Log.info("Received request for messages with optional user parameter set to: '" + user + "'");
		List<Long> messages = new ArrayList<Long>();

		Log.info("Collecting all messages in server for user " + user);
		synchronized (this) {
			String path = String.format("/%s/%s/received", getLocalDomain(), user);
			List<String> list = pr.listDirectory(path);
			for (String s : list) {

				String[] vector = s.split("/");
				String[] aux = vector[vector.length - 1].split("\\.");
				long l = Long.parseLong(aux[0]);
				Log.info("Adding messaeg with id: " + l + ".");
				messages.add(l);
			}
		}

		Log.info("Returning message list to user with " + messages.size() + " messages.");
		return messages;
	}

	@Override
	public void removeFromUserInbox(String user, long mid, String pwd) {

		if (localUserClient.getUser(user, pwd) == null) {
			Log.info("The sender does not exist or if the pwd is not correct");
			throw new WebApplicationException(Status.FORBIDDEN);
		}

		synchronized (this) {
			String path = String.format("/%s/%s/received/%d.txt", getLocalDomain(), user, mid);
			if (!pr.delete(path)) {
				Log.info("The message does not exist in the user inbox");
				throw new WebApplicationException(Status.NOT_FOUND);
			}
		}
	}

	@Override
	public void deleteMessage(String user, long mid, String pwd) {
		synchronized (this) {

			if (localUserClient.getUser(user, pwd) == null) {
				Log.info("The sender does not exist or if the pwd is not correct");
				throw new WebApplicationException(Status.FORBIDDEN);
			}
			String path = String.format("/%s/allMessages/%d.txt", getLocalDomain(), mid);
			Message m = pr.getMessage(path);

			if (m != null) {
				String[] u = m.getSender().split("<");
				u = u[1].split("@");

				if (!u[0].equals(user)) {
					Log.info("Only the message's sender can delete this message");
					return;
				}

				List<String> serverSend = new ArrayList<String>();
				for (String dest : m.getDestination()) {
					String[] receive = dest.split("@");
					if (!receive[1].equals(getLocalDomain()) && !serverSend.contains(receive[1])) {

						serverSend.add(receive[1]);
						SendMessInfo e = new SendMessInfo(m);
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
						path = String.format("/%s/%s/received/%d.txt", getLocalDomain(), receive[0], mid);
						pr.delete(path);
					}
				}
				path = String.format("/%s/allMessages/%d.txt", getLocalDomain(), mid);
				pr.delete(path);
			}
		}
	}

	@Override
	public void deleteMessageFromOut(String id, String secret) {
		if (secret == null || !secret.equals(INTERNAL_SECRET))
			throw new WebApplicationException(Response.Status.FORBIDDEN);
		long mid = Long.parseLong(id);

		synchronized (this) {
			String path = String.format("/%s/allMessages/%d.txt", getLocalDomain(), mid);
			Message m = pr.getMessage(path);
			if (m != null) {
				for (String dest : m.getDestination()) {
					String[] receiver = dest.split("@");

					if (receiver[1].equals(getLocalDomain())) {
						path = String.format("/%s/%s/received/%d.txt", getLocalDomain(), receiver[0], mid);
						pr.delete(path);
					}
				}
				path = String.format("/%s/allMessages/%d.txt", getLocalDomain(), mid);
				pr.delete(path);
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
					String aux = String.format("/%s/allMessages/%d.txt", getLocalDomain(), message.getId());
					pr.messageUpload(aux, message);

					aux = String.format("/%s/%s/received/%d.txt", getLocalDomain(), receive[0], message.getId());
					if (pr.messageUpload(aux, message) != 200)
						notAccept[i++] = receiver;
				}
			}
		}
		return notAccept;
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
					error.setId(newID);
					String aux = String.format("/%s/allMessages/%d.txt", getLocalDomain(), newID);
					while (pr.messageUpload(aux, error) == 409) {
						newID = Math.abs(randomNumberGenerator.nextLong());
						error.setId(newID);
						aux = String.format("/%s/allMessages/%d.txt", getLocalDomain(), newID);
					}

					aux = String.format("/%s/%s/received/%d.txt", getLocalDomain(), u[0], newID);
					pr.messageUpload(aux, error);
				}
			}
		}
	}

	/**
	 * Auxiliary method to get the local domain
	 * 
	 * @return the local domain
	 */
	private static String getLocalDomain() {
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