package com.carnifex.rsyncmover.audit;

import com.carnifex.rsyncmover.audit.entry.Entry;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.carnifex.rsyncmover.audit.Type.*;


public class Audit extends Thread {

    private static final String HEADERS = "HTTP/1.0 200 OK\r\nServer: rsyncMover\r\nContent-type: text/html\r\n" +
            "Content-length: %d\r\nConnection: close\r\n\r\n";
    private static final List<Type> types = Arrays.asList(ERROR, DUPLICATE, MOVED, DOWNLOADED, SEEN);
    private static final long passivateInterval = 500 * 60; // 5 minutes
    private static final Logger logger = LogManager.getLogger();
    private final Map<Type, Set<Entry>> allEntries;
    private final Map<Type, Set<Entry>> dailyEntries;
    private final Lock lock;
    private final LocalDateTime startTime;
    private final boolean passivate;
    private final String passivateLocation;
    private volatile boolean locked;
    private volatile long nextPassivate;
    private volatile boolean needToPassivate;
    private volatile boolean passivated;
    private volatile boolean passivating;

    public Audit(final boolean passivate, final String passivateLocation) {
        super("AuditThread");
        this.allEntries = new ConcurrentHashMap<>();
        this.dailyEntries = new ConcurrentHashMap<>();
        this.lock = new ReentrantLock();
        this.locked = false;
        this.startTime = LocalDateTime.now();
        this.passivate = passivate;
        this.passivateLocation = passivateLocation;
        this.needToPassivate = false;
        this.passivated = true;
        this.passivating = false;
        if (passivate) {
            this.start();
        }
        logger.info("Audit successfully initialised");
    }

    public void shutdown() {
        passivate();
        this.interrupt();
    }

    @Override
    public void run() {
        logger.info("Audit thread started, reading initial passivated file");
        allEntries.putAll(readPassivatedFile());
        for (;;) {
            final long current = System.currentTimeMillis();
            // within 30 seconds is close enough
            if (needToPassivate && current - nextPassivate < 30 * 1000) {
                logger.debug("Passivating audit");
                passivate();
            }
            nextPassivate = System.currentTimeMillis() + passivateInterval;
            try {
                Thread.sleep(passivateInterval);
            } catch (InterruptedException e) {
                logger.debug("Interrupted", e);
            }
        }
    }

    private synchronized void passivate() {
        logger.debug("Passivating audit");
        lock.lock();
        final Map<Type, Set<Entry>> map = readPassivatedFile();
        if (!needToPassivate) {
            return;
        }
        final int newSize = (int) map.values().stream().flatMap(Collection::stream).count();
        final int oldSize = (int) allEntries.values().stream().flatMap(Collection::stream).count();
        if (newSize != oldSize) {
            map.putAll(allEntries);
            FileOutputStream fileOut = null;
            ObjectOutputStream out = null;
            try {
                fileOut = new FileOutputStream(passivateLocation, false);
                out = new ObjectOutputStream(fileOut);
                out.writeObject(map);
                allEntries.clear();
                logger.debug("Audit passivated - " + (oldSize - newSize) + " entries added to passivated location");
            } catch (IOException e) {
                final String msg = "Exception writing passivated entries";
                logger.error(msg, e);
                add(new ErrorEntry(msg, e));
            } finally {
                close(fileOut);
                close(out);
            }
        }

        needToPassivate = false;
        passivated = true;
        lock.unlock();
    }

    @SuppressWarnings("unchecked")
    private Map<Type, Set<Entry>> readPassivatedFile() {
        final Map<Type, Set<Entry>> map = new HashMap<>();
        if (passivated && new File(passivateLocation).exists()) {
            FileInputStream fileIn = null;
            ObjectInputStream in = null;
            try {
                fileIn = new FileInputStream(passivateLocation);
                in = new ObjectInputStream(fileIn);
                final Map<Type, Set<Entry>> fromFile = (Map<Type, Set<Entry>>) in.readObject();
                map.putAll(fromFile);
                in.close();
                logger.debug("Read " + (int) allEntries.values().stream().flatMap(Collection::stream).count() + " passivated entries");
            } catch (IOException | ClassNotFoundException e) {
                final String msg = "Exception reading passivated entries";
                logger.error(msg, e);
                add(new ErrorEntry(msg, e));
            } finally {
                close(in);
                close(fileIn);
            }
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
        allEntries.putAll(readPassivatedFile());
        final StringBuilder message = new StringBuilder();
        message.append("<html><body>");
        message.append(makeUptime(startTime));
        for (Type type : types) {
            if (allEntries.containsKey(type)) {
                message.append(makeTitle(type));
                message.append(makeContent(allEntries.get(type)));
            }
        }
        message.append("</body></html>");
        return String.format(HEADERS, message.length()) + message.toString();
    }

    public String formatEmail() {
        final StringBuilder message = new StringBuilder();
        message.append("<html><body>");
        message.append(makeUptime(startTime));
        for (Type type : types) {
            if (dailyEntries.containsKey(type)) {
                message.append(makeTitle(type));
                message.append(makeContent(dailyEntries.get(type)));
            }
        }
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
                .sorted().collect(Collectors.joining("<br />"));
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
        if (locked && lock.tryLock()) {
            dailyEntries.clear();
        }
    }

    public void add(final Entry entry) {
        if (!lock.tryLock()) {
            lock.lock();
        }
        lock.unlock();
        add(entry, dailyEntries);
        final boolean addedToAll = add(entry, allEntries);
        if (addedToAll) {
            scheduleNextPassivate(System.currentTimeMillis());
        }
    }

    private void scheduleNextPassivate(final long next) {
        if (passivate) {
            needToPassivate = true;
            nextPassivate = next + passivateInterval;
            logger.trace("Passivate scheduled");
        }
    }

    private boolean add(final Entry entry, final Map<Type, Set<Entry>> map) {
        return map.computeIfAbsent(entry.getType(), ignore -> new HashSet<>()).add(entry);
    }
}
