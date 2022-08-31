package sd1920.trab2.clients.utils;

import sd1920.trab2.api.User;

public interface UsersEmailClientSoap {

	User getUser(String name, String pwd);
}
