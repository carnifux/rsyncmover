package com.carnifex.rsyncmover.audit;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Audit {

    private final Set<String> duplicate;
    private final Set<String> moved;
    private final Set<String> downloaded;
    private final Set<String> seen;
    private final Set<String> error;
    private final Set<String> duplicateAll;
    private final Set<String> movedAll;
    private final Set<String> downloadedAll;
    private final Set<String> seenAll;
    private final Set<String> errorAll;
    private final Lock lock;
    private final LocalDateTime startTime;
    private volatile boolean locked;

    public Audit() {
        this.duplicate = new HashSet<>();
        this.moved = new HashSet<>();
        this.downloaded = new HashSet<>();
        this.seen = new HashSet<>();
        this.error = new HashSet<>();
        this.duplicateAll = new HashSet<>();
        this.movedAll = new HashSet<>();
        this.downloadedAll = new HashSet<>();
        this.seenAll = new HashSet<>();
        this.errorAll = new HashSet<>();
        this.lock = new ReentrantLock();
        this.locked = false;
        this.startTime = LocalDateTime.now();
    }

    public String formatAll() {
        final StringBuilder message = new StringBuilder(headers());
        message.append("<html><body>");
        message.append(makeUptime(startTime));
        if (!errorAll.isEmpty()) {
            message.append(makeTitle("ERRORS"));
            message.append(makeContent(errorAll));
        }
        if (!movedAll.isEmpty()) {
            message.append(makeTitle("MOVED"));
            message.append(makeContent(movedAll));
        }
        if (!duplicateAll.isEmpty()) {
            message.append(makeTitle("DELETED DUPLICATES"));
            message.append(makeContent(duplicateAll));
        }
        if (!downloadedAll.isEmpty()) {
            message.append(makeTitle("DOWNLOADED"));
            message.append(makeContent(downloadedAll));
        }
        if (!seenAll.isEmpty()) {
            message.append(makeTitle("SEEN"));
            message.append(makeContent(seenAll));
        }
        message.append("</body></html>");
        return message.toString();
    }

    public String formatToday() {
        final StringBuilder message = new StringBuilder(headers());
        message.append("<html><body>");
        message.append(makeUptime(startTime));
        if (!error.isEmpty()) {
            message.append(makeTitle("ERRORS"));
            message.append(makeContent(error));
        }
        if (!moved.isEmpty()) {
            message.append(makeTitle("MOVED"));
            message.append(makeContent(moved));
        }
        if (!duplicate.isEmpty()) {
            message.append(makeTitle("DELETED DUPLICATES"));
            message.append(makeContent(duplicate));
        }
        if (!downloaded.isEmpty()) {
            message.append(makeTitle("DOWNLOADED"));
            message.append(makeContent(downloaded));
        }
        if (!seen.isEmpty()) {
            message.append(makeTitle("SEEN"));
            message.append(makeContent(seen));
        }
        message.append("</body></html>");
        return message.toString();
    }

    String makeUptime(final LocalDateTime startTime) {
        final Period period = Period.between(startTime.toLocalDate(), LocalDate.now());
        final Duration duration = Duration.between(startTime, LocalDateTime.now());
        final int years = period.getYears();
        final int months = period.getMonths();
        final int days = period.getDays();
        final long seconds = duration.getSeconds();
        final long hours = (seconds / (60L * 60L)) % 24L;
        final long minutes = (seconds - ((seconds / (60L * 60L)) * 60L * 60L)) / 60L;

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

    private String makeContent(final Set<String> strings) {
        return strings.stream().sorted().collect(Collectors.joining("<br />"));
    }

    private String makeTitle(final String title) {
        return "<br />================" + title + "================<br /><br />";
    }

    private String headers() {
        return "HTTP/1.0 200 OK\r\nServer: rsyncMover\r\nDate: \r\n" + LocalDateTime.now().toString() + "\r\nContent-type: text/html\r\n\r\n";
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
            duplicate.clear();
            moved.clear();
            downloaded.clear();
            seen.clear();
            error.clear();
        }
    }

    @SafeVarargs
    private final void add(final String value, final Set<String>... sets) {
        for (final Set<String> set : sets) {
            set.add(value);
        }
    }

    public void addDuplicateDeletion(final String path) {
        add(path, duplicate, duplicateAll);
    }

    public void addMoved(final String to, final String from, final String method) {
        add(String.format("%s: %s -> %s", method, to, from), moved, movedAll);
    }

    public void addDownloaded(final String filename) {
        add(filename, downloaded, downloadedAll);
    }

    public void addSeen(final String filename) {
        add(filename, seen, seenAll);
    }

    public void addError(final String error) {
        addError(error, null);
    }

    public void addError(final String e, final Throwable t) {
        if (t == null) {
            add(e, error, errorAll);
            return;
        }
        final String reduce = Stream.of(t.getStackTrace())
                .map(t_ -> t_.toString() + "<br />")
                .reduce("", (a, b) -> a + b);
        add(e + "<br />" + reduce, error, errorAll);
    }

}
