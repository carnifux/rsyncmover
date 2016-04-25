package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.beans.RsyncMover.Servers.Server;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

public class Config {

    private final RsyncMover config;

    public Config(final RsyncMover config) {
        this.config = config;
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

    public boolean getDeleteDuplicateFiles() {
        return config.getMovers().isDeleteDuplicateFiles();
    }

    public long getMinimumFreeSpaceForDownload() {
        final String minimumFreeSpaceToDownload = config.getServers().getMinimumFreeSpaceToDownload();
        final long multiplier = getMultiplier(minimumFreeSpaceToDownload);
        return (long) (Double.valueOf(minimumFreeSpaceToDownload.replaceAll("[^\\d\\.]", "")) * multiplier);
    }

    public LocalTime getEmailSendTime() {
        final String time = config.getEmailSummary().getSendEmailAt();
        return LocalTime.parse(time != null ? time : "0000", DateTimeFormatter.ofPattern("HHmm"));
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
