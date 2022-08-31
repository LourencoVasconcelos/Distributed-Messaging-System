package sd1920.trab2.clients.rest;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.rest.MessageService;
import sd1920.trab2.api.restReplic.MessageServiceReplic;
import sd1920.trab2.clients.utils.MessagesEmailClient;

public class MessageClientRest extends EmailClientRest implements MessagesEmailClient {

	public MessageClientRest(String serverUrl, int maxRetries, int retryPeriod) {
		super(serverUrl, maxRetries, retryPeriod, MessageService.PATH);
	}

	@Override
	public Response createInbox(String user, String secret) {
		System.out.println(target.toString());
		return target.path("outuser").path(user).queryParam("secret", secret).request().post(Entity.json(""));
	}

	@Override
	public Response deleteInbox(String user, String secret) {

		return target.path("outuser").path(user).queryParam("secret", secret).request().delete();
	}

	@Override
	public Response forwardPostMessage(Message m, String secret) {

		return target.path("out").queryParam("secret", secret).request().accept(MediaType.APPLICATION_JSON)
				.post(Entity.entity(m, MediaType.APPLICATION_JSON));
	}

	@Override
	public Response forwardDeleteMessage(Message m, String secret) {

		return target.path("out").path(("" + m.getId())).queryParam("secret", secret).request().delete();
	}

	@Override
	public int postMessageInReplic(Message m, String serverURI, String secret, long version) {
		int success = 0;
		WebTarget targ = restClient.target(serverURI).path(MessageServiceReplic.PATH);
		try {
			Response r = targ.path("primary").queryParam("secret", secret).request()
					.header(MessageServiceReplic.HEADER_VERSION, version).accept(MediaType.APPLICATION_JSON)
					.post(Entity.entity(m, MediaType.APPLICATION_JSON));
			if (r.getStatus() == Status.OK.getStatusCode()) {
				success++;
			} else {
				System.out.println("Error, HTTP error status: " + r.getStatus());
			}

		} catch (ProcessingException e) {
			return success;
		}

		return success;
	}

	@Override
	public int removeMessageInReplic(String user, long mid, String serverURI, String secret, long version) {
		int success = 0;

		WebTarget targ = restClient.target(serverURI).path(MessageServiceReplic.PATH).path("primary").path("mbox")
				.path(user).path("" + mid).queryParam("secret", secret);

		Response r = targ.request().header(MessageServiceReplic.HEADER_VERSION, version).delete();

		if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
			System.out.println("Success");
			success++;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
		}

		return success;
	}

	@Override
	public int deleteMessageInReplic(long mid, String serverURI, String secret, long version) {
		int success = 0;
		WebTarget targ = restClient.target(serverURI).path(MessageServiceReplic.PATH).path("primary").path("msg")
				.path("" + mid).queryParam("secret", secret);

		Response r = targ.request().header(MessageServiceReplic.HEADER_VERSION, version).delete();

		if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
			System.out.println("Success");
			success++;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
		}

		return success;
	}

	@Override
	public int deleteMessageFromOutInReplic(String id, String serverURI, String secret, long version) {
		int success = 0;
		WebTarget targ = restClient.target(serverURI).path(MessageServiceReplic.PATH).path("primary").path("out")
				.path(id).queryParam("secret", secret);

		Response r = targ.request().header(MessageServiceReplic.HEADER_VERSION, version).delete();

		if (r.getStatus() == Status.NO_CONTENT.getStatusCode()) {
			System.out.println("Success");
			success++;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
		}

		return success;
	}

	@Override
	public int receiveMessageFromOutInReplic(Message m, String serverURI, String secret, long version) {
		int success = 0;
		WebTarget target = restClient.target(serverURI).path(MessageServiceReplic.PATH).path("primary").path("out")
				.queryParam("secret", secret);

		Response r = target.request().header(MessageServiceReplic.HEADER_VERSION, version)
				.accept(MediaType.APPLICATION_JSON).post(Entity.entity(m, MediaType.APPLICATION_JSON));

		if (r.getStatus() == Status.OK.getStatusCode() && r.hasEntity()) {
			System.out.println("Success");
			success++;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
		}

		return success;
	}

}
