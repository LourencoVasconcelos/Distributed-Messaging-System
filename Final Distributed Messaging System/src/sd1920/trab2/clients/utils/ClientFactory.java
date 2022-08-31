package sd1920.trab2.clients.utils;

import sd1920.trab2.clients.rest.MessageClientRest;
import sd1920.trab2.clients.rest.UserClientRest;
import sd1920.trab2.clients.soap.MessageClientSoap;
import sd1920.trab2.clients.soap.UserClientSoap;

public class ClientFactory {
	public static MessagesEmailClient getMessagesClient(String url, int maxRetries, int retryPeriod) {
		String[] split = url.toString().split("/");
		String type = split[split.length - 1];
		if (type.equals("soap")) {
			return (MessagesEmailClient) new MessageClientSoap(url, maxRetries, retryPeriod);
		} else if (type.equals("rest")) {
			return new MessageClientRest(url, maxRetries, retryPeriod);
		} else {
			throw new AssertionError("Unknown url: " + url + " - " + type);
		}
	}

	public static UsersEmailClient getUsersClient(String url, int maxRetries, int retryPeriod) {
		String[] split = url.toString().split("/");
		String type = split[split.length - 1];
		if (type.equals("soap")) {
			return (UsersEmailClient) new UserClientSoap(url, maxRetries, retryPeriod);
		} else if (type.equals("rest")) {
			return new UserClientRest(url, maxRetries, retryPeriod);
		} else {
			throw new AssertionError("Unknown url: " + url + " - " + type);
		}
	}

	public static MessageEmailClientSoap getMessagesClientSoap(String url, int maxRetries, int retryPeriod) {
		String[] split = url.toString().split("/");
		String type = split[split.length - 1];
		if (type.equals("soap")) {
			return new MessageClientSoap(url, maxRetries, retryPeriod);
		} else if (type.equals("rest")) {
			return (MessageEmailClientSoap) new MessageClientRest(url, maxRetries, retryPeriod);
		} else {
			throw new AssertionError("Unknown url: " + url + " - " + type);
		}
	}

	public static UsersEmailClientSoap getUsersClientSoap(String url, int maxRetries, int retryPeriod) {
		String[] split = url.toString().split("/");
		String type = split[split.length - 1];
		if (type.equals("soap")) {
			return new UserClientSoap(url, maxRetries, retryPeriod);
		} else if (type.equals("rest")) {
			return (UsersEmailClientSoap) new UserClientRest(url, maxRetries, retryPeriod);
		} else {
			throw new AssertionError("Unknown url: " + url + " - " + type);
		}
	}
}
