package com.carnifex.rsyncmover.audit;

import com.carnifex.rsyncmover.audit.entry.Entry;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import com.carnifex.rsyncmover.sync.Ssh.DownloadWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.carnifex.rsyncmover.audit.Type.DOWNLOADED;
import static com.carnifex.rsyncmover.audit.Type.DUPLICATE;
import static com.carnifex.rsyncmover.audit.Type.ERROR;
import static com.carnifex.rsyncmover.audit.Type.MOVED;
import static com.carnifex.rsyncmover.audit.Type.SEEN;


public class Audit extends Thread {

    private static final String HEADERS = "";
    private static final List<Type> types = Arrays.asList(ERROR, DUPLICATE, MOVED, DOWNLOADED, SEEN);
    private static final long persistInterval = 5 * 1000 * 60; // 5 minutes
    private static final Logger logger = LogManager.getLogger();
    private final Map<Type, Set<Entry>> allEntries;
    private final Map<Type, Set<Entry>> dailyEntries;
    private final Lock lock;
    private final LocalDateTime startTime;
    private final boolean persist;
    private final String persistLocation;
    private volatile boolean locked;
    private volatile long nextPersist;
    private volatile boolean needToPersist;
    private volatile boolean persisted;
    private transient List<DownloadWatcher> downloadWatchers;

    public Audit(final boolean persist, final String persistLocation, final Audit old) {
        super("AuditThread");
        if (old == null) {
            this.allEntries = new ConcurrentHashMap<>();
            this.dailyEntries = new ConcurrentHashMap<>();
            this.startTime = LocalDateTime.now();
        } else {
            this.allEntries = old.allEntries;
            this.dailyEntries = old.dailyEntries;
            this.startTime = old.startTime;
            old.shutdown();
        }
        this.lock = new ReentrantLock();
        this.locked = false;
        this.persist = persist;
        this.persistLocation = persistLocation;
        this.needToPersist = false;
        this.persisted = true;
        this.downloadWatchers = new ArrayList<>();
        if (persist) {
            this.start();
        }
        logger.info("Audit successfully initialised");
    }

    public void addDownloadWatcher(final DownloadWatcher downloadWatcher) {
        this.downloadWatchers.add(downloadWatcher);
    }

    public void shutdown() {
        persist();
        this.interrupt();
    }

    @Override
    public void run() {
        logger.info("Audit thread started, reading initial persisted file");
        addEntriesToMap(readPersistedFile(), allEntries);
        for (;;) {
            final long current = System.currentTimeMillis();
            // within 30 seconds is close enough
            if (needToPersist && current - nextPersist < 30 * 1000) {
                persist();
            }
            nextPersist = System.currentTimeMillis() + persistInterval;
            try {
                Thread.sleep(persistInterval);
            } catch (InterruptedException e) {
                logger.debug("Interrupted", e);
                break;
            }
        }
    }

    private synchronized void persist() {
        if (!persist || !needToPersist) {
            logger.debug("No need to persist audit, returning");
            return;
        }
        logger.debug("Persisting audit");
        lock.lock();
        final Map<Type, Set<Entry>> map = readPersistedFile();
        final int fileSize = (int) map.values().stream().flatMap(Collection::stream).count();
        final int memorySize = (int) allEntries.values().stream().flatMap(Collection::stream).count();
        if (fileSize != memorySize) {
            final int entriesAdded = addEntriesToMap(allEntries, map);
            FileOutputStream fileOut = null;
            ObjectOutputStream out = null;
            try {
                fileOut = new FileOutputStream(persistLocation, false);
                out = new ObjectOutputStream(fileOut);
                out.writeObject(map);
                allEntries.clear();
                logger.debug("Audit persisted - " + entriesAdded + " entries added to persisted location");
            } catch (IOException e) {
                final String msg = "Exception writing persisted entries";
                logger.error(msg, e);
                add(new ErrorEntry(msg, e));
            } finally {
                close(fileOut);
                close(out);
            }
        }

        needToPersist = false;
        persisted = true;
        lock.unlock();
    }

    private int addEntriesToMap(final Map<Type, Set<Entry>> newMap, final Map<Type, Set<Entry>> mapToAddTo) {
        return newMap.entrySet().stream()
                .mapToInt(entry -> {
                    final Set<Entry> entries = mapToAddTo.computeIfAbsent(entry.getKey(), ignore -> new HashSet<>());
                    final int oldSize = entries.size();
                    entries.addAll(entry.getValue());
                    return entries.size() - oldSize;
                })
                .sum();
    }

    @SuppressWarnings("unchecked")
    private Map<Type, Set<Entry>> readPersistedFile() {
        final Map<Type, Set<Entry>> map = new HashMap<>();
        if (persist && persisted && new File(persistLocation).exists()) {
            FileInputStream fileIn = null;
            ObjectInputStream in = null;
            try {
                fileIn = new FileInputStream(persistLocation);
                in = new ObjectInputStream(fileIn);
                final Map<Type, Set<Entry>> fromFile = (Map<Type, Set<Entry>>) in.readObject();
                map.putAll(fromFile);
                persisted = false;
                logger.debug("Read " + (int) allEntries.values().stream().flatMap(Collection::stream).count() + " persisted entries");
            } catch (InvalidClassException e) {
                final String msg = "Invalid class detected deserializing - deleting audit. Sorry :(";
                logger.error(msg, e);
                add(new ErrorEntry(msg, e));
                close(in);
                close(fileIn);
                try {
                    Files.delete(Paths.get(persistLocation));
                } catch (Exception e1) {
                    final String message = "Error deleting invalid audit";
                    logger.error(message, e1);
                    add(new ErrorEntry(message, e1));
                }
            } catch (IOException | ClassNotFoundException e) {
                final String msg = "Exception reading persisted entries";
                logger.error(msg, e);
                add(new ErrorEntry(msg, e));
            } finally {
                close(in);
                close(fileIn);
            }
            needToPersist = true;
        }
        return map;
    }

    private void close(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                final String msg = "Exception closing closeable";
                logger.error(msg, e);
                add(new ErrorEntry(msg, e));
            }
        }
    }

    public String formatAll() {
        addEntriesToMap(readPersistedFile(), allEntries);
        final StringBuilder message = new StringBuilder();
        message.append("<html><body>");
        message.append(makeUptime(startTime));
        // i really need to rewrite this
        message.append("<div id=\"status\" style=\"height:200px\"></div>")
                .append("<script>var prev = \"\";\n" +
                        "var f = function() {\n" +
                        "    var req = new XMLHttpRequest();\n" +
                        "    req.onreadystatechange = function() {\n" +
                        "        var res = req.responseText;\n" +
                        "        if (prev !== res) {\n" +
                        "            document.getElementById(\"status\").innerHTML = res;\n" +
                        "        }\n" +
                        "        prev = res;\n" +
                        "    }\n" +
                        "    req.open(\"GET\", window.location.href + \"/downloadstatus\", true);\n" +
                        "    req.send(null);\n" +
                        "    setTimeout(f, 1000);\n" +
                        "};\n" +
                        "setTimeout(f, 1000);</script>");
        types.stream().filter(allEntries::containsKey).forEachOrdered(type -> {
            message.append(makeTitle(type));
            message.append(makeContent(allEntries.get(type)));
        });
        message.append("</body></html>");
        return String.format(HEADERS, message.length()) + message.toString();
    }

    public String formatEmail() {
        final StringBuilder message = new StringBuilder();
        message.append("<html><body>");
        message.append(makeUptime(startTime));
        types.stream().filter(dailyEntries::containsKey).forEachOrdered(type -> {
            message.append(makeTitle(type));
            message.append(makeContent(dailyEntries.get(type)));
        });
        message.append("</body></html>");
        return message.toString();
    }

    // visible for testing
    String makeUptime(final LocalDateTime startTime) {
        final LocalDate nowDate = LocalDate.now();
        final LocalDateTime nowTime = LocalDateTime.now();
        final Period period = Period.between(startTime.toLocalDate(), nowDate);
        final Duration duration = Duration.between(startTime, nowTime);
        final long seconds = duration.getSeconds();
        final long rawHours = seconds / (60L * 60L);
        final long hours = rawHours % 24L;
        final long minutes = (seconds - (rawHours * 60L * 60L)) / 60L;
        final int years = period.getYears();
        final int months = period.getMonths();
        // subtract a day if 24 hours havent passed yet so days cant possibly be >0
        // or if we've passed a day boundary (ie 23:00 -> 01:00), as Period will count this as an extra day
        final int days = Math.max(0, rawHours >= 24 && nowTime.minusHours(hours).toLocalDate().isEqual(nowDate) ? period.getDays() : period.getDays() - 1);

        final StringBuilder builder = new StringBuilder("Uptime: ");
        if (years > 0) {
            builder.append(years).append(" year").append(years != 1 ? "s " : " ");
        }
        if (months > 0) {
            builder.append(months).append(" month").append(months != 1 ? "s " : " ");
        }
        builder.append(days).append(" day").append(days != 1 ? "s " : " ");
        builder.append(hours).append(" hour").append(hours != 1 ? "s " : " ");
        builder.append(minutes).append(" minute").append(minutes != 1 ? "s" : "");
        return builder.append("<br /><br />").toString();
    }

    private String makeContent(final Set<Entry> strings) {
        return strings.stream().map(entry -> entry.getCreatedAt().toString() + ": " + entry.format())
                .sorted(Comparator.reverseOrder()).collect(Collectors.joining("<br />"));
    }

    private String makeTitle(final Type type) {
        return "<br />================" + type.toString() + "================<br /><br />";
    }

    public void accessing() {
        lock.lock();
        locked = true;
    }

    public void stopAccessing() {
        lock.unlock();
        locked = false;
    }

    public void clear() {
        if (locked) {
            dailyEntries.clear();
        }
    }

    public void add(final Entry entry) {
        lock.lock();
        add(entry, dailyEntries);
        final boolean addedToAll = add(entry, allEntries);
        if (addedToAll) {
            scheduleNextPersist(System.currentTimeMillis());
        }
        lock.unlock();
    }

    private void scheduleNextPersist(final long next) {
        if (persist && !needToPersist) {
            needToPersist = true;
            nextPersist = next + persistInterval;
            logger.trace("persist scheduled");
        }
    }

    private boolean add(final Entry entry, final Map<Type, Set<Entry>> map) {
        return map.computeIfAbsent(entry.getType(), ignore -> new HashSet<>()).add(entry);
    }

    public String getDownloadWatcherStatuses() {
        return downloadWatchers.stream()
                .filter(DownloadWatcher::isCurrentlyActive)
                .map(DownloadWatcher::getMessage)
                .collect(Collectors.joining("<br />")) + "<br /><br /><br />";
    }
}
