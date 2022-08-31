package sd1920.trab2.clients.soap;

public class EmailClientSoap {

	public final static int CONNECTION_TIMEOUT = 1000;
	public final static int REPLY_TIMEOUT = 600;

	int maxRetries;
	int retryPeriod;
	String serverUri;

	public EmailClientSoap(String serverUri, int maxRetries, int retryPeriod) {
		this.serverUri = serverUri;
		this.maxRetries = maxRetries;
		this.retryPeriod = retryPeriod;
	}
}
