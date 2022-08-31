package sd1920.trab1.clients.utils;


import sd1920.trab1.api.User;

public class UserUtills {

	public static void printMessage(User u) {
		System.out.println("Name: " + u.getName());
		System.out.println("Display Name: " + u.getDisplayName());
		System.out.println("Domain: " + u.getDomain());
		System.out.println("pwd: " + u.getPwd());
	}

}
