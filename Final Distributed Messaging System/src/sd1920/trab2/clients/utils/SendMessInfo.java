package sd1920.trab2.clients.utils;

import sd1920.trab2.api.Message;

/**
 * Auxiliary class to save the messages and the respective type (post or delete)
 * on the BlockingDeque
 *
 */
public class SendMessInfo {

	private Message m;
	private boolean toPost;

	/**
	 * 
	 * @param m message received
	 */
	public SendMessInfo(Message m) {
		this.m = m;
	}

	/**
	 * Receives true if it's a post message and false if it's a delete message
	 * 
	 * @param a boolean that represents the type of the message
	 */
	public void setBoolean(boolean a) {
		toPost = a;
	}

	/**
	 * Returns the message
	 * 
	 * @return the message
	 */
	public Message getMess() {
		return m;
	}

	/**
	 * Returns true if it's a post and false if it's a delete message
	 * 
	 * @return boolean true if it's a post message and false if it's a delete
	 *         message
	 */
	public boolean toPostMessage() {
		return toPost;
	}

}
