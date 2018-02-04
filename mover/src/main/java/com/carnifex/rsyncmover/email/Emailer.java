package com.carnifex.rsyncmover.email;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xbill.DNS.*;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Emailer extends Thread {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Logger logger = LogManager.getLogger();
    private static final String emailContentType = "text/html; charset=utf-8";
    private final boolean enabled;
    private final String to;
    private final String from;
    private final LocalTime timeToSendEmail;
    private final Audit audit;

    public Emailer(final boolean enabled, final String to, final String from, final LocalTime timeToSendEmail, final Audit audit) {
        super("Emailer");
        this.enabled = enabled;
        this.to = to;
        this.from = from;
        this.timeToSendEmail = timeToSendEmail;
        this.audit = audit;
    }

    @Override
    public void run() {
        logger.info("Email summary successfully initiated at time " + timeToSendEmail);
        for (;;) {
            try {
                final long sleepTime = nextEmailTime();
                logger.info("Sending next email in " + TimeUnit.MILLISECONDS.toMinutes(sleepTime) + " minutes");
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                logger.debug("Emailer thread interrupted", e);
                return;
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

    public void send() {
        if (!enabled) {
            return;
        }
        try {
            final Properties properties = new Properties();
            properties.put("mail.transport.protocol", "smtp");
            final String mxRecord = getMXRecordsForEmailAddress(to);
            logger.debug("Found host address for email " + to + " as " + mxRecord);
            properties.put("mail.smtp.host", "relay.plus.net");
            properties.put("mail.smtp.port", "25");
            properties.put("mail.smtp.from", from);
            properties.put("mail.smtp.allow8bitmime", "true");

            final Session session = Session.getInstance(properties);
            final MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(String.format("\"%s\"<%s>", from, from)));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject("RsyncMover Email Summary " + LocalDate.now().format(formatter));
            audit.accessing();
            message.setContent(audit.formatEmail(), emailContentType);
            audit.clear();
            audit.stopAccessing();
            Transport.send(message);
            logger.info("Email successfully sent");
        } catch (MessagingException e) {
            final String msg = "Exception sending email";
            logger.error(msg, e);
            audit.add(new ErrorEntry(msg, e));
        }
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
                    .sorted(Comparator.comparingInt(MXRecord::getPriority))
                    .findFirst()
                    .map(MXRecord::getTarget)
                    .map(Name::toString)
                    .orElseThrow(() -> new RuntimeException("Unable to find record for email " + address));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
