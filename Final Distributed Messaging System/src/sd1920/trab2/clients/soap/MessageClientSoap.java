package sd1920.trab2.clients.soap;

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab2.api.soap.MessageServiceSoap;
import sd1920.trab2.clients.utils.MessageEmailClientSoap;

public class MessageClientSoap extends EmailClientSoap implements MessageEmailClientSoap {

	private static final String MESSAGES_WSDL = "/messages/?wsdl";

	MessageServiceSoap messageProxy;

	public MessageClientSoap(String serverUri, int maxRetries, int retryPeriod) {
		super(serverUri, maxRetries, retryPeriod);
	}

	@Override
	public void createInbox(String user, String secret) {

		try {
			getClient().createInbox(user, secret);
		} catch (MalformedURLException | WebServiceException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void deleteInbox(String user, String secret) {

		try {
			getClient().deleteInbox(user, secret);
		} catch (MalformedURLException | WebServiceException e) {
			e.printStackTrace();
		}
	}

	private MessageServiceSoap getClient() throws MalformedURLException, WebServiceException {
		synchronized (this) {
			if (messageProxy == null) {
				QName QNAME = new QName(MessageServiceSoap.NAMESPACE, MessageServiceSoap.NAME);
				Service service = Service.create(new URL(serverUri + MESSAGES_WSDL), QNAME);
				messageProxy = service.getPort(MessageServiceSoap.class);
				// Set timeouts
				((BindingProvider) messageProxy).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
						CONNECTION_TIMEOUT);
				((BindingProvider) messageProxy).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
						REPLY_TIMEOUT);
			}
			return messageProxy;
		}
	}
}
