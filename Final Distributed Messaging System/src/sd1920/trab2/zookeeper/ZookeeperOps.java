package sd1920.trab2.zookeeper;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import com.google.gson.Gson;

import sd1920.trab2.Operations.Operation;
import sd1920.trab2.api.Message;
import sd1920.trab2.api.User;
import sd1920.trab2.api.restReplic.MessageServiceReplic;
import sd1920.trab2.api.restReplic.UserServiceReplic;
import sd1920.trab2.server.rest.resources.MessageResourceRestReplic;
import sd1920.trab2.server.rest.resources.UserResourceRestReplic;

@Singleton
public class ZookeeperOps {

	private ZookeeperProcessor zk;
	private String serverURI;
	private String domain;
	private List<String> lst;
	private String primaryPath;
	private Map<Long, Operation> opsMap;
	private long version;
	String primaryURI;
	private MessageResourceRestReplic msgServer;
	private UserResourceRestReplic userServer;
	static final Gson json = new Gson();

	public final static int CONNECTION_TIMEOUT = 100000;
	public final static int REPLY_TIMEOUT = 60000;
	private final static String INTERNAL_SECRET = "secret";
	static final byte OP_USER_POSTUSER = 1;
	static final byte OP_USER_UPDATEUSER = 2;
	static final byte OP_USER_DELETEUSER = 3;
	static final byte OP_MESSAGE_POSTMESSAGE = 4;
	static final byte OP_MESSAGE_DELETEMESSAGE = 5;
	static final byte OP_MESSAGE_REMOVE_FROM_INBOX_MESSAGE = 6;

	public ZookeeperOps(String domain, String serverURI) throws Exception {
		zk = new ZookeeperProcessor("kafka:2181");
		opsMap = new HashMap<Long, Operation>(); // meter Size, a stora de ADA vai chumbar-nos
		primaryPath = "";
		String newPath = zk.write("/" + domain, CreateMode.PERSISTENT);
		if (newPath != null) {
			System.out.println("Created znode: " + newPath);
		}

		zk.getChildren("/" + domain, new Watcher() {
			@Override
			public void process(WatchedEvent event) {
				lst = zk.getChildren("/" + domain, this);

				String bla = lst.get(0);
				for (int i = 1; i < lst.size(); i++) {
					String next = lst.get(i);
					long aux = Long.parseLong(bla.split("_")[1]);
					long current = Long.parseLong(next.split("_")[1]);
					if (current < aux)
						bla = next;
				}

				String newPrimaryPath = "/" + domain + "/" + bla;

				if (!primaryPath.equals("")) {
					if (!newPrimaryPath.equalsIgnoreCase(primaryPath)
							&& serverURI.equalsIgnoreCase(new String(zk.getData(newPrimaryPath)))) {
						synchronized (this) {
							setFlags();
							updatePrimary();
							setFlags();
						}
					}
				}
				primaryPath = newPrimaryPath;

			}

		});

		newPath = zk.write("/" + domain + "/bla_", serverURI, CreateMode.EPHEMERAL_SEQUENTIAL);

		System.out.println("Created child znode: " + newPath);

		this.domain = domain;
		this.serverURI = serverURI;
	}

	public void regist(MessageResourceRestReplic msgs, UserResourceRestReplic users) {
		msgServer = msgs;
		userServer = users;
	}

	private void updatePrimary() {
		for (int i = 0; i < lst.size(); i++) {
			String path = "/" + domain + "/" + lst.get(i);
			String serverTest = new String(zk.getData(path));
			if (!serverURI.equals(serverTest)) {
				syncPrimary(serverTest);
			}
		}
	}

	public boolean syncPrimary(String serverToRequest) {
		WebTarget target = getClient(serverToRequest);
		Response r = target.request().header(MessageServiceReplic.HEADER_VERSION, version)
				.accept(MediaType.APPLICATION_JSON).get();

		if (r.getStatus() == Status.OK.getStatusCode()) {
			List<Operation> ops = r.readEntity(new GenericType<List<Operation>>() {
			});

			executeOps(ops);
			return true;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
			return false;
		}

	}

	private WebTarget getClient(String serverToRequest) {
		ClientConfig config = new ClientConfig();
		config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
		config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMEOUT);
		Client client = ClientBuilder.newClient(config);
		WebTarget target = client.target(serverToRequest).path(UserServiceReplic.PATH).path("getOperations");
		return target;
	}

	public List<String> getAllReplic() {
		List<String> newList = new LinkedList<String>();
		for (int i = 0; i < lst.size(); i++) {
			String path = "/" + domain + "/" + lst.get(i);
			newList.add(new String(zk.getData(path)));
		}
		return newList;
	}

	public String getPrimaryURI() {

		String bla = lst.get(0);
		for (int i = 1; i < lst.size(); i++) {
			String next = lst.get(i);
			long aux = Long.parseLong(bla.split("_")[1]);
			long current = Long.parseLong(next.split("_")[1]);
			if (current < aux)
				bla = next;
		}
		String p = "/" + domain + "/" + bla;

		return new String(zk.getData(p));
	}

	public void addOperation(Operation operation) {
		opsMap.put(version++, operation);
	}

	public long getVersion() {
		return version;
	}

	public List<Operation> getOperations(Long versNumber) {
		List<Operation> opsMissing = new LinkedList<Operation>();

		for (long i = versNumber; i < version; i++) {

			opsMissing.add(opsMap.get(i));
		}

		return opsMissing;
	}

	public void setFlags() {
		msgServer.setFlag();
		userServer.setFlag();
	}

	public void updateState() {
		syncSecondary();
	}

	public boolean syncSecondary() {

		WebTarget target = getClient(getPrimaryURI());
		Response r = target.request().header(MessageServiceReplic.HEADER_VERSION, getVersion())
				.accept(MediaType.APPLICATION_JSON).get();

		if (r.getStatus() == Status.OK.getStatusCode()) {
			List<Operation> ops = r.readEntity(new GenericType<List<Operation>>() {
			});

			executeOps(ops);
			return true;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
			return false;
		}

	}

	private void executeOps(List<Operation> ops) {
		for (int i = 0; i < ops.size(); i++) {

			Operation op = ops.get(i);

			if (op != null) {

				switch (op.getCode()) {
				case OP_USER_POSTUSER:
					userServer.postUserFromPrimary(json.fromJson(op.getParams()[0], User.class), null, INTERNAL_SECRET);
					break;
				case OP_USER_UPDATEUSER:
					userServer.updateUserFromPrimary(op.getParams()[0], op.getParams()[1],
							json.fromJson(op.getParams()[2], User.class), null, INTERNAL_SECRET);
					break;
				case OP_USER_DELETEUSER:
					userServer.deleteUserFromPrimary(op.getParams()[0], op.getParams()[1], null, INTERNAL_SECRET);
					break;
				case OP_MESSAGE_POSTMESSAGE:
					msgServer.postMessageFromPrimary(json.fromJson(op.getParams()[0], Message.class), null,
							INTERNAL_SECRET);
					break;
				case OP_MESSAGE_DELETEMESSAGE:
					msgServer.deleteMessageFromPrimary(Long.parseLong(op.getParams()[0]), null, INTERNAL_SECRET);
					break;
				case OP_MESSAGE_REMOVE_FROM_INBOX_MESSAGE:
					msgServer.removeFromUserInboxFromPrimary(op.getParams()[0], Long.parseLong(op.getParams()[1]), null,
							INTERNAL_SECRET);
					break;
				default:
					System.out.println("No operation found");
				}

			}
		}
	}

}
