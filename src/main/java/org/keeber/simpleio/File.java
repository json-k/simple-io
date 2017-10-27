package org.keeber.simpleio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.keeber.simpleio.plugin.FilePlugin;

/**
 * 
 * @author Jason
 *
 */
public abstract class File implements Comparable<File> {

  /**
   * <p>
   * Resolves the path to a {@link File} using the appropriate scheme / plugin.
   * 
   * <p>
   * This is the entry point to file creation.
   * 
   * <p>
   * 
   * <pre>
   * <code>
   * File file=File.resolve("/path/to/some/file.txt");
   * </pre>
   * 
   * </code>
   * 
   * @param uristring
   * @return
   * @throws IOException
   */
  public static File resolve(String uristring) throws IOException {
    return in.stance.resolve(URI.create(Plugin.escape(uristring)));
  }

  /**
   * <p>
   * Resolves the path to a {@link File} using the appropriate scheme / plugin.
   * 
   * <p>
   * This is the entry point to file creation.
   * 
   * <p>
   * 
   * <pre>
   * <code>
   * File file=File.resolve("/path/to/some/file.txt");
   * </pre>
   * 
   * </code>
   * 
   * @param uri
   * @return
   * @throws IOException
   */
  public static File resolve(URI uri) throws IOException {
    return in.stance.resolve(uri);
  }

  /**
   * <p>
   * Add a {@link #Plugin} (the 'file' plugin is loaded by default) - plugins are store in a map
   * using the scheme as the key (can be overwritten).
   * 
   * <p>
   * Plugins can also be loaded via a ServiceLoader.
   * 
   * @param plugin
   */
  public static void addPlugin(Plugin plugin) {
    in.stance.addPlugin(plugin);
  }

  private static class in {
    private static Core stance = new Core();
  }

  /**
   * A 'floating singleton' - we can't make a singleton of the parent class (because it's abstract).
   * So we create a singleton here that can be access via static delegate methods of the parent
   * class.
   * 
   * @author Jason
   *
   */
  private static class Core {
    private Map<String, Plugin> plugins = new HashMap<String, Plugin>();

    private Core() {
      ServiceLoader<Plugin> loader = ServiceLoader.load(Plugin.class);
      plugins.put(FilePlugin.FILE_SCHEME, new FilePlugin());
      for (Plugin plugin : loader) {
        plugins.put(plugin.getScheme(), plugin);
      }
    }

    private void addPlugin(File.Plugin plugin) {
      plugins.put(plugin.getScheme(), plugin);
    }

    public File resolve(URI uri) throws IOException {
      if (uri == null) {
        throw new IOException("NULL URI (not allowed).");
      }
      String scheme = uri.getScheme();
      if (scheme == null) {
        return plugins.get(FilePlugin.FILE_SCHEME).resolve(uri);
      } else if (plugins.containsKey(scheme)) {
        return plugins.get(scheme).resolve(uri);
      }
      throw new IOException("Scheme not found for: " + uri.toString());
    }
  }

  /**
   * The abstract Plugin class - extend to provide a means of resolving a given scheme.
   * 
   * @author Jason
   * 
   */
  public static abstract class Plugin {

    /**
     * Resolve the provided string using the scheme for this plugin.
     * 
     * @param uri
     * @return {@link #File}
     * @throws IOException
     */
    protected abstract File resolve(URI uri) throws IOException;

    /**
     * <p>
     * Provides the URI scheme that can be resolved using this plugin.
     * <p>
     * A return of "ftp" suggests this plugin can handle URIs that have the scheme "ftp://..."
     * 
     * @return the scheme
     */
    public abstract String getScheme();

    /*
     * General utility methods to encourage consistency in plugins.
     */
    /**
     * Return the given string without the content after it's last [.] (period).
     * 
     * @param name
     * @return
     */
    protected static String getBaseName(String name) {
      int idx = name.lastIndexOf('.');
      return (idx == -1) ? name : name.substring(0, idx);
    }

    /**
     * Return the content of the given string after it's last [.] (period).
     * 
     * @param name
     * @return
     */
    protected static String getExtension(String name) {
      int idx = name.lastIndexOf('.');
      return (idx == -1) ? "" : name.substring(idx + 1);
    }

    /**
     * Remove double slashed in path.
     * 
     * @param path
     * @return
     */
    protected static String cleanPath(String path) {
      return path.replaceAll("/+", "/");
    }

    /**
     * Return only the name from/the/given/path.
     * 
     * @param path
     * @return
     */
    protected static String getNameFromPath(String path) {
      return path.substring(path.lastIndexOf("/") + 1);
    }

    /**
     * Return the parent of the given path (the content before the last meaningful '/' (slash)).
     * 
     * @param path
     * @return
     */
    protected static String getParentFromPath(String path) {
      if (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      int idx = path.lastIndexOf("/");
      return idx >= 0 ? path.substring(0, idx + 1) : path;
    }

    protected static String escape(String path) {
      return path.replaceAll(" ", "%20");
    }

    protected static String unescape(String path) {
      return path.replaceAll("%20", " ");
    }

    /**
     * 
     * @param uri
     * @return
     */
    protected static URI normalize(URI uri, boolean isDir) {
      if (uri.getPath().endsWith("/")) {
        return uri.normalize();
      } else {
        return URI.create(uri.normalize() + (isDir ? "/" : ""));
      }

    }
  }
  /**
   * A functional interface for determining if a file should be included in a list operation.
   * 
   * @author Jason
   *
   */
  @FunctionalInterface
  public static interface GrabFilter {

    public boolean shouldGrab(File f) throws IOException;

  }
  /**
   * A functional interface for derermining if a folder should be followed during a list operation.
   * 
   * @author Jason
   *
   */
  @FunctionalInterface
  public static interface MoveFilter {

    public boolean shouldMove(File f, int depth) throws IOException;

  }

  /**
   * A class containing standard filters.
   * 
   * @author Jason
   *
   */
  public static class filters {

    /**
     * A grab filter that takes everything.
     */
    public static GrabFilter EVERYTHING = (File f) -> {
      return true;
    };

    /**
     * A MOVE filter that follows every directory.
     */
    public static MoveFilter ALL_DIRECTORIES = (File f, int d) -> {
      return true;
    };

    /**
     * A GRAB filter that picks up visible files.
     */
    public static GrabFilter ALL_VISIBLE = (File f) -> {
      return f.isVisible();
    };

    /**
     * A GRAB filter that picks up visible files.
     */
    public static GrabFilter VISIBLE_FILES = (File f) -> {
      return f.isFile() && f.isVisible();
    };

    /**
     * A MOVE filter that follows visible directories.
     */
    public static MoveFilter VISIBLE_DIRECTORIES = (File f, int d) -> {
      return f.isVisible();
    };

    /**
     * A MOVE filter that ironically doesn't (the current directory).
     */
    public static MoveFilter ONLY_THIS_DIRECTORY = (File f, int d) -> {
      return false;
    };

  }

  /**
   * Comparators class contains standard comparators and a factory method for creating other types.
   * 
   * @author Jason
   * 
   */
  public static class comparators {
    public enum Order {
      ASCENDING(1), DECENDING(-1);

      int n;

      Order(int n) {
        this.n = n;
      }

      protected int getN() {
        return n;
      }
    }

    public enum By {
      NAME, MODIFIED, DEPTH;
    }

    /**
     * The default comparator (by Name, Ascending).
     */
    public static final Comparator<File> DEFAULT = new Sorter(By.NAME, Order.ASCENDING);

    /**
     * Factory method for creating File comparators.
     * 
     * @param by
     * @param order
     * @return
     */
    public static Comparator<File> sort(By by, Order order) {
      return new Sorter(by, order);
    }

    private static class Sorter implements Comparator<File> {

      private Order order;
      private By by;

      public Sorter(By by, Order order) {
        this.by = by;
        this.order = order;
      }

      public int compare(File t1, File t2) {
        try {
          if (by == By.MODIFIED) {
            return order.getN() * (new Long(t1.getLastModified()).compareTo(t2.getLastModified()));
          } else if (by == By.DEPTH) {
            int len1 = t1.getPath().split("/").length;
            int len2 = t2.getPath().split("/").length;
            return order.getN() * ((len1 == len2) ? 0 : (len1 > len2) ? 1 : -1);
          } else {
            return order.getN() * (t1.getName().toLowerCase().compareTo(t2.getName().toLowerCase()));
          }
        } catch (IOException ex) {
        }
        return 0;
      }
    }

  }

  /**
   * Operations that can be performed on this file.
   */
  public Operations operations = new Operations();

  public class Operations {

    /**
     * String content of this file.
     * 
     * @return
     * @throws IOException
     */
    public String getStringContent() throws IOException {
      return Streams.asString(File.this.open(File.READ));
    }

    /**
     * String content of this file.
     * 
     * @return
     * @throws IOException
     */
    public String getStringContent(String encoding) throws IOException {
      return Streams.asString(File.this.open(File.READ), encoding);
    }

    /**
     * Byte the content of this file.
     * 
     * @return
     * @throws IOException
     */
    public byte[] getByteContent() throws IOException {
      return Streams.asByteArray(File.this.open(File.READ));
    }

    /**
     * Writes the provided string content to this file.
     * 
     * @param content
     * @return
     * @throws IOException
     */
    public File setStringContent(String content) throws IOException {
      return setByteContent(content.getBytes());
    }

    /**
     * Writes the provided string content to this file.
     * 
     * @param content
     * @return this file.
     * @throws IOException
     */
    public File setStringContent(String content, String encoding) throws IOException {
      return setByteContent(content.getBytes(encoding));

    }

    /**
     * Writes the provided byte content to this file.
     * 
     * @return this file.
     * @throws IOException
     */
    public File setByteContent(byte[] content) throws IOException {
      ByteArrayInputStream bos = new ByteArrayInputStream(content);
      Streams.copy(bos, File.this.open(File.WRITE), true);
      return File.this;
    }

    /**
     * Copy the content of the current file to the provided output file.
     * 
     * @param out
     * @return the copied file.
     * @throws IOException
     */
    public File copyTo(File out) throws IOException {
      Streams.copy(File.this.open(File.READ), out.open(File.WRITE), true);
      return out;
    }

    /**
     * Provides and extended dump of a file for testing / debugging.
     * 
     * @return
     * @throws IOException
     */
    public String toString() {
      StringBuilder s = new StringBuilder();
      s.append("{\n");
      s.append("\t\"URI\":\"").append(File.this.getURI()).append("\",\n");
      s.append("\t\"name\":\"").append(File.this.getName()).append("\",\n");
      s.append("\t\"path\":\"").append(File.this.getPath()).append("\",\n");

      try {
        s.append("\t\"exists\": ").append(File.this.exists()).append(",\n");
        s.append("\t\"dir\": ").append(File.this.isDirectory()).append(",\n");
        s.append("\t\"length\": ").append(File.this.length()).append("\n");
        s.append("\t\"lastModified\": \"").append(new Date(File.this.getLastModified()).toString()).append("\",\n");
      } catch (IOException e) {
        e.printStackTrace();
      }
      s.append("}");

      return s.toString();
    }

    /**
     * Deletes a directory (and all files).
     * 
     * @return
     * @throws IOException
     */
    public boolean rmdir() throws IOException {
      if (!File.this.exists()) {
        return true;
      }
      List<File> contents = File.this.list(filters.EVERYTHING, filters.ALL_DIRECTORIES, comparators.sort(comparators.By.DEPTH, comparators.Order.DECENDING));
      for (File file : contents) {
        file.delete();
      }
      File.this.delete();
      return !File.this.exists();
    }

    /**
     * A 'smart' mkdirs command. If this file is a directory the mkdirs command is called on it -
     * otherwise it is called on the parent.
     * 
     * <p>
     * This allows chaining of the create command.
     * 
     * @return the file.
     * @throws IOException
     */
    public File mkdirs() throws IOException {
      if (File.this.isDirectory() && !File.this.exists()) {
        File.this.mkdirs();
        return File.this;
      } else {
        return File.this.parent().operations.mkdirs();
      }
    }

  }

  /*
   * ABSTRACT FILE METHODS
   */

  /**
   * Used in the open method.
   */
  public static final Class<OutputStream> WRITE = OutputStream.class;
  /**
   * Used in the open method.
   */
  public static final Class<InputStream> READ = InputStream.class;

  /**
   * Provide a stream for either reading from or writing to the file. Callers should be careful to
   * close the stream after use as some streams may be very sensitive to this (like the FTP
   * implementation).
   * 
   * @param streamType one of File.READ or File.WRITE
   * @return either an InputStream or OutputStream instance.
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  public <T> T open(Class<T> streamType) throws IOException {
    if (isDirectory()) {
      throw new IOException("Cannot open directory for reading (because that is weird).");
    }
    if (streamType.equals(File.READ)) {
      return (T) read();
    }
    if (streamType.equals(File.WRITE)) {
      return (T) write();
    }
    return null;
  };

  protected abstract InputStream read() throws IOException;

  protected abstract OutputStream write() throws IOException;

  /**
   * 
   * @return last modified time in milliseconds.
   * @throws IOException
   */
  public abstract long getLastModified() throws IOException;

  /**
   * 
   * @param time last modified time in milliseconds.
   * @throws IOException
   */
  public abstract void setLastModified(long time) throws IOException;

  /**
   * 
   * @return length of the file in bytes
   * @throws IOException
   */
  public abstract long length() throws IOException;

  public abstract boolean isDirectory() throws IOException;

  public abstract boolean isFile() throws IOException;

  public abstract boolean isVisible() throws IOException;

  public abstract boolean exists() throws IOException;

  public abstract File parent() throws IOException;

  /**
   * List all of the files that pass through the provided filter sorted with the sorter.
   * 
   * @param filter
   * @param sorter
   * @return
   * @throws IOException
   */
  public abstract List<File> list(GrabFilter grab, MoveFilter move, Comparator<File> sorter) throws IOException;

  /**
   * List all of the files that pass through the provided filter sorted with the default sorter
   * (name ascending).
   * 
   * @param filter
   * @return
   * @throws IOException
   */
  public List<File> list(GrabFilter grab, MoveFilter move) throws IOException {
    return list(grab, move, comparators.DEFAULT);
  }

  public abstract String getName();

  /**
   * 
   * @return the File name without extension.
   */
  public abstract String getBaseName();

  /**
   * This method when combined with getBaseName() should give the same result as getName().
   * 
   * @return the File extension.
   */
  public abstract String getExtension();

  public abstract String getPath();

  /**
   * 
   * @return a URI capable of reconstructing this file when passed to the File.resolve() method.
   */
  public abstract URI getURI();

  public abstract boolean delete() throws IOException;

  public abstract boolean mkdir() throws IOException;

  /**
   * Attempts to create a directory with all of the required parent directories.
   * 
   * @return
   * @throws IOException
   */
  public abstract boolean mkdirs() throws IOException;

  public abstract boolean rename(File file) throws IOException;

  /**
   * Creates a new File based on the existing path of the current file.
   * 
   * @param path
   * @return new File.
   * @throws IOException
   */
  public abstract File create(String path) throws IOException;

  /**
   * Releases any resources used by this File.
   */
  public abstract void dispose();

  /**
   * Produces a 'copy' of this resource (by calling the File.resolve() method using the existing
   * getURI() call).
   */
  public File clone() {
    try {
      return File.resolve(getURI());
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "File [getURI()=" + getURI() + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((getURI() == null) ? 0 : getURI().hashCode());
    return result;
  }

  @Override
  public int compareTo(File o) {
    return this.getName().compareTo(o.getName());
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    File other = (File) obj;
    if (getURI() == null) {
      if (other.getURI() != null)
        return false;
    } else if (!getURI().equals(other.getURI()))
      return false;
    return true;
  }

  /*
   * Static utility methods
   */
  /**
   * 
   * @return current user home dir as a File.
   * @throws IOException
   */
  public static File getUserHome() throws IOException {
    return File.resolve(new java.io.File(System.getProperty("user.home")).toURI().toString());
  }

  /**
   * 
   * @return current user desktop folder as a File.
   * @throws IOException - if the user has no desktop folder
   */
  public static File getUserDesktop() throws IOException {
    File desktop = File.getUserHome().create("/Desktop/");
    if (!desktop.exists()) {
      throw new IOException("Desktop folder not found.");
    }
    return desktop;
  }

  /**
   * Creates an empty File in the default temporary-file directory, using the given prefix and
   * suffix to generate its name.
   * 
   * @param prefix
   * @param suffix
   * @return
   * @throws IOException
   */
  public static File createTempFile(String prefix, String suffix) throws IOException {
    java.io.File tmp = java.io.File.createTempFile(prefix, suffix);
    tmp.deleteOnExit();
    return File.resolve(tmp.toURI());
  }

}
