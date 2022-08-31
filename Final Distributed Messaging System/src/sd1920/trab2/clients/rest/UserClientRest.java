package sd1920.trab2.clients.rest;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import sd1920.trab2.api.User;
import sd1920.trab2.api.rest.UserService;
import sd1920.trab2.api.restReplic.MessageServiceReplic;
import sd1920.trab2.clients.utils.UsersEmailClient;

public class UserClientRest extends EmailClientRest implements UsersEmailClient {

	public UserClientRest(String serverUrl, int maxRetries, int retryPeriod) {
		super(serverUrl, maxRetries, retryPeriod, UserService.PATH);
	}

	@Override
	public User getUser(String name, String pwd) {
		WebTarget aux = target;
		if (pwd != null)
			target = target.queryParam("pwd", pwd);
		System.out.println("TOU AQUI");
		System.out.println(target.toString());
		Response r = null;
		try {
			r = target.path(name).request().accept(MediaType.APPLICATION_JSON).get();
		} catch (ProcessingException e) {
			e.printStackTrace();
		}

		User u = null;
		if (r.getStatus() == Status.OK.getStatusCode() && r.hasEntity()) {
			u = r.readEntity(User.class);
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
		}
		target = aux;
		return u;
	}

	@Override
	public User getUserForReplic(String name, String pwd, long version, String serverURI) {
		WebTarget targ = restClient.target(serverURI).path(UserService.PATH);

		if (pwd != null)
			targ = targ.queryParam("pwd", pwd);

		Response r = targ.path(name).request().header(MessageServiceReplic.HEADER_VERSION, version)
				.accept(MediaType.APPLICATION_JSON).get();

		User u = null;
		if (r.getStatus() == Status.OK.getStatusCode() && r.hasEntity()) {
			System.out.println("Success:");
			u = r.readEntity(User.class);
		} else
			System.out.println("Error, HTTP error status: " + r.getStatus());
		return u;
	}

	@Override
	public int postUserInReplic(User user, String serverURI, String secret, long version) {
		int success = 0;
		WebTarget targ = restClient.target(serverURI).path(UserService.PATH).path("primary").queryParam("secret",
				secret);

		Response r = targ.request().header(MessageServiceReplic.HEADER_VERSION, version)
				.accept(MediaType.APPLICATION_JSON).post(Entity.entity(user, MediaType.APPLICATION_JSON));

		if (r.getStatus() == Status.OK.getStatusCode()) {
			System.out.println("Success");
			success++;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
		}

		return success;
	}

	@Override
	public int updateInReplic(String name, String pwd, User user, String serverURI, String secret, long version) {
		int success = 0;
		WebTarget targ = restClient.target(serverURI).path(UserService.PATH).path("primary").path(name);

		if (pwd != null)
			targ = targ.queryParam("pwd", pwd).queryParam("secret", secret);

		Response r = targ.request().header(MessageServiceReplic.HEADER_VERSION, version)
				.accept(MediaType.APPLICATION_JSON).put(Entity.entity(user, MediaType.APPLICATION_JSON));

		if (r.getStatus() == Status.OK.getStatusCode()) {
			System.out.println("Success");
			success++;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
		}

		return success;
	}

	@Override
	public int deleteInReplic(String name, String pwd, String serverURI, String secret, long version) {
		int success = 0;

		WebTarget targ = restClient.target(serverURI).path(UserService.PATH).path("primary").path(name);

		if (pwd != null)
			targ = targ.queryParam("pwd", pwd).queryParam("secret", secret);

		Response r = targ.request().header(MessageServiceReplic.HEADER_VERSION, version).delete();

		if (r.getStatus() == Status.OK.getStatusCode()) {
			System.out.println("Success");
			success++;
		} else {
			System.out.println("Error, HTTP error status: " + r.getStatus());
		}

		return success;
	}

}
