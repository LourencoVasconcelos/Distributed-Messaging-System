package sd1920.trab2.clients.soap;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab2.api.User;
import sd1920.trab2.api.soap.MessagesException;
import sd1920.trab2.api.soap.UserServiceSoap;
import sd1920.trab2.clients.utils.UsersEmailClientSoap;

public class UserClientSoap extends EmailClientSoap implements UsersEmailClientSoap {

	private static final String USERS_WSDL = "/users/?wsdl";

	UserServiceSoap userProxy;

	public UserClientSoap(String serverUrl, int maxRetries, int retryPeriod) {
		super(serverUrl, maxRetries, retryPeriod);
	}

	private UserServiceSoap getClient() throws MalformedURLException, WebServiceException {
		synchronized (this) {
			if (userProxy == null) {
				QName QNAME = new QName(UserServiceSoap.NAMESPACE, UserServiceSoap.NAME);
				Service service = Service.create(new URL(serverUri + USERS_WSDL), QNAME);
				userProxy = service.getPort(UserServiceSoap.class);
				// Set timeouts
				((BindingProvider) userProxy).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
						CONNECTION_TIMEOUT);
				((BindingProvider) userProxy).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
						REPLY_TIMEOUT);
			}
			return userProxy;
		}
	}

	@Override
	public User getUser(String name, String pwd) {
		User u = null;
		try {
			u = getClient().getUser(name, pwd);
		} catch (MalformedURLException | WebServiceException | MessagesException e) {
			e.printStackTrace();
			return null;
		}

		return u;
	}
}
