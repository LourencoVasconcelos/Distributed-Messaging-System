package sd1920.trab2.api.soap;

import java.util.List;

import javax.jws.WebMethod;
import javax.jws.WebService;

import sd1920.trab2.api.Message;

@WebService(serviceName=MessageServiceSoap.NAME, 
	targetNamespace=MessageServiceSoap.NAMESPACE, 
	endpointInterface=MessageServiceSoap.INTERFACE)
public interface MessageServiceSoap {
	
	static final String NAME = "messages";
	static final String NAMESPACE = "http://sd2019";
	static final String INTERFACE = "sd1920.trab2.api.soap.MessageServiceSoap";
	static final String MESSAGES_WSDL = "/messages/?wsdl";
	
	/**
	 * Posts a new message to the server, associating it to the inbox of every individual destination.
	 * An outgoing message should be modified before delivering it, by assigning an ID, and
	 * by changing the sender to be in the format "display name <name@domain>", with display name the
	 * display name associated with a user.
	 * NOTE: there might be some destinations that are not from the local domain (see grading for 
	 * how addressing this feature is valued).
	 * 
	 * @param msg the message object to be posted to the server
	 * @param pwd password of the user sending the message
	 * @return the unique numerical identifier for the posted message. 
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	public long postMessage(String pwd, Message msg) throws MessagesException;
	
	/**
	 * Obtains the message identified by mid of user user
	 * @param user user name for the operation
	 * @param mid the identifier of the message
	 * @param pwd password of the user
	 * @return the message if it exists.
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	public Message getMessage(String user, String pwd, long mid) throws MessagesException;
		
	/**
	 * Returns a list of all ids of messages stored in the server for a given user
	 * @param user the username of the user whose messages should be returned
	 * @param pwd password of the user
	 * @return a list of ids potentially empty;
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	public List<Long> getMessages(String user, String pwd) throws MessagesException;

	/**
	 * Removes a message identified by mid from the inbox of user identified by user.
	 * @param user the username of the inbox that is manipulated by this method
	 * @param mid the identifier of the message to be deleted
	 * @param pwd password of the user
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	void removeFromUserInbox(String user, String pwd, long mid) throws MessagesException;

	/**
	 * Removes the message identified by mid from the inboxes of any server that holds the message.
	 * The deletion can be executed asynchronously and does not generate any error message if the
	 * message does not exist.
	 * 
	 * @param user the username of the sender of the message to be deleted
	 * @param mid the identifier of the message to be deleted
	 * @param pwd password of the user that sent the message
	 * @throws MessagesException in case of error.
	 */
	@WebMethod
	void deleteMessage(String user, String pwd, long mid) throws MessagesException;
	
	/**
	 * Creates an Inbox for the user in the server
	 * 
	 * @param user the username of the new user in the server
	 */
	@WebMethod
	void createInbox(String user, String secret);
	
	/**
	 * Deletes the Inbox of the user in the server
	 * 
	 * @param user the username of the Inbox to be deleted
	 */
	@WebMethod
	void deleteInbox(String user, String secret);

	/**
	 * Removes the message identified by id from the inboxes on this server.
	 * 
	 * @param id the identifier of the message to be deleted
	 */
	@WebMethod
	void deleteMessageFromOut(String id, String secret);

	/**
	 * Receives a message from another server and posts it on all destinations
	 * that are in this server.
	 * Returns an array of users' username that don't exist in this server
	 * 
	 * @param message the message object to be posted in this server
	 * @return 200 a list of users potentially empty;
	 */
	@WebMethod
	String[] receiveMessageFromOut(Message message, String secret);
	
}