package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.beans.RsyncMover.Servers.Server;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

import javax.xml.bind.annotation.XmlElement;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileSystems;
import java.nio.file.attribute.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
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
                final Collection<LoggerConfig> loggers = context.getConfiguration().getLoggers().values();
                final Level level = Level.getLevel(config.getLogLevel().toUpperCase());
                loggers.forEach(logger -> logger.setLevel(level));
                context.updateLoggers();
                logger.info("Set log level to " + config.getLogLevel());
            } catch (Exception e) {
                logger.error("Exception setting log level", e);
            }
        }
    }

    public long getMaxDownloadSpeedBytes() {
        final String maxDownloadSpeed = config.getServers().getMaxDownloadSpeed();
        return Long.parseLong(maxDownloadSpeed == null ? getDefault(config.getServers(), "getMaxDownloadSpeed", String.class) : maxDownloadSpeed);
    }

    public boolean isRunOnce() {
        final Boolean runOnce = config.isRunOnce();
        return runOnce == null ? getDefault(config, "isRunOnce", boolean.class) : runOnce;
    }

    public int maxConcurrentDownloads() {
        final Integer maxConcurrentDownloads = config.getServers().getMaxConcurrentDownloads();
        return maxConcurrentDownloads == null ? getDefault(config.getServers(), "getMaxConcurrentDownloads", int.class) : maxConcurrentDownloads;
    }

    public boolean shouldWriteLogFile() {
        return config.getMovers().isWriteLogFile();
    }

    public UserPrincipal getUserPrincipal() {
        final String user = config.getMovers().getMovedFileUser();
        if (user != null) {
            try {
                return FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(user);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public boolean killDownloadOnExit() {
        final Boolean killDownloadOnExit = config.getServers().isKillDownloadOnExit();
        return killDownloadOnExit == null ? getDefault(config.getServers(), "isKillDownloadOnExit", boolean.class) : killDownloadOnExit;
    }

    public String getMoverPassivateLocation() {
        return config.getMovers().getPassivateLocation();
    }

    public List<String> getWatchDir() {
        return config.getWatches().getWatch();
    }

    public boolean downloadsMustMatchMover() {
        final Boolean mustMatchMoverForDownload = config.getServers().isMustMatchMoverForDownload();
        return mustMatchMoverForDownload == null ? getDefault(config.getServers(), "isMustMatchMoverForDownload", boolean.class) : mustMatchMoverForDownload;
    }

    public List<Server> getServers() {
        return config.getServers().getServer();
    }

    public List<RsyncMover.Movers.Mover> getMovers() {
        return config.getMovers().getMover();
    }

    public int getSyncFrequency() {
        final Integer updateIntervalMinutes = config.getServers().getUpdateIntervalMinutes();
        return (updateIntervalMinutes == null ? getDefault(config.getServers(), "getUpdateIntervalMinutes", int.class) : updateIntervalMinutes) * 60 * 1000;
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
        final Boolean depassivateEachTime = config.getServers().isDepassivateEachTime();
        return depassivateEachTime == null ? getDefault(config.getServers(), "isDepassivateEachTime", boolean.class) : depassivateEachTime;
    }

    public RsyncMover.EmailSummary getEmail() {
        return config.getEmailSummary();
    }

    public boolean isLazyPolling() {
        final Boolean lazyPolling = config.getMovers().isLazyPolling();
        return config.getServers().isDownloadFiles() && lazyPolling == null ? getDefault(config.getMovers(), "isLazyPolling", boolean.class) : lazyPolling;
    }

    public Set<PosixFilePermission> getFilePermissions() {
        final String permissions = config.getMovers().getMovedFilePermissions();
        return permissions != null ? PosixFilePermissions.fromString(permissions) : null;
    }

    public Set<PosixFilePermission> getFolderPermissions() {
        final String permissions = config.getMovers().getMovedFolderPermissions();
        return permissions != null ? PosixFilePermissions.fromString(permissions) : null;
    }

    public boolean shouldPassivateAudit() {
        final Boolean passivate = config.getAudit().isPassivate();
        return passivate == null ? getDefault(config.getAudit(), "isPassivate", boolean.class) : passivate;
    }

    public String getAuditPassivateLocation() {
        return config.getAudit().getPassivateLocation();
    }

    public boolean getDeleteDuplicateFiles() {
        final Boolean deleteDuplicateFiles = config.getMovers().isDeleteDuplicateFiles();
        return deleteDuplicateFiles == null ? getDefault(config.getMovers(), "isDeleteDuplicateFiles", boolean.class) : deleteDuplicateFiles;
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
        final Boolean webServer = config.getWebServer().isWebServer();
        return webServer == null ? getDefault(config.getWebServer(), "isWebServer", boolean.class) : webServer;
    }

    public int getPort() {
        final Integer port = config.getWebServer().getPort();
        return port == null ? getDefault(config.getWebServer(), "getPort", int.class) : port;
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

    @SuppressWarnings("unchecked")
    private <T> T getDefault(final Object obj, final String methodName, final Class<T> returnType) {
        try {
            final String fieldName = findFieldName(methodName);
            final Field declaredField = obj.getClass().getDeclaredField(fieldName);
            final XmlElement declaredAnnotation = declaredField.getDeclaredAnnotation(XmlElement.class);
            if (declaredAnnotation != null) {
                final String val = declaredAnnotation.defaultValue();
                if (returnType == boolean.class) {
                    return (T) Boolean.valueOf(val);
                } else if (returnType == int.class) {
                    return (T) Integer.valueOf(val);
                } else {
                    return (T) val;
                }
            } else {
                throw new RuntimeException();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // visible for testing
    String findFieldName(final String methodName) {
        int i = 0;
        while (!Character.isUpperCase(methodName.charAt(i))) {
            i++;
        }
        final String substring = methodName.substring(i);
        return substring.substring(0, 1).toLowerCase() + substring.substring(1);
    }
}
