package sd1920.trab2.dropbox;

public abstract class ProxyArgs {

	public ProxyArgs() {

	}

	public static class ContentDelelteArgs extends ProxyArgs {

		final String path;

		public ContentDelelteArgs(String path) {
			super();
			this.path = path;
		}

	}

	public static class ContentGetArgs extends ProxyArgs {

		final String path;

		public ContentGetArgs(String path) {
			super();

			this.path = path;
		}
	}

	public static class ListFolderContinueArgs extends ProxyArgs {

		final String cursor;

		public ListFolderContinueArgs(String cursor) {
			this.cursor = cursor;
		}
	}

	public static class CreateFolderV2Args extends ProxyArgs {

		final String path;
		final boolean autorename;

		public CreateFolderV2Args(String path, boolean autorename) {
			super();

			this.path = path;
			this.autorename = autorename;
		}
	}

	public static class ContentUploadArgs extends ProxyArgs {

		final String path;
		final String mode;
		final boolean autorename;
		final boolean mute;
		final boolean strict_conflict;

		public ContentUploadArgs(String path, String mode, boolean autorename, boolean mute, boolean strict_conflict) {
			super();

			this.path = path;
			this.mode = mode;
			this.autorename = autorename;
			this.mute = mute;
			this.strict_conflict = strict_conflict;
		}
	}

	public static class ListFolderArgs extends ProxyArgs {

		final String path;
		final boolean recursive, include_media_info, include_deleted, include_has_explicit_shared_members,
				include_mounted_folders;

		public ListFolderArgs(String path, boolean recursive) {
			super();

			this.path = path;
			this.recursive = recursive;
			this.include_media_info = false;
			this.include_deleted = false;
			this.include_mounted_folders = false;
			this.include_has_explicit_shared_members = false;
		}
	}

}
