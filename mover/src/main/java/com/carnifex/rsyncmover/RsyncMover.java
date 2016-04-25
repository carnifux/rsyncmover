package com.carnifex.rsyncmover;


import com.carnifex.rsyncmover.config.Config;
import com.carnifex.rsyncmover.config.ConfigLoader;
import com.carnifex.rsyncmover.email.Emailer;
import com.carnifex.rsyncmover.mover.io.FileChangeWatcher;
import com.carnifex.rsyncmover.mover.io.FileWatcher;
import com.carnifex.rsyncmover.mover.io.Mover;
import com.carnifex.rsyncmover.mover.io.MoverThread;
import com.carnifex.rsyncmover.sync.Ssh;
import com.carnifex.rsyncmover.sync.SyncedFiles;
import com.carnifex.rsyncmover.sync.Syncer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class RsyncMover {

    private static final Logger logger = LogManager.getLogger(RsyncMover.class);

    public static void main(String[] args) {
        start(args != null && args.length > 0 ? args[0] : "config.xml");
    }

    public static void start(final String configPath) {
        final Config config = new Config(new ConfigLoader().load(configPath));
        final Emailer emailer = new Emailer(config.getEmail().isEmailReport(), config.getEmail().getTo(), config.getEmail().getFrom(), config.getEmailSendTime());
        final List<Mover> movers = config.getMovers().stream().map(Mover::new).collect(Collectors.toList());

        if (config.moveFiles()) {
            final MoverThread moverThread = new MoverThread(config.getFilePermissions(), config.getDeleteDuplicateFiles(), emailer);
            final FileChangeWatcher fileChangeWatcher = new FileChangeWatcher(movers, moverThread);
            final String passivateLocation = config.getPassivateLocation();
            final List<FileWatcher> watchers = config.getWatchDir().stream().map(watch -> new FileWatcher(watch,
                    passivateLocation != null ? Collections.singleton(passivateLocation) : Collections.emptySet(),
                    fileChangeWatcher)).collect(Collectors.toList());
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    // finish any pending moves before shutting down vm
                    watchers.forEach(FileWatcher::shutdown);
                    moverThread.shutdown();
                }
            });
            logger.info("File moving successfully initiated");
        }
        if (config.downloadFiles()) {
            final List<Ssh> sshs = config.getServers().stream()
                    .flatMap(server -> server.getDirectories().getDirectory().stream()
                        .map(dir -> new Ssh(server.getHost(), server.getPort(),
                            dir.getDirectory(), dir.getRealDirectory(), server.getUser(), server.getPass(),
                            server.getHostKey(), config.getFilePermissions())))
                    .collect(Collectors.toList());
            final SyncedFiles syncedFiles = new SyncedFiles(Paths.get(config.getPassivateLocation()));
            final Syncer syncer = new Syncer(config.getWatchDir(), sshs, syncedFiles, config.getSyncFrequency(),
                    config.shouldDepassivateEachTime(), config.getMinimumFreeSpaceForDownload(), config.getFilePermissions(),
                    config.downloadsMustMatchMover(), movers, emailer);
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    // finish any pending downloads before shutting down vm
                    syncer.shutdown();
                }
            });
            logger.info("File downloading successfully initiated");
        }

        if (!config.downloadFiles() && !config.moveFiles()) {
            logger.info("No operations configured, shutting down");
        }
    }
}
