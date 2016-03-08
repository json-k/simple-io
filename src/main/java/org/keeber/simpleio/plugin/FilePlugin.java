package org.keeber.simpleio.plugin;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.keeber.simpleio.File;

public class FilePlugin extends File.Plugin {
  private static final java.io.File home = new java.io.File(System.getProperty("user.home"));
  public static final String FILE_SCHEME = "file";

  @Override
  protected File resolve(URI uri) throws IOException {
    String path = cleanPath(uri.getPath().startsWith("~") ? uri.getPath().replaceFirst("^~", home.toURI().getPath()) : uri.getPath());
    return new SIOFile(new java.io.File(path), path.endsWith("/"));
  }

  @Override
  public String getScheme() {
    return FILE_SCHEME;
  }

  public static class SIOFile extends File {
    private java.io.File ref;
    private boolean isDir = false;

    protected SIOFile(java.io.File ref, boolean isDir) throws IOException {
      this.ref = ref.getCanonicalFile();
      this.isDir = isDir;
    }

    protected InputStream read() throws IOException{
      return new FileInputStream(ref);
    }
    
    protected OutputStream write() throws IOException{
      return new FileOutputStream(ref);
    }
    
    @Override
    public long getLastModified() throws IOException {
      return ref.lastModified();
    }

    @Override
    public void setLastModified(long time) throws IOException {
      ref.setLastModified(time);
    }

    @Override
    public long length() throws IOException {
      return ref.length();
    }

    @Override
    public boolean isDirectory() {
      return ref.exists() ? ref.isDirectory() : isDir;
    }

    @Override
    public boolean isFile() {
      return !isDirectory();
    }

    @Override
    public boolean isVisible() {
      return !ref.getName().startsWith(".") && !ref.isHidden();
    }

    @Override
    public boolean exists() throws IOException {
      return ref.exists();
    }

    @Override
    public File parent() throws IOException {
      return new SIOFile(ref.getParentFile(), true);
    }

    @Override
    public List<File> list(GrabFilter grab, MoveFilter move, Comparator<File> default1) throws IOException {
      List<File> list = IOList(grab, move, ref, 0);
      Collections.sort(list, default1);
      return list;
    }

    public ArrayList<File> IOList(GrabFilter grab, MoveFilter move, java.io.File root, int depth) throws IOException {
      ArrayList<File> retList = new ArrayList<File>();
      java.io.File[] list;
      if (root.canRead()) {
        list = root.listFiles();
        if (list != null) {
          for (java.io.File f : list) {
            if (grab.shouldGrab(new SIOFile(f, f.isDirectory()))) {
              retList.add(new SIOFile(f, f.isDirectory()));
            }
            if (f.isDirectory() && move.shouldMove(new SIOFile(f, f.isDirectory()), depth)) {
              retList.addAll(IOList(grab, move, f, depth + 1));
            }
          }
        }
      }
      return retList;
    }

    @Override
    public String getName() {
      return ref.getName();
    }

    @Override
    public String getBaseName() {
      return Plugin.getBaseName(getName());
    }

    @Override
    public String getExtension() {
      return Plugin.getExtension(getName());
    }

    @Override
    public String getPath() {
      return Plugin.cleanPath(ref.toURI().normalize().getPath() + (isDirectory() ? "/" : ""));
    }

    @Override
    public URI getURI() {
      return Plugin.normalize(ref.toURI(), isDirectory());
    }

    @Override
    public boolean delete() throws IOException {
      return ref.delete();
    }

    @Override
    public boolean mkdir() throws IOException {
      return ref.mkdir();
    }

    @Override
    public boolean mkdirs() throws IOException {
      return ref.mkdirs();
    }

    @Override
    public boolean rename(File file) throws IOException {
      if (!SIOFile.class.equals(file.getClass())) {
        throw new IOException("Cross scheme rename not implemented (or allowed).");
      }
      return ref.renameTo(new java.io.File(file.getURI()));
    }

    @Override
    public File create(String path) throws IOException {
      return File.resolve(URI.create(escape(getPath() + path)));
    }

    @Override
    public void dispose() {
      // Not required
    }


  }

}
