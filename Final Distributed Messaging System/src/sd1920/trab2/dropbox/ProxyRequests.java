package sd1920.trab2.dropbox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.pac4j.scribe.builder.api.DropboxApi20;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import com.google.gson.Gson;

import sd1920.trab2.api.Message;
import sd1920.trab2.api.User;
import sd1920.trab2.dropbox.replies.ListFolderReturn;
import sd1920.trab2.dropbox.replies.ListFolderReturn.FolderEntry;

public class ProxyRequests {

	protected static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
	protected static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";

	private static final String CREATE_FOLDER_V2_URL = "https://api.dropboxapi.com/2/files/create_folder_v2";
	private static final String CONTENT_DOWNLOAD_URL = "https://content.dropboxapi.com/2/files/download";
	private static final String CONTENT_DELETE_URL = "https://api.dropboxapi.com/2/files/delete_v2";
	private static final String CONTENT_UPLOAD_URL = "https://content.dropboxapi.com/2/files/upload";
	private static final String LIST_FOLDER_URL = "https://api.dropboxapi.com/2/files/list_folder";
	private static final String LIST_FOLDER_CONTINUE_URL = "https://api.dropboxapi.com/2/files/list_folder/continue";

	public final static int MAX_RETRIES = 2;
	public final static long RETRY_PERIOD = 5000;

	private OAuth20Service service;
	private OAuth2AccessToken accessToken;

	private Gson json;

	public ProxyRequests(String apiKey, String apiSecret, String accessTokenStr) {
		service = new ServiceBuilder(apiKey).apiSecret(apiSecret).build(DropboxApi20.INSTANCE);
		accessToken = new OAuth2AccessToken(accessTokenStr);
		json = new Gson();
	}

	public boolean createDirectory(String directoryName) {
		OAuthRequest createFolder = new OAuthRequest(Verb.POST, CREATE_FOLDER_V2_URL);
		createFolder.addHeader("Content-Type", JSON_CONTENT_TYPE);
		createFolder.setPayload(json.toJson(new ProxyArgs.CreateFolderV2Args(directoryName, false)));
		service.signRequest(accessToken, createFolder);

		Response r = null;

		short retries = 0;
		boolean success = false;

		while (!success && retries < MAX_RETRIES) {

			try {
				r = service.execute(createFolder);
			} catch (Exception e) {
				e.printStackTrace();
				retries++;
				threadSleepError();
			}

			if (r.getCode() == 200) {
				success = true;
			} else {

				if (r.getCode() == 429) {
					threadSleepRetry(r);
				} else {
					printError(r);
					retries++;
				}
			}
		}
		return success;
	}

	public User getUser(String path) {
		OAuthRequest getUser = new OAuthRequest(Verb.POST, CONTENT_DOWNLOAD_URL);
		getUser.addHeader("Content-Type", OCTET_STREAM_CONTENT_TYPE);

		String header1 = json.toJson(new ProxyArgs.ContentGetArgs(path));
		getUser.addHeader("Dropbox-API-Arg", header1);

		service.signRequest(accessToken, getUser);

		Response r = null;
		User reply = null;

		short retries = 0;

		while (retries < MAX_RETRIES) {

			try {
				r = service.execute(getUser);
				reply = json.fromJson(r.getBody(), User.class);
			} catch (Exception e) {
				e.printStackTrace();
				retries++;
				threadSleepError();
			}

			if (r.getCode() == 200) {
				return reply;
			} else {
				if (r.getCode() == 429) {
					threadSleepRetry(r);
				} else {
					printError(r);
					retries++;
				}
			}
		}
		return null;
	}

	public boolean delete(String directoryPath) {
		OAuthRequest deleteFile = new OAuthRequest(Verb.POST, CONTENT_DELETE_URL);

		deleteFile.addHeader("Content-Type", JSON_CONTENT_TYPE);
		deleteFile.setPayload(json.toJson(new ProxyArgs.ContentDelelteArgs(directoryPath)));
		service.signRequest(accessToken, deleteFile);

		Response r = null;

		short retries = 0;

		while (retries < MAX_RETRIES) {

			try {
				r = service.execute(deleteFile);
			} catch (Exception e) {
				e.printStackTrace();
				retries++;
				threadSleepError();
			}

			if (r.getCode() == 200) {
				return true;
			} else {
				if (r.getCode() == 429) {
					threadSleepRetry(r);
				} else {
					printError(r);
					retries++;
				}
			}

		}
		return false;
	}

	public boolean contentUpload(String directoryName, User user, String type) {

		OAuthRequest createFile = new OAuthRequest(Verb.POST, CONTENT_UPLOAD_URL);
		String header1 = json.toJson(new ProxyArgs.ContentUploadArgs(directoryName, type, false, false, false));
		createFile.addHeader("Dropbox-API-Arg", header1);
		createFile.addHeader("Content-Type", OCTET_STREAM_CONTENT_TYPE);

		createFile.setPayload(json.toJson(user));

		service.signRequest(accessToken, createFile);

		Response r = null;

		short retries = 0;
		while (retries < MAX_RETRIES) {

			try {
				r = service.execute(createFile);
			} catch (Exception e) {
				e.printStackTrace();
				retries++;
				threadSleepError();
			}

			if (r.getCode() == 200) {
				return true;
			} else {

				if (r.getCode() == 429) {
					threadSleepRetry(r);
				} else {
					printError(r);
					retries++;
				}
			}

		}
		return false;
	}

	public int messageUpload(String directoryName, Message msg) {

		OAuthRequest createFile = new OAuthRequest(Verb.POST, CONTENT_UPLOAD_URL);
		String header1 = json.toJson(new ProxyArgs.ContentUploadArgs(directoryName, "add", false, false, false));
		createFile.addHeader("Dropbox-API-Arg", header1);
		createFile.addHeader("Content-Type", OCTET_STREAM_CONTENT_TYPE);
		createFile.setPayload(json.toJson(msg));
		service.signRequest(accessToken, createFile);

		Response r = null;

		short retries = 0;
		while (retries < MAX_RETRIES) {

			try {
				r = service.execute(createFile);
			} catch (Exception e) {
				e.printStackTrace();
				retries++;
				threadSleepError();
			}

			if (r.getCode() == 200) {
				return r.getCode();
			} else {
				if (r.getCode() == 409) {
					return r.getCode();
				} else {
					if (r.getCode() == 429) {
						threadSleepRetry(r);
					} else {
						printError(r);
						retries++;
					}
				}
			}
		}
		return -1;
	}

	public Message getMessage(String path) {
		OAuthRequest getMessage = new OAuthRequest(Verb.POST, CONTENT_DOWNLOAD_URL);
		getMessage.addHeader("Content-Type", OCTET_STREAM_CONTENT_TYPE);

		String header1 = json.toJson(new ProxyArgs.ContentGetArgs(path));
		getMessage.addHeader("Dropbox-API-Arg", header1);

		service.signRequest(accessToken, getMessage);

		Response r = null;
		Message reply = null;

		short retries = 0;
		while (retries < MAX_RETRIES) {

			try {
				r = service.execute(getMessage);
				reply = json.fromJson(r.getBody(), Message.class);

			} catch (Exception e) {
				e.printStackTrace();
				retries++;
				threadSleepError();
			}

			if (r.getCode() == 200) {
				return reply;
			} else {
				if (r.getCode() == 429) {
					threadSleepRetry(r);
				} else {
					printError(r);
					return null;
				}
			}
		}
		return null;
	}

	public List<String> listDirectory(String directoryName) {
		List<String> directoryContents = new ArrayList<String>();

		OAuthRequest listDirectory = new OAuthRequest(Verb.POST, LIST_FOLDER_URL);
		listDirectory.addHeader("Content-Type", JSON_CONTENT_TYPE);
		listDirectory.setPayload(json.toJson(new ProxyArgs.ListFolderArgs(directoryName, false)));

		service.signRequest(accessToken, listDirectory);

		Response r = null;

		short retries = 0;
		while (retries < MAX_RETRIES) {

			try {
				while (true) {
					r = service.execute(listDirectory);

					if (r.getCode() != 200) {
						System.err.println("Failed to list directory '" + directoryName + "'. Status " + r.getCode()
								+ ": " + r.getMessage());
						System.err.println(r.getBody());
						return null;
					}

					ListFolderReturn reply = json.fromJson(r.getBody(), ListFolderReturn.class);

					for (FolderEntry e : reply.getEntries()) {
						directoryContents.add(e.toString());
					}

					if (reply.has_more()) {
						listDirectory = new OAuthRequest(Verb.POST, LIST_FOLDER_CONTINUE_URL);
						listDirectory.addHeader("Content-Type", JSON_CONTENT_TYPE);
						listDirectory.setPayload(json.toJson(new ProxyArgs.ListFolderContinueArgs(reply.getCursor())));
						service.signRequest(accessToken, listDirectory);
					} else {
						return directoryContents;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				retries++;
				threadSleepError();
			}
		}
		return directoryContents;
	}

	private void threadSleepError() {
		try {
			Thread.sleep(RETRY_PERIOD); // wait until attempting again.
		} catch (InterruptedException Ie) {
			// Nothing to be done here, if this happens we will just retry sooner.
		}
	}

	private void threadSleepRetry(Response r) {
		double time = Double.parseDouble(r.getHeader("Retry-After"));
		try {
			Thread.sleep((long) Math.ceil(time));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void printError(Response r) {
		System.err.println("HTTP Error Code: " + r.getCode() + ": " + r.getMessage());
		try {
			System.err.println(r.getBody());
		} catch (IOException e) {
			System.err.println("No body in the response");
		}
	}

}
