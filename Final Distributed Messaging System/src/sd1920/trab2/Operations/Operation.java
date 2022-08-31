package sd1920.trab2.Operations;

import com.google.gson.Gson;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.User;

public class Operation {
	static final Gson json = new Gson();
	static final byte OP_USER_POSTUSER = 1;
	static final byte OP_USER_UPDATEUSER = 2;
	static final byte OP_USER_DELETEUSER = 3;
	static final byte OP_MESSAGE_POSTMESSAGE = 4;
	static final byte OP_MESSAGE_DELETEMESSAGE = 5;
	static final byte OP_MESSAGE_REMOVE_FROM_INBOX_MESSAGE = 6;

	final byte code;
	final String[] params;

	public Operation() {
		this.code = 0;
		this.params = null;
	}

	Operation(byte code, String[] params) {
		this.code = code;
		this.params = params;
	}

	public byte getCode() {
		return code;
	}

	public String[] getParams() {
		return params;
	}

	public static final Operation createPostUserOp(User u) {
		return new Operation(OP_USER_POSTUSER, new String[] { json.toJson(u) });
	}

	public static final Operation updateUserOp(String user, String pwd, User u) {
		return new Operation(OP_USER_UPDATEUSER, new String[] { user, pwd, json.toJson(u) });
	}

	public static final Operation deleteUserOp(String user, String pwd) {
		return new Operation(OP_USER_DELETEUSER, new String[] { user, pwd });
	}

	public static final Operation createPostMessageOp(Message m) {
		return new Operation(OP_MESSAGE_POSTMESSAGE, new String[] { json.toJson(m) });
	}

	public static final Operation removeFromInboxOp(String user, long mid) {
		return new Operation(OP_MESSAGE_REMOVE_FROM_INBOX_MESSAGE, new String[] { user, "" + mid });
	}

	public static final Operation deleteMessageOp(long mid) {
		return new Operation(OP_MESSAGE_DELETEMESSAGE, new String[] { "" + mid });
	}

}