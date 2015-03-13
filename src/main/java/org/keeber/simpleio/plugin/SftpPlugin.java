package org.keeber.simpleio.plugin;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.keeber.simpleio.File;
import org.keeber.simpleio.File.Plugin;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

public class SftpPlugin extends Plugin {
	private static final Logger logger = Logger.getLogger(SftpPlugin.class.getName());
 
	public static Plugin create() {
		return new SftpPlugin();
	}

	@Override public File resolve(URI uri) throws IOException {
		URL url = new URL(Plugin.unescape(uri.toString()).replace("sftp://", "ftp://"));

		JSch ssh = new JSch();
		String host = url.getHost();
		String[] userinfo = uri.getUserInfo().split(":");
		String username = URLDecoder.decode(userinfo[0], "UTF-8");
		String password = (userinfo.length == 1) ? null : URLDecoder.decode(userinfo[1], "UTF-8");
		try {
			Session session = ssh.getSession(username, host, 22);

			Properties config = new Properties();
			config.put("StrictHostKeyChecking", "no");
			config.put("PreferredAuthentications", "publickey,keyboard-interactive,password");
			session.setConfig(config);
			session.setPassword(password);
			session.connect();

			Channel channel = session.openChannel("sftp");
			channel.connect();
			ChannelSftp client = (ChannelSftp) channel;

			return new SftpSIOFile(client, host, username, password, url.getPath());
		} catch (JSchException e) {
			throw new IOException(e);
		}
	}

	@Override public String getScheme() {
		return "sftp";
	}

	public class SftpSIOFile extends File {
		private ChannelSftp client;
		protected String path;
		protected String name;
		protected SftpATTRS stats;

		private String username;
		private String password;
		private String host;

		public SftpSIOFile(ChannelSftp client, String host, String username, String password, String path) throws IOException {
			this.client = client;
			this.path = path;
			this.host = host;
			this.username = username;
			this.password = password;
			init();
		}

		private void init() throws IOException {
			try {
				this.stats = client.lstat(path);
			} catch (SftpException e) {
				this.stats = null;
			}
		}

		private void checkConnect() throws IOException {
			if (!client.isConnected()) {
				try {
					client.connect();
				} catch (JSchException e) {
					throw new IOException(e);
				}
			}
		}

		public void setLastModified(long time) throws IOException {
			if (stats != null) {
				stats.setACMODTIME((int) time, (int) time);
				stats.setPERMISSIONS(Integer.parseInt("777", 8));
				try {
					client.setStat(path, stats);
				} catch (SftpException e) {
					throw new IOException(e);
				}
			}
		}

		public long length() throws IOException {
			return stats == null ? 0 : stats.getSize();
		}

		public boolean isDirectory() {
			return stats == null ? path.endsWith("/") : stats.isDir();
		}

		public boolean isFile() {
			return !isDirectory();
		}

		public boolean exists() throws IOException {
			return stats != null || (path.length() == 0 || "/".equals(path));
		}

		public boolean delete() throws IOException {
			if (stats == null) {
				return false;
			}
			this.checkConnect();
			try {
				if (isDirectory()) {
					client.rmdir(path);
				} else {
					client.rm(path);
				}
			} catch (SftpException e) {
				throw new IOException(e);
			}
			init();
			return stats == null;
		}

		public boolean mkdir() throws IOException {
			this.checkConnect();
			try {
				client.mkdir(path);
				client.chmod(Integer.parseInt("777", 8), path);
			} catch (SftpException e) {
				throw new IOException(e);
			}
			init();
			return true;
		}

		public boolean mkdirs() throws IOException {
			String[] dirs = path.split("/");
			String dirString = "";
			for (int d = 0; d < dirs.length; d++) {
				dirString = dirString + dirs[d] + "/";
				try {
					client.mkdir(dirString);
					client.chmod(Integer.parseInt("777", 8), dirString);
				} catch (SftpException e) {
					// throw new IOException(e);
				}
				setLastModified(System.currentTimeMillis());
			}
			init();
			return stats != null;
		}

		public File parent() throws IOException {
			return new SftpSIOFile(client, host, username, password, Plugin.getParentFromPath(path));
		}

		@Override public List<File> list(Filter filter, Comparator<File> sorter) throws IOException {
			ArrayList<File> list = IOList(filter, this.path, 0);
			Collections.sort(list, sorter);
			return list;
		}

		public List<File> list(Filter filter) throws IOException {
			return list(filter, File.comparators.DEFAULT);
		}

		private ArrayList<File> IOList(Filter filter, String root, int depth) throws IOException {
			ArrayList<File> retList = new ArrayList<File>();
			if (stats == null || !stats.isDir()) {
				return retList;
			}
			this.checkConnect();
			try {
				// client.cd(root);

				ChannelSftp.LsEntry f;
				for (Object o : client.ls(root)) {
					f = (ChannelSftp.LsEntry) o;

					SftpSIOFile ff = new SftpSIOFile(client, host, username, password, Plugin.cleanPath(root + "/" + f.getFilename()));
					if (ff.getPath().endsWith(".") || ff.getName().endsWith(".")) {
						continue;
					}
					if (filter.isListed(ff)) {
						retList.add(ff);
					}
					if (ff.isDirectory() && filter.isFollowed(ff, depth)) {
						retList.addAll(IOList(filter, ff.path, depth + 1));
					}
				}
			} catch (SftpException e) {
				if (!e.getMessage().startsWith("3")) {
					throw new IOException(e);
				}
			}
			return retList;
		}

		public String getName() {
			return Plugin.getNameFromPath(path);
		}

		public String getBaseName() {
			return Plugin.getBaseName(getName());
		}

		public String getExtension() {
			return Plugin.getExtension(getName());
		}

		public String getPath() {
			return Plugin.cleanPath(host + "/" + path);
		}

		@SuppressWarnings("unchecked") @Override public <T> T open(Class<T> streamType) throws IOException {
			this.checkConnect();
			try {
				client.cd(Plugin.getParentFromPath(path));
				if (streamType == File.READ) {
					if (stats == null || isDirectory()) {
						return null;
					}
					return (T) client.get(getName());
				}
				if (streamType == File.WRITE) {
					return (T) new CloseNotifyOutputStream(client.put(getName()));
				}
			} catch (SftpException e) {
				throw new IOException(e);
			}
			return null;

		}

		public class CloseNotifyOutputStream extends FilterOutputStream {

			protected CloseNotifyOutputStream(OutputStream is) {
				super(is);
			}

			@Override public void close() throws IOException {
				super.close();
				init();
			}

		}

		public boolean rename(File file) throws IOException {
			if (stats == null) {
				return false;
			}
			if (!SftpSIOFile.class.equals(file.getClass())) {
				throw new IOException("Cross scheme rename not implemented (or allowed).");
			}
			this.checkConnect();
			String oPath = ((SftpSIOFile) file).path;
			try {
				client.rename(path, oPath);
			} catch (SftpException e) {
				throw new IOException(e);
			}
			this.path = oPath;
			init();
			return true;
		}

		public void dispose() {
			try {
				client.getSession().disconnect();
			} catch (JSchException e) {
				logger.log(Level.SEVERE, null, e);
			}
			client.disconnect();

			logger.log(Level.CONFIG, "File[{0}] Logged out.", new Object[] { getScheme() });
		}

		public boolean isVisible() {
			return (getName() == null) ? true : !(getName().startsWith("."));
		}

		public File create(String path) throws IOException {
			return new SftpSIOFile(client, host, username, password, Plugin.cleanPath(this.path + path));
		}

		public String stringMarshal() {
			return "ftp://" + username + ":" + password + "@" + host + path;
		}

		@Override public long getLastModified() throws IOException {
			return (stats == null) ? 0 : stats.getMTime();
		}

		@Override public URI getURI() {
			try {
				return new URI("sftp", username + ":" + password, host, 22, path, null, null);
			} catch (URISyntaxException e) {
				e.printStackTrace();
				return null;
			}
		}
	}

}
