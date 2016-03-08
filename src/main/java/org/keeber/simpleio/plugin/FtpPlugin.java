package org.keeber.simpleio.plugin;

import java.io.BufferedReader;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileEntryParser;
import org.apache.commons.net.ftp.parser.FTPFileEntryParserFactory;
import org.apache.commons.net.ftp.parser.ParserInitializationException;
import org.keeber.simpleio.File;
import org.keeber.simpleio.File.Plugin;

public class FtpPlugin extends Plugin {
  private static final Logger logger = Logger.getLogger(FtpPlugin.class.getName());

  public static Plugin create() {
    return new FtpPlugin();
  }

  @Override
  public File resolve(URI uri) throws IOException {
    try {
      URL url = uri.toURL();
      FTPClient client = new FTPClient();

      String host = url.getHost();

      int port = (url.getPort() < 1) ? url.getDefaultPort() : url.getPort();
      client.connect(InetAddress.getByName(url.getHost()), port);
      // // Login
      String[] userinfo = url.getUserInfo().split(":");
      String username = URLDecoder.decode(userinfo[0], "UTF-8");
      String password = (userinfo.length == 1) ? null : URLDecoder.decode(userinfo[1], "UTF-8");

      client.login(username, password);
      client.setFileType(FTP.BINARY_FILE_TYPE);
      client.setListHiddenFiles(true);
      // // Passive modes
      client.enterLocalPassiveMode();
      // // Pete's Server
      if (client.getSystemType().matches(".*MACOS.*")) {
        client.setParserFactory(new RumpusFileEntryParserFactory());
      }
      // ///////////////////////////////
      return new FtpSIOFile(null, client, url.getFile(), host, port, username, password, url.getPath().endsWith("/"));
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public String getScheme() {
    return "ftp";
  }

  public class FtpSIOFile extends File {
    private FTPFile ref;
    private FTPClient client;
    protected String path;
    private String host;
    private String username;
    private String password;
    private int port;
    private boolean exists = false;
    private boolean isDir;

    public FtpSIOFile(FTPFile ref, FTPClient client, String path, String host, int port, String username, String password, boolean isDir) throws IOException {
      this.ref = ref;
      this.client = client;
      this.path = path;
      this.host = host;
      this.port = port;
      this.username = username;
      this.password = password;
      this.isDir = isDir;
      if (ref == null) {
        resolveRef();
      } else {
        this.exists = true;
      }

    }

    private void resolveRef() throws IOException {
      String pPath = Plugin.getParentFromPath(path.replaceFirst("/$", ""));
      if (path == null || pPath == null || pPath.equals(path) || pPath.length() == 0) {
        this.ref = new FTPFile();
        ref.setName("/");
        ref.setSize(0);
        ref.setType(FTPFile.DIRECTORY_TYPE);
        ref.setTimestamp(Calendar.getInstance());
        exists = true;// /probably

      } else {
        this.ref = getFileFromList(pPath, Plugin.getNameFromPath(path.replaceFirst("/$", "")));
        if (this.ref == null) {
          this.nullRef();
        }
      }
    }

    private void nullRef() {
      this.ref = new FTPFile();
      ref.setName(Plugin.getNameFromPath(path));
      ref.setSize(0);
      ref.setType((path.endsWith("/")) ? FTPFile.DIRECTORY_TYPE : FTPFile.DIRECTORY_TYPE);
      ref.setTimestamp(Calendar.getInstance());
      this.exists = false;
    }

    private FTPFile getFileFromList(String dir, String name) throws IOException {
      this.checkConnect();
      for (FTPFile f : client.listFiles(dir)) {

        if (f.getName().equals(name)) {

          this.exists = true;
          return f;
        }
      }
      return null;
    }

    private void checkConnect() throws IOException {
      if (!client.isConnected()) {
        client.connect(host);
        client.login(username, password);
      }
    }

    public void setLastModified(long time) throws IOException {
      if (ref == null) {
        return;
      }
      Calendar c = new GregorianCalendar();
      c.setTimeInMillis(time);
      ref.setTimestamp((Calendar) c.clone());
    }

    public long length() throws IOException {
      return (ref == null) ? 0 : ref.getSize();
    }

    public boolean isDirectory() {
      return (!exists) ? isDir : ref.isDirectory();
    }

    public boolean isFile() {
      return (!exists) ? false : ref.isFile();
    }

    public boolean exists() throws IOException {
      return (exists);
    }

    public boolean delete() throws IOException {
      if (ref == null) {
        return false;
      }
      this.checkConnect();
      if (ref.isDirectory()) {
        return client.removeDirectory(path);
      } else {
        return client.deleteFile(path);
      }
    }

    public boolean mkdir() throws IOException {
      this.checkConnect();
      client.changeWorkingDirectory(Plugin.getParentFromPath(path));
      client.mkd(Plugin.getNameFromPath(path));
      this.resolveRef();
      return true;
    }

    public boolean mkdirs() throws IOException {
      String[] dirs = path.split("/");
      String dirString = "";
      for (int d = 0; d < dirs.length; d++) {
        dirString = dirString + dirs[d] + "/";
        if (!client.changeWorkingDirectory(dirString)) {
          if (!client.makeDirectory(dirString)) {
            return false;
          } else {
            client.site("chmod 777 " + dirString);
            if (!client.changeWorkingDirectory(dirString)) {
              return false;
            }
          }
        }
      }
      this.resolveRef();
      return true;
    }

    public File parent() throws IOException {
      String r = Plugin.getParentFromPath(path);
      return new FtpSIOFile(null, client, r, host, port, username, password, true);
    }

    @Override
    public List<File> list(GrabFilter grab, MoveFilter move, Comparator<File> sorter) throws IOException {
      ArrayList<File> list = IOList(grab, move, this.path, 0);
      Collections.sort(list, sorter);
      return list;
    }


    private ArrayList<File> IOList(GrabFilter grab, MoveFilter move, String root, int depth) throws IOException {
      ArrayList<File> retList = new ArrayList<File>();
      this.checkConnect();
      for (FTPFile f : client.listFiles(root)) {
        FtpSIOFile ff = new FtpSIOFile(f, client, Plugin.cleanPath(root + "/" + f.getName()), host, port, username, password, f.isDirectory());
        if (ff.getPath().endsWith(".") || ff.getName().endsWith(".")) {
          continue;
        }
        if (grab.shouldGrab(ff)) {
          retList.add(ff);
        }
        if (f.isDirectory() && move.shouldMove(ff, depth)) {
          retList.addAll(IOList(grab, move, ff.path, depth + 1));
        }
      }
      return retList;
    }

    public String getName() {
      return Plugin.getNameFromPath(path);
    }

    public String getBaseName() {
      return Plugin.getBaseName(ref.getName());
    }

    public String getExtension() {
      return Plugin.getExtension(ref.getName());
    }

    public String getPath() {
      return Plugin.cleanPath(host + "/" + path + (isDirectory() ? "/" : ""));
    }

    protected InputStream read() throws IOException {
      this.checkConnect();
      return client.retrieveFileStream(path);
    }

    protected OutputStream write() throws IOException {
      this.checkConnect();
      return new CloseNotifyOutputStream(client.storeFileStream(path));
    }

    public class CloseNotifyOutputStream extends FilterOutputStream {

      protected CloseNotifyOutputStream(OutputStream is) {
        super(is);
      }

      @Override
      public void close() throws IOException {
        super.close();
        client.completePendingCommand();
        resolveRef();
      }

    }

    public boolean rename(File file) throws IOException {
      if (ref == null) {
        return false;
      }
      if (!FtpSIOFile.class.equals(file.getClass())) {
        throw new IOException("Cross scheme rename not implemented (or allowed).");
      }
      this.checkConnect();
      String oPath = ((FtpSIOFile) file).path;
      client.rename(path, oPath);
      this.path = oPath;
      resolveRef();
      return true;
    }

    public void dispose() {
      try {
        client.logout();
        client.disconnect();
      } catch (IOException ex) {
        logger.log(Level.SEVERE, null, ex);
      }
    }

    public boolean isVisible() {
      return (getName() == null) ? true : !(getName().startsWith("."));
    }

    public File create(String path) throws IOException {
      return new FtpSIOFile(null, client, Plugin.cleanPath(this.path + path), host, port, username, password, path.endsWith("/"));
    }

    public String stringMarshal() {
      return "ftp://" + username + ":" + password + "@" + host + path;
    }

    @Override
    public long getLastModified() throws IOException {
      return (ref == null) ? 0 : ref.getTimestamp().getTimeInMillis();
    }

    @Override
    public URI getURI() {
      try {
        return new URI("ftp", username + ":" + password, host, port, Plugin.cleanPath(path + (isDirectory() ? "/" : "")), null, null);
      } catch (URISyntaxException e) {
        e.printStackTrace();
        return null;
      }
    }
  }

  public class RumpusFileEntryParserFactory implements FTPFileEntryParserFactory {
    private HashMap<String, Integer> mMap = new HashMap<String, Integer>();

    public RumpusFileEntryParserFactory() {
      mMap.put("jan", 0);
      mMap.put("feb", 1);
      mMap.put("mar", 2);
      mMap.put("apr", 3);
      mMap.put("may", 4);
      mMap.put("jun", 5);
      mMap.put("jul", 6);
      mMap.put("aug", 7);
      mMap.put("sep", 8);
      mMap.put("oct", 9);
      mMap.put("nov", 10);
      mMap.put("dec", 11);
    }

    public FTPFileEntryParser createFileEntryParser(String key) throws ParserInitializationException {

      return new Parser();

    }

    public FTPFileEntryParser createFileEntryParser(FTPClientConfig config) throws ParserInitializationException {
      return new Parser();
    }

    public class Parser implements FTPFileEntryParser {

      public FTPFile parseFTPEntry(String listEntry) {
        FTPFile f = new FTPFile();
        f.setRawListing(listEntry);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(0);
        String[] preparsed = listEntry.split("\\s+");
        String[] parsed;
        // ///////// TEMP
        if (preparsed[1].equals("folder")) {
          f.setType(FTPFile.DIRECTORY_TYPE);
          parsed = new String[preparsed.length + 1];
          parsed[0] = preparsed[0];
          parsed[1] = "folder";
          parsed[2] = "0";
          parsed[3] = "0";
          for (int i = 4; i < parsed.length; i++) {
            parsed[i] = preparsed[i - 1];
          }
        } else {
          f.setType(FTPFile.FILE_TYPE);
          parsed = preparsed;
        }
        // ///DATE MODIFIED
        c.set((parsed[6].matches(".*:.*")) ? Calendar.getInstance().get(Calendar.YEAR) : Integer.parseInt(parsed[6]), mMap.get(parsed[4].toLowerCase()), Integer.parseInt(parsed[5]),
            (parsed[6].matches(".*:.*")) ? Integer.parseInt(parsed[6].split(":")[0]) : 0, (parsed[6].matches(".*:.*")) ? Integer.parseInt(parsed[6].split(":")[1]) : 0);

        f.setTimestamp(c);
        // ///SIZE
        f.setSize(Long.parseLong(parsed[3]));
        // ///NAME
        if (parsed.length > 8) {
          f.setName(parsed[7]);
          for (int i = 8; i < parsed.length; i++) {
            f.setName(f.getName() + " " + parsed[i]);
          }
        } else {
          f.setName(parsed[7]);
        }
        return f;
      }

      public String readNextEntry(BufferedReader reader) throws IOException {
        return reader.readLine();
      }

      public List<String> preParse(List<String> original) {
        return original;
      }

    }
  }

}
