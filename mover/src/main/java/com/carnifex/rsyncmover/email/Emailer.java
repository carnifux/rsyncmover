package com.carnifex.rsyncmover.email;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.*;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Emailer extends Thread {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Logger logger = LogManager.getLogger(Emailer.class);
    private static final String emailContentType = "text/html; charset=utf-8";
    private final Set<String> duplicate;
    private final Set<String> moved;
    private final Set<String> downloaded;
    private final Set<String> seen;
    private final Set<String> error;
    private final boolean sendEmail;
    private final String to;
    private final String from;
    private final Properties properties;
    private final Object lock;
    private final LocalTime timeToSendEmail;
    private volatile boolean sendingEmail;

    public Emailer(final boolean enabled, final String to, final String from, final LocalTime timeToSendEmail) {
        super("Emailer");
        this.sendEmail = enabled;
        this.duplicate = new HashSet<>();
        this.moved = new HashSet<>();
        this.downloaded = new HashSet<>();
        this.seen = new HashSet<>();
        this.error = new HashSet<>();
        this.to = to;
        this.from = from;
        this.lock = new Object();
        this.sendingEmail = false;
        this.timeToSendEmail = timeToSendEmail;

        this.properties = new Properties();
        if (sendEmail) {
            this.properties.put("mail.transport.protocol", "smtp");
            final String mxRecord = getMXRecordsForEmailAddress(to);
            logger.debug("Found host address for email " + to + " as " + mxRecord);
            this.properties.put("mail.smtp.host", mxRecord);
            this.properties.put("mail.smtp.port", "25");
            this.properties.put("mail.smtp.from", from);
            this.properties.put("mail.smtp.allow8bitmime", "true");
            this.setDaemon(true);
            this.start();
            logger.info("Email summary successfully initiated");
        }
    }

    @Override
    public void run() {
        for (;;) {
            try {
                final long sleepTime = nextEmailTime();
                logger.info("Sending next email in " + TimeUnit.MILLISECONDS.toMinutes(sleepTime) + " minutes");
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                logger.error("Emailer thread interrupted", e);
                continue; // just try to keep sleeping until we need to send the email
            }
            send();
        }
    }

    long nextEmailTime() {
        final LocalDateTime nowAtTime = LocalDate.now().atTime(timeToSendEmail);
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime nextEmail = nowAtTime.isBefore(now) ? nowAtTime.plusDays(1) : nowAtTime;
        final long next = Duration.between(now, nextEmail).getSeconds();
        final long nextInMinutes = next / 60L;
        return nextInMinutes == 0 ? Duration.between(now, now.plusDays(1)).getSeconds() * 1000L : next * 1000L;
    }

    private void send() {
        try {
            final Session session = Session.getInstance(properties);
            final MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(String.format("\"%s\"<%s>", to, from)));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject("RsyncMover Email Summary " + LocalDate.now().format(formatter));
            message.setContent(buildEmailContent(), emailContentType);
            Transport.send(message);
            logger.info("Email successfully sent");
        } catch (MessagingException e) {
            logger.error("Exception sending email", e);
        }
    }

    private String buildEmailContent() {
        final StringBuilder message = new StringBuilder();
        synchronized (lock) {
            sendingEmail = true;
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
            error.clear();
            moved.clear();
            duplicate.clear();
            downloaded.clear();
            seen.clear();
            sendingEmail = false;
        }
        return message.toString();
    }

    private String makeContent(final Set<String> strings) {
        return strings.stream().sorted().collect(Collectors.joining("<br />"));
    }

    private String makeTitle(final String title) {
        return "<br />================" + title + "================<br /><br />";
    }

    private void add(final String value, final Set<String> set) {
        if (sendEmail) {
            if (sendingEmail) {
                synchronized (lock) {
                    set.add(value);
                }
            } else {
                set.add(value);
            }
        }
    }

    public void addDuplicateDeletion(final String path) {
        add(path, duplicate);
    }

    public void addMoved(final String to, final String from, final String method) {
        add(String.format("%s: %s -> %s", method, to, from), moved);
    }

    public void addDownloaded(final String filename) {
        add(filename, downloaded);
    }

    public void addSeen(final String filename) {
        add(filename, seen);
    }

    public void addError(final String error) {
        addError(error, null);
    }

    public void addError(final String e, final Throwable t) {
        if (t == null) {
            add(e, error);
            return;
        }
        final String reduce = Stream.of(t.getStackTrace())
                .map(t_ -> t_.toString() + "<br />")
                .reduce("", (a, b) -> a + b);
        add(e + "<br />" + reduce, error);
    }

    public  String getMXRecordsForEmailAddress(String address) {
        try {
            final String hostName = address.split("@")[1];

            final Record[] records = new Lookup(hostName, Type.MX).run();
            if (records == null || records.length == 0) {
                throw new RuntimeException("No MX records found for domain " + hostName + ".");
            }

            return Stream.of(records)
                    .map(record -> (MXRecord) record)
                    .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                    .findFirst()
                    .map(MXRecord::getTarget)
                    .map(Name::toString)
                    .orElseThrow(() -> new RuntimeException("Unable to find record for email " + address));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
