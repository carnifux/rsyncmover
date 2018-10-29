package com.carnifex.rsyncmover;


import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.config.Config;
import com.carnifex.rsyncmover.config.ConfigLoader;
import com.carnifex.rsyncmover.config.ConfigWatcher;
import com.carnifex.rsyncmover.email.Emailer;
import com.carnifex.rsyncmover.mover.io.FileChangeWatcher;
import com.carnifex.rsyncmover.mover.io.FileWatcher;
import com.carnifex.rsyncmover.mover.io.Mover;
import com.carnifex.rsyncmover.mover.io.MoverThread;
import com.carnifex.rsyncmover.notifications.Notifier;
import com.carnifex.rsyncmover.sync.Sftp;
import com.carnifex.rsyncmover.sync.SyncedFiles;
import com.carnifex.rsyncmover.sync.Syncer;
import com.carnifex.rsyncmover.web.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RsyncMover {

    private static final Logger logger = LogManager.getLogger();
    private static final Map<Class<?>, Object> components = new ConcurrentHashMap<>();
    private static Config currentConfig;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        final String configPath = args != null && args.length > 0 ? args[0] : "config.xml";
        final Config config = new Config(new ConfigLoader().load(Paths.get(configPath)));
        try {
            init(config);
        } catch (Exception e) {
            logger.error("", e);
            System.exit(1);
        }
        if (!config.isRunOnce()) {
            final ConfigWatcher configWatcher = new ConfigWatcher(configPath);
            components.put(ConfigWatcher.class, configWatcher);
        }
        if (config.isRunOnce()) {
            logger.info("Running once.");
            if (config.downloadFiles()) {
                final Syncer syncer = (Syncer) components.get(Syncer.class);
                syncer.sync();
            }
            if (config.moveFiles()) {
                final FileChangeWatcher fileChangeWatcher = (FileChangeWatcher) components.get(FileChangeWatcher.class);
                fileChangeWatcher.shutdown();
            }
            shutdownAll(config);
            ((List<Emailer>) components.get(Emailer.class)).forEach(emailer -> emailer.send());
            final Audit audit = (Audit) components.get(Audit.class);
            audit.shutdown();
            logger.info("Run completed, shutting down");
        }
    }

    public static synchronized void init(final Config config) {
        final Audit audit = new Audit(config.shouldPassivateAudit(), config.getAuditPassivateLocation(), (Audit) components.get(Audit.class));
        components.put(Audit.class, audit);
        // don't add to threads in components, as audit doesn't get shut down
        Runtime.getRuntime().addShutdownHook(new Thread(audit::shutdown));

        final List<Emailer> emailers = config.getEmailSendTime().stream()
                .map(time -> new Emailer(config.getEmail().isEmailReport(), config.getEmail().getTo(), config.getEmail().getFrom(), time, audit))
                .collect(Collectors.toList());
        components.putIfAbsent(Emailer.class, emailers);
        config.getAgents().forEach(Notifier::create);
        final List<Mover> movers = config.getMovers().stream().map(m -> new Mover(m, audit)).collect(Collectors.toList());

        final Lock simultaneousLock = config.isAllowSimultaneousTasks() ? null : new ReentrantLock();

        if (config.moveFiles()) {
            final MoverThread moverThread = initMoverThread(config, simultaneousLock, audit);
            final String moverPassivateLocation = config.getMoverPassivateLocation();
            final SyncedFiles syncedFiles = new SyncedFiles(moverPassivateLocation != null ? Paths.get(moverPassivateLocation) : null);
            final FileChangeWatcher fileChangeWatcher = new FileChangeWatcher(movers, moverThread, syncedFiles, audit);
            final List<FileWatcher> fileWatchers = initFileWatchers(config, moverThread, fileChangeWatcher, audit);
            if (fileWatchers.stream().noneMatch(FileWatcher::isActive)) {
                throw new IllegalArgumentException("No file watchers were able to be initialised");
            }
            components.putIfAbsent(FileChangeWatcher.class, fileChangeWatcher);
            components.putIfAbsent(MoverThread.class, moverThread);
            components.putIfAbsent(FileWatcher.class, fileWatchers);
            logger.info("File moving successfully initiated");
        } else {
            logger.warn("Not moving files as configured not to.");
        }

        if (config.downloadFiles()) {
            final List<Sftp> sftps = initSshs(config, simultaneousLock);
            final MoverThread moverThread = config.moveFiles() ? (MoverThread) components.get(MoverThread.class) : null;
            final Syncer syncer = initSyncer(config, movers, sftps, moverThread, audit);
            components.putIfAbsent(Sftp.class, sftps);
            components.putIfAbsent(Syncer.class, syncer);
            logger.info("File downloading successfully initiated");
        } else {
            logger.warn("Not downloading as configured not to.");
        }

        if (!config.isRunOnce()) {
            emailers.forEach(Thread::start);
        }

        if (config.runServer()) {
            final Server server = new Server(config.getPort(), (Syncer) components.get(Syncer.class), movers, audit);
            components.putIfAbsent(Server.class, server);
        }


        currentConfig = config;
    }

    private static List<Sftp> initSshs(final Config config, final Lock simultaneousLock) {
        return config.getServers().stream()
            .flatMap(server -> server.getDirectories().stream()
                    .map(dir -> dir.getDirectory())
                    .map(dir -> {
                        // handle nulls
                        final List<Notifier> notifiers = Boolean.TRUE.equals(server.isNotify()) ?
                                server.getAgents().getAgent().stream().map(Notifier::find).collect(Collectors.toList()) : Collections.emptyList();
                        return new Sftp(server.getHost(), server.getPort(),
                                dir.getDirectory(), dir.getRealDirectory(), server.getUser(), server.getPass(),
                                server.getHostKey(), config.getFilePermissions(), config.getMaxDownloadSpeedBytes(), notifiers,
                                simultaneousLock);
                    }))
            .collect(Collectors.toList());
    }


    public static synchronized void reinit(final String configPath) {
        shutdownAll(currentConfig);
        init(new Config(new ConfigLoader().load(configPath)));
    }

    @SuppressWarnings("unchecked")
    private static void shutdownAll(final Config config) {
        if (!config.isRunOnce()) {
            final List<Emailer> emailer = (List<Emailer>) components.remove(Emailer.class);
            if (emailer != null) {
                emailer.forEach(Thread::interrupt);
            }
        }
        // shut down movers first so any current downloads arent moved with old movers
        if (components.containsKey(MoverThread.class)) {
            final MoverThread moverThread = (MoverThread) components.remove(MoverThread.class);
            if (moverThread != null) {
                moverThread.shutdown(!config.isRunOnce());
            }
            final List<FileWatcher> fileWatchers = (List<FileWatcher>) components.remove(FileWatcher.class);
            if (fileWatchers != null) {
                fileWatchers.forEach(FileWatcher::shutdown);
            }
            final FileChangeWatcher fileChangeWatcher = (FileChangeWatcher) components.remove(FileChangeWatcher.class);
            if (fileChangeWatcher != null) {
                fileChangeWatcher.interrupt();
            }
        }
        if (components.containsKey(Syncer.class)) {
            components.remove(Sftp.class);
            final Syncer syncer = (Syncer) components.remove(Syncer.class);
            if (syncer != null) {
                if (config.killDownloadOnExit()) {
                    syncer.forceShutdown();
                }
                syncer.shutdown();
            }

        }
        if (components.containsKey(Server.class)) {
            final Server server = (Server) components.remove(Server.class);
            server.shutdown();
        }
        if (components.containsKey(Audit.class)) {
            final Audit audit = (Audit) components.get(Audit.class);
            audit.resetTransients();
        }
        Notifier.shutdown();
        // should contain audit, config watcher, and a list of shutdown hooks (thread)
        if (components.size() != 3 && !config.isRunOnce()) {
            if (components.size() != 2 || !components.keySet().equals(new HashSet<>(Arrays.asList(Audit.class, ConfigWatcher.class)))) {
                logger.fatal("Did not remove all components - shutdown not successful.");
                logger.fatal("Remaining components: ");
                components.keySet().forEach(key -> logger.fatal(key));
                throw new RuntimeException();
            }
        }
        ((Set<Thread>) components.computeIfAbsent(Thread.class, ignore -> new HashSet<>()))
                .forEach(thread -> Runtime.getRuntime().removeShutdownHook(thread));
        components.remove(Thread.class);
    }

    private static MoverThread initMoverThread(final Config config, final Lock simultaneousLock, final Audit audit) {
        return new MoverThread(config.getFilePermissions(), config.getFolderPermissions(), config.getUserPrincipal(), config.getDeleteDuplicateFiles(),
                simultaneousLock, audit);
    }

    @SuppressWarnings("unchecked")
    private static List<FileWatcher> initFileWatchers(final Config config, final MoverThread moverThread, final FileChangeWatcher fileChangeWatcher, final Audit audit) {
        final List<FileWatcher> watchers = config.getWatchDir().stream().map(watch -> new FileWatcher(watch,
                asSet(config.getPassivateLocation(), config.getAuditPassivateLocation()),
                fileChangeWatcher, config.isLazyPolling(), audit)).collect(Collectors.toList());
        final Thread hook = new Thread(() -> {
            // finish any pending moves before shutting down vm
            watchers.forEach(FileWatcher::shutdown);
            moverThread.shutdown(true);
        });
        ((Set<Thread>) components.computeIfAbsent(Thread.class, ignore -> new HashSet<>())).add(hook);
        return watchers;
    }

    @SuppressWarnings("unchecked")
    private static Syncer initSyncer(final Config config, final List<Mover> movers, final List<Sftp> sftps,
                                     final MoverThread moverThread, final Audit audit) {
        final SyncedFiles syncedFiles = new SyncedFiles(Paths.get(config.getPassivateLocation()));
        final Syncer syncer = new Syncer(config.getWatchDir(), sftps, syncedFiles, config.getSyncFrequency(),
                config.shouldDepassivateEachTime(), config.getMinimumFreeSpaceForDownload(), config.getFilePermissions(),
                config.downloadsMustMatchMover(), movers, config.isLazyPolling(),
                config.maxConcurrentDownloads(), config.isRunOnce(), (List<FileWatcher>) components.get(FileWatcher.class),
                moverThread, audit);
        // finish any pending downloads before shutting down vm
        final Thread hook = new Thread(syncer::shutdown);
        Runtime.getRuntime().addShutdownHook(hook);
        ((Set<Thread>) components.computeIfAbsent(Thread.class, ignore -> new HashSet<>())).add(hook);
        return syncer;
    }

    private static Set<String> asSet(final String... strings) {
        return Stream.of(strings).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
