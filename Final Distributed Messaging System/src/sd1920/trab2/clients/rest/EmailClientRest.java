package sd1920.trab2.clients.rest;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

public abstract class EmailClientRest {
	public final static int CONNECTION_TIMEOUT = 60000;
	public final static int REPLY_TIMEOUT = 45000;

	WebTarget target;
	int maxRetries;
	int retryPeriod;
	javax.ws.rs.client.Client restClient;

	public EmailClientRest(String serverUrl, int maxRetries, int retryPeriod, String resourceUrl) {
		ClientConfig config = new ClientConfig();
		config.property(ClientProperties.CONNECT_TIMEOUT, CONNECTION_TIMEOUT);
		config.property(ClientProperties.READ_TIMEOUT, REPLY_TIMEOUT);
		restClient = ClientBuilder.newClient(config);
		target = restClient.target(serverUrl).path(resourceUrl);
		this.maxRetries = maxRetries;
		this.retryPeriod = retryPeriod;
	}
}
