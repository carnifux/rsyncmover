package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.beans.RsyncMover.Servers.Server;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Config {

    private static final Logger logger = LogManager.getLogger();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private final RsyncMover config;

    public Config(final RsyncMover config) {
        this.config = config;
        setLogLevel();
    }

    private void setLogLevel() {
        if (config.getLogLevel() != null) {
            try {
                final LoggerContext context = (LoggerContext) LogManager.getContext(false);
                final LoggerConfig loggerConfig = context.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
                loggerConfig.setLevel(Level.getLevel(config.getLogLevel().toUpperCase()));
                context.updateLoggers();
                logger.info("Set log level to " + config.getLogLevel());
            } catch (Exception e) {
                logger.error("Exception setting log level", e);
            }
        }
    }

    public List<String> getWatchDir() {
        return config.getWatches().getWatch();
    }

    public boolean downloadsMustMatchMover() {
        return config.getServers().isMustMatchMoverForDownload();
    }

    public List<Server> getServers() {
        return config.getServers().getServer();
    }

    public List<RsyncMover.Movers.Mover> getMovers() {
        return config.getMovers().getMover();
    }

    public int getSyncFrequency() {
        return config.getServers().getUpdateIntervalMinutes() * 60 * 1000;
    }

    public String getPassivateLocation() {
        return config.getServers().getPassivateLocation();
    }

    public boolean moveFiles() {
        return config.getMovers().isMoveFiles();
    }

    public boolean downloadFiles() {
        return config.getServers().isDownloadFiles();
    }

    public boolean shouldDepassivateEachTime() {
        return config.getServers().isDepassivateEachTime();
    }

    public RsyncMover.EmailSummary getEmail() {
        return config.getEmailSummary();
    }

    public Set<PosixFilePermission> getFilePermissions() {
        final String permissions = config.getMovers().getMovedFilePermissions();
        return permissions != null ? PosixFilePermissions.fromString(permissions) : null;
    }

    public boolean shouldPassivateAudit() {
        return config.getAudit().isPassivate();
    }

    public String getAuditPassivateLocation() {
        return config.getAudit().getPassivateLocation();
    }

    public boolean getDeleteDuplicateFiles() {
        return config.getMovers().isDeleteDuplicateFiles();
    }

    public long getMinimumFreeSpaceForDownload() {
        final String minimumFreeSpaceToDownload = config.getServers().getMinimumFreeSpaceToDownload();
        final long multiplier = getMultiplier(minimumFreeSpaceToDownload);
        return (long) (Double.valueOf(minimumFreeSpaceToDownload.replaceAll("[^\\d\\.]", "")) * multiplier);
    }

    public List<LocalTime> getEmailSendTime() {
        final String time = config.getEmailSummary().getSendEmailAt();
        return time == null ? Collections.singletonList(LocalTime.parse("0000", TIME))
                : Stream.of(time.split(";")).map(eachTime -> LocalTime.parse(eachTime, TIME)).collect(Collectors.toList());
    }

    public boolean runServer() {
        return config.getWebServer().isWebServer();
    }

    public int getPort() {
        return config.getWebServer().getPort();
    }

    private long getMultiplier(final String freeSpace) {
        switch (freeSpace.charAt(freeSpace.length() - 1)) {
            case 'K':
            case 'k':
                return 1024L;
            case 'M':
            case 'm':
                return 1024L * 1024L;
            case 'G':
            case 'g':
                return 1024L * 1024L * 1024L;
            case 'T':
            case 't':
                return 1024L * 1024L * 1024L * 1024L;
            default:
                return 1;
        }
    }
}
