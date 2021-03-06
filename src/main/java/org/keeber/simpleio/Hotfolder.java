package org.keeber.simpleio;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.keeber.simpleio.File.GrabFilter;
import org.keeber.simpleio.File.MoveFilter;

public class Hotfolder {
  private String id;
  private int index = 0;

  public Hotfolder() {
    this.id = UUID.randomUUID().toString();
  }

  public Hotfolder(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  private File folder;
  private GrabFilter grab = File.filters.ALL_VISIBLE;
  private MoveFilter move = File.filters.ONLY_THIS_DIRECTORY;
  private Comparator<File> sorter = File.comparators.DEFAULT;

  public File getFolder() {
    return folder;
  }

  public Hotfolder setFolder(File folder) {
    this.folder = folder;
    return this;
  }


  public Hotfolder setFilters(GrabFilter grab, MoveFilter move) {
    this.grab = grab;
    this.move = move;
    return this;
  }

  public Comparator<File> getSorter() {
    return sorter;
  }

  public Hotfolder setSorter(Comparator<File> sorter) {
    this.sorter = sorter;
    return this;
  }

  private int interval = 15;
  private int settle = 4;
  private TimeUnit unit = TimeUnit.SECONDS;

  public int getInterval() {
    return interval;
  }

  /**
   * 
   * 
   * @param interval
   * @param unit
   * @param settle
   * @return
   */
  public Hotfolder setTimes(int interval, TimeUnit unit, int settle) {
    this.interval = interval;
    this.unit = unit;
    this.settle = settle;
    return this;
  }

  public Hotfolder setInterval(int interval) {
    this.interval = interval;
    return this;
  }

  public int getSettle() {
    return settle;
  }

  public Hotfolder setSettle(int settle) {
    this.settle = settle;
    return this;
  }

  public TimeUnit getUnit() {
    return unit;
  }

  public Hotfolder setUnit(TimeUnit unit) {
    this.unit = unit;
    return this;
  }

  private transient Logger logger;

  public Logger getLogger() {
    return logger == null ? (logger = Logger.getLogger(Hotfolder.class.getSimpleName() + "-" + id)) : logger;
  }

  private Subscriber subscriber;

  public Hotfolder setSubscriber(Subscriber subscriber) {
    this.subscriber = subscriber.setParent(this);
    return this;
  }

  public static abstract class Subscriber {
    private Hotfolder parent;

    /**
     * Called when a file has been 'accepted' by the hotfolder.
     * 
     * @param file
     */
    public abstract void onAdded(File file);

    /**
     * Let the parent hotfolder know this tile is no longer being used by this subscriber.
     * 
     * @param file
     */
    public void release(File file) {
      parent.release(file);
    }

    /**
     * 
     * @return the parent hotfolder for this provider.
     */
    public Hotfolder getParent() {
      return parent;
    }

    private Subscriber setParent(Hotfolder Hotfolder) {
      parent = Hotfolder;
      return this;
    }

  }

  private ScheduledExecutorService scheduler;
  private boolean running = false;

  public boolean isRunning() {
    return running;
  }

  public Hotfolder setRunning(boolean running) {
    if (this.running != running) {
      this.running = running;
      if (running) {
        index = -1;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(new Runnable() {
          public void run() {
            try {
              onInterval(index++);
            } catch (Exception ex) {
              getLogger().log(Level.SEVERE, null, ex);
            }
          }
        }, interval, interval, unit);
      } else {
        scheduler.shutdownNow();
        try {
          scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
        }
        reset();
      }
    }
    return this;
  }

  public void release(File file) {
    FileTracker tracker = filemap.remove(file);
    if (tracker != null) {
      tracker.clear();
    }
  }

  public Hotfolder reset() {
    filemap.clear();
    return this;
  }

  private final Map<File, FileTracker> filemap = new ConcurrentHashMap<File, FileTracker>();

  private void onInterval(int interval) {
    if (folder == null) {
      getLogger().log(Level.WARNING, "[Scanning] Target folder not set.");
      return;
    }
    try {
      if (!folder.exists()) {
        getLogger().log(Level.WARNING, "[Scanning] Target folder not found.");
        return;
      }
    } catch (IOException e) {
      getLogger().log(Level.SEVERE, "[Scanning] Target folder not found.", e);
    }
    getLogger().log(Level.CONFIG, "[Scanning] {0}", new Object[] {folder.getPath()});
    try {
      List<File> files = folder.list(grab, move, sorter);
      for (File file : filemap.keySet().toArray(new File[0])) {
        if (!files.contains(file)) {
          filemap.remove(file);
        }
      }
      for (File file : files) {
        if (filemap.containsKey(file)) {
          FileTracker tracker = filemap.get(file);
          if (!tracker.isLaunched()) {
            if (tracker.check(file.length(), file.getLastModified()) >= settle) {
              if (subscriber != null) {
                subscriber.onAdded(file);
              }
              tracker.launch();
            }
          }
        } else {
          filemap.put(file, new FileTracker(file.length(), file.getLastModified()));
        }
      }

    } catch (IOException e) {
      getLogger().log(Level.SEVERE, "[Scanning] ERROR", e);
    }
  }

  private class FileTracker {

    public FileTracker(long size, long lastmod) {
      this.size = size;
      this.lastmod = lastmod;
    }

    public int check(long size, long lastmod) {
      if (launched) {
        return -1;
      } else if (this.size == size && this.lastmod == lastmod) {
        duration++;
      } else {
        duration = (duration > 0) ? duration : 0;
        this.size = size;
        this.lastmod = lastmod;
      }
      return duration;
    }

    public void launch() {
      launched = true;
    }

    public void clear() {
      launched = false;
    }

    public boolean isLaunched() {
      return launched;
    }

    @Override
    public String toString() {
      return "FileTracker[duration=" + duration + ",lastmod=" + lastmod + ",size=" + size + "]";
    }

    private boolean launched;
    private long size = 0;
    private int duration = 0;
    private long lastmod = 0;
  }

}
