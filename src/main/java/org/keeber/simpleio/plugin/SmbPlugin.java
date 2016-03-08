package org.keeber.simpleio.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.keeber.simpleio.File;
import org.keeber.simpleio.File.Plugin;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

public class SmbPlugin extends Plugin {
  public static final String FILEPROTOCOL = "file";

  @Override
  public File resolve(URI uri) throws IOException {
    return new SmbSIOFile(new SmbFile(Plugin.unescape(uri.toString())));
  }

  @Override
  public String getScheme() {
    return "smb";
  }

  public static Plugin create() {
    return new SmbPlugin();
  }

  public static void setSMBProperty(String prop, String val) {
    jcifs.Config.setProperty(prop, val);
  }

  public static class SmbSIOFile extends File {
    protected SmbFile ref;

    protected SmbSIOFile(SmbFile ref) {
      this.ref = ref;
    }

    protected InputStream read() throws IOException {
      return new SmbFileInputStream(ref);
    }

    protected OutputStream write() throws IOException {
      return new SmbFileOutputStream(ref);
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
      return ref.exists() ? ref.length() : 0;
    }

    @Override
    public boolean isDirectory() throws IOException {
      return ref.isDirectory() || ref.toString().endsWith("/");
    }

    @Override
    public boolean isFile() throws IOException {
      return !isDirectory();
    }

    @Override
    public boolean isVisible() throws IOException {
      return !ref.getName().startsWith(".") && !ref.isHidden();
    }

    @Override
    public boolean exists() throws IOException {
      return ref.exists();
    }

    @Override
    public File parent() throws IOException {
      return new SmbSIOFile(new SmbFile(ref.getParent()));
    }

    @Override
    public List<File> list(GrabFilter grab, MoveFilter move, Comparator<File> sorter) throws IOException {
      List<File> list;
      try {
        list = IOList(grab, move, ref, 0);
      } catch (SmbException e) {
        throw new IOException(e);
      }
      Collections.sort(list, sorter);
      return list;
    }


    private ArrayList<File> IOList(GrabFilter grab, MoveFilter move, SmbFile root, int depth) throws SmbException, IOException {
      ArrayList<File> retList = new ArrayList<File>();
      SmbFile[] list;
      if (root.canRead()) {
        list = root.listFiles();
        if (list != null) {
          for (SmbFile f : list) {
            if (grab.shouldGrab(new SmbSIOFile(f))) {
              retList.add(new SmbSIOFile(f));
            }
            if (f.isDirectory() && move.shouldMove(new SmbSIOFile(f), depth)) {
              retList.addAll(IOList(grab, move, f, depth + 1));
            }
          }
        }
      }
      return retList;
    }

    @Override
    public String getName() {
      return ref.getName().replaceFirst("/$", "");
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
      return unescape(Plugin.cleanPath(getURI().getPath()));
    }

    @Override
    public URI getURI() {
      try {
        return new URI(ref.toString().replaceAll(" ", "%20"));
      } catch (URISyntaxException e) {
        return null;
      }
    }

    @Override
    public boolean delete() throws IOException {
      ref.delete();
      return ref.exists();
    }

    @Override
    public boolean mkdir() throws IOException {
      ref.mkdir();
      return ref.exists();
    }

    @Override
    public boolean mkdirs() throws IOException {
      ref.mkdirs();
      return ref.exists();
    }

    @Override
    public boolean rename(File file) throws IOException {
      if (!SmbSIOFile.class.equals(file.getClass())) {
        throw new IOException("Cross scheme rename not implemented (or allowed).");
      }
      ref.renameTo(new SmbFile(unescape(file.getURI().toString())));
      return file.exists();
    }

    @Override
    public File create(String path) throws IOException {
      return new SmbSIOFile(new SmbFile(ref.toString() + unescape(path)));
    }

    @Override
    public void dispose() {
      // Not required
    }

  }

}
