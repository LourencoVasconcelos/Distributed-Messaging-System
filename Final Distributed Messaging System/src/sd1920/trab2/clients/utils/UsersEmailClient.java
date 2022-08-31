package sd1920.trab2.clients.utils;

import sd1920.trab2.api.User;

public interface UsersEmailClient {

	User getUser(String name, String pwd);

	User getUserForReplic(String name, String pwd, long version, String serverURI);

	int postUserInReplic(User user, String serverURI, String secret, long version);

	int updateInReplic(String name, String pwd, User user, String serverURI, String secret, long version);

	int deleteInReplic(String name, String pwd, String serverURI, String secret, long version);
}
