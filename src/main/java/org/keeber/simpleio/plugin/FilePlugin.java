package org.keeber.simpleio.plugin;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.keeber.simpleio.File;

public class FilePlugin extends File.Plugin {
	private static final java.io.File home = new java.io.File(System.getProperty("user.home"));
	public static final String FILE_SCHEME = "file";

	@Override protected File resolve(URI uri) throws IOException {
		String path = cleanPath(uri.getPath().startsWith("~") ? uri.getPath().replaceFirst("^~", home.toURI().getPath()) : uri.getPath());
		return new SIOFile(new java.io.File(path), path.endsWith("/"));
	}
 
	@Override public String getScheme() {
		return FILE_SCHEME;
	}

	public static class SIOFile extends File {
		private java.io.File ref;
		private boolean isDir = false;

		protected SIOFile(java.io.File ref, boolean isDir) throws IOException {
			this.ref = ref.getCanonicalFile();
			this.isDir = isDir;
		}

		@SuppressWarnings("unchecked") @Override public <T> T open(Class<T> streamType) throws IOException {
			if (streamType.equals(File.READ)) {
				return (T) new FileInputStream(ref);
			}
			if (streamType.equals(File.WRITE)) {
				return (T) new FileOutputStream(ref);
			}
			return null;
		}

		@Override public long getLastModified() throws IOException {
			return ref.lastModified();
		}

		@Override public void setLastModified(long time) throws IOException {
			ref.setLastModified(time);
		}

		@Override public long length() throws IOException {
			return ref.length();
		}

		@Override public boolean isDirectory() {
			return ref.exists() ? ref.isDirectory() : isDir;
		}

		@Override public boolean isFile() {
			return !isDirectory();
		}

		@Override public boolean isVisible() {
			return !ref.getName().startsWith(".") && !ref.isHidden();
		}

		@Override public boolean exists() throws IOException {
			return ref.exists();
		}

		@Override public File parent() throws IOException {
			return new SIOFile(ref.getParentFile(), true);
		}

		@Override public List<File> list(Filter filter, Comparator<File> default1) throws IOException {
			List<File> list = IOList(filter, ref, 0);
			Collections.sort(list, default1);
			return list;
		}

		@Override public List<File> list(Filter filter) throws IOException {
			return this.list(filter, File.comparators.DEFAULT);
		}

		public ArrayList<File> IOList(Filter filter, java.io.File root, int depth) throws IOException {
			ArrayList<File> retList = new ArrayList<File>();
			java.io.File[] list;
			if (root.canRead()) {
				list = root.listFiles();
				if (list != null) {
					for (java.io.File f : list) {
						if (filter.isListed(new SIOFile(f, f.isDirectory()))) {
							retList.add(new SIOFile(f, f.isDirectory()));
						}
						if (f.isDirectory() && filter.isFollowed(new SIOFile(f, f.isDirectory()), depth)) {
							retList.addAll(IOList(filter, f, depth + 1));
						}
					}
				}
			}
			return retList;
		}

		@Override public String getName() {
			return ref.getName();
		}

		@Override public String getBaseName() {
			return Plugin.getBaseName(getName());
		}

		@Override public String getExtension() {
			return Plugin.getExtension(getName());
		}

		@Override public String getPath() {
			return Plugin.cleanPath(ref.toURI().normalize().getPath() + (isDirectory() ? "/" : ""));
		}

		@Override public URI getURI() {
			return Plugin.normalize(ref.toURI(), isDirectory());
		}

		@Override public boolean delete() throws IOException {
			return ref.delete();
		}

		@Override public boolean mkdir() throws IOException {
			return ref.mkdir();
		}

		@Override public boolean mkdirs() throws IOException {
			return ref.mkdirs();
		}

		@Override public boolean rename(File file) throws IOException {
			if (!SIOFile.class.equals(file.getClass())) {
				throw new IOException("Cross scheme rename not implemented (or allowed).");
			}
			return ref.renameTo(new java.io.File(file.getURI()));
		}

		@Override public File create(String path) throws IOException {
			return File.resolve(URI.create(escape(getPath() + path)));
		}

		@Override public void dispose() {
			// Not required
		}

	}

}
