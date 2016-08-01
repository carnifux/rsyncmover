package com.carnifex.rsyncmover;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.config.Config;
import com.carnifex.rsyncmover.config.ConfigLoader;
import com.carnifex.rsyncmover.config.ConfigWatcher;
import com.carnifex.rsyncmover.email.Emailer;
import com.carnifex.rsyncmover.mover.io.FileChangeWatcher;
import com.carnifex.rsyncmover.mover.io.FileWatcher;
import com.carnifex.rsyncmover.mover.io.Mover;
import com.carnifex.rsyncmover.mover.io.MoverThread;
import com.carnifex.rsyncmover.sync.Sftp;
import com.carnifex.rsyncmover.sync.SyncedFiles;
import com.carnifex.rsyncmover.sync.Syncer;
import com.carnifex.rsyncmover.web.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RsyncMover {

    private static final Logger logger = LogManager.getLogger();
    private static final Map<Class<?>, Object> components = new ConcurrentHashMap<>();


    public static void main(String[] args) {
        final String configPath = args != null && args.length > 0 ? args[0] : "config.xml";
        final Config config = new Config(new ConfigLoader().load(configPath));
        init(config);
        final ConfigWatcher configWatcher = new ConfigWatcher(configPath);
        components.put(ConfigWatcher.class, configWatcher);
    }

    public static synchronized void init(final Config config) {

        final Audit audit = new Audit(config.shouldPassivateAudit(), config.getAuditPassivateLocation(), (Audit) components.get(Audit.class));
        components.put(Audit.class, audit);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                audit.shutdown();
            }
        });

        final List<Emailer> emailers = config.getEmailSendTime().stream()
                .map(time -> new Emailer(config.getEmail().isEmailReport(), config.getEmail().getTo(), config.getEmail().getFrom(), time, audit))
                .collect(Collectors.toList());
        components.putIfAbsent(Emailer.class, emailers);
        final List<Mover> movers = config.getMovers().stream().map(m -> new Mover(m, audit)).collect(Collectors.toList());

        if (config.moveFiles()) {
            final MoverThread moverThread = initMoverThread(config, audit);
            final String moverPassivateLocation = config.getMoverPassivateLocation();
            final SyncedFiles syncedFiles = new SyncedFiles(moverPassivateLocation != null ? Paths.get(moverPassivateLocation) : null);
            final FileChangeWatcher fileChangeWatcher = new FileChangeWatcher(movers, moverThread, syncedFiles);
            final List<FileWatcher> fileWatchers = initFileWatchers(config, moverThread, fileChangeWatcher, audit);
            components.putIfAbsent(FileChangeWatcher.class, fileChangeWatcher);
            components.putIfAbsent(MoverThread.class, moverThread);
            components.putIfAbsent(FileWatcher.class, fileWatchers);
            logger.info("File moving successfully initiated");
        }

        if (config.downloadFiles()) {
            final List<Sftp> sftps = initSshs(config);
            final Syncer syncer = initSyncer(config, movers, sftps, audit);
            components.putIfAbsent(Sftp.class, sftps);
            components.putIfAbsent(Syncer.class, syncer);
            logger.info("File downloading successfully initiated");
        }

        if (!config.moveFiles() && !config.downloadFiles()) {
            logger.info("No operations configured, shutting down");
            return;
        }

        emailers.forEach(Thread::start);

        if (config.runServer()) {
            final Server server = new Server(config.getPort(), (Syncer) components.get(Syncer.class), audit);
            components.putIfAbsent(Server.class, server);
        }
    }

    private static List<Sftp> initSshs(final Config config) {
        return config.getServers().stream()
            .flatMap(server -> server.getDirectories().getDirectory().stream()
                    .map(dir -> new Sftp(server.getHost(), server.getPort(),
                            dir.getDirectory(), dir.getRealDirectory(), server.getUser(), server.getPass(),
                            server.getHostKey(), config.getFilePermissions())))
            .collect(Collectors.toList());
    }


    public static synchronized void reinit(final String configPath) {
        shutdownAll();
        init(new Config(new ConfigLoader().load(configPath)));
    }

    @SuppressWarnings("unchecked")
    private static void shutdownAll() {
        final List<Emailer> emailer = (List<Emailer>) components.remove(Emailer.class);
        emailer.forEach(Thread::interrupt);
        // shut down movers first so any current downloads arent moved with old movers
        if (components.containsKey(MoverThread.class)) {
            final MoverThread moverThread = (MoverThread) components.remove(MoverThread.class);
            moverThread.shutdown();
            final List<FileWatcher> fileWatchers = (List<FileWatcher>) components.remove(FileWatcher.class);
            fileWatchers.forEach(FileWatcher::shutdown);
            final FileChangeWatcher fileChangeWatcher = (FileChangeWatcher) components.remove(FileChangeWatcher.class);
            fileChangeWatcher.interrupt();
        }
        if (components.containsKey(Syncer.class)) {
            final List<Sftp> sftps = (List<Sftp>) components.remove(Sftp.class);
            final Syncer syncer = (Syncer) components.remove(Syncer.class);
            syncer.shutdown();
        }
        if (components.containsKey(Server.class)) {
            final Server server = (Server) components.remove(Server.class);
            server.shutdown();
        }
        // should contain audit and config watcher
        if (components.size() != 2) {
            throw new RuntimeException("Did not remove all components - shutdown not successful.");
        }
    }

    private static MoverThread initMoverThread(final Config config, final Audit audit) {
        return new MoverThread(config.getFilePermissions(), config.getDeleteDuplicateFiles(), audit);
    }

    private static List<FileWatcher> initFileWatchers(final Config config, final MoverThread moverThread, final FileChangeWatcher fileChangeWatcher, final Audit audit) {
        final List<FileWatcher> watchers = config.getWatchDir().stream().map(watch -> new FileWatcher(watch,
                asSet(config.getPassivateLocation(), config.getAuditPassivateLocation()),
                fileChangeWatcher, config.isLazyPolling(), audit)).collect(Collectors.toList());
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // finish any pending moves before shutting down vm
                watchers.forEach(FileWatcher::shutdown);
                moverThread.shutdown();
            }
        });
        return watchers;
    }

    @SuppressWarnings("unchecked")
    private static Syncer initSyncer(final Config config, final List<Mover> movers, final List<Sftp> sftps, final Audit audit) {
        final SyncedFiles syncedFiles = new SyncedFiles(Paths.get(config.getPassivateLocation()));
        final Syncer syncer = new Syncer(config.getWatchDir(), sftps, syncedFiles, config.getSyncFrequency(),
                config.shouldDepassivateEachTime(), config.getMinimumFreeSpaceForDownload(), config.getFilePermissions(),
                config.downloadsMustMatchMover(), movers, config.isLazyPolling(), (List<FileWatcher>) components.get(FileWatcher.class), audit);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // finish any pending downloads before shutting down vm
                syncer.shutdown();
            }
        });
        return syncer;
    }

    private static Set<String> asSet(final String... strings) {
        return Stream.of(strings).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
