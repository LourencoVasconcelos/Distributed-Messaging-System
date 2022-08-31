package sd1920.trab2.clients.utils;

import javax.ws.rs.core.Response;

import sd1920.trab2.api.Message;

public interface MessagesEmailClient {

	Response createInbox(String user, String secret);

	Response deleteInbox(String user, String secret);

	Response forwardPostMessage(Message m, String secret);

	Response forwardDeleteMessage(Message m, String secret);

	int postMessageInReplic(Message m, String serverURI, String secret, long version);

	int removeMessageInReplic(String user, long mid, String serverURI, String secret, long version);

	int deleteMessageInReplic(long mid, String serverURI, String secret, long version);

	int deleteMessageFromOutInReplic(String id, String serverURI, String secret, long version);

	int receiveMessageFromOutInReplic(Message m, String serverURI, String secret, long version);
}
