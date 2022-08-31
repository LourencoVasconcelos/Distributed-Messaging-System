package sd1920.trab2.clients.utils;

public interface MessageEmailClientSoap {
	void createInbox(String user, String secret);

	void deleteInbox(String user, String secret);
}
