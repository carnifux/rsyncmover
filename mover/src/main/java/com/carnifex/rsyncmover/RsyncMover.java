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
import com.carnifex.rsyncmover.sync.Ssh;
import com.carnifex.rsyncmover.sync.SyncedFiles;
import com.carnifex.rsyncmover.sync.Syncer;
import com.carnifex.rsyncmover.web.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RsyncMover {

    private static final Logger logger = LogManager.getLogger();
    private static final Map<Class<?>, Object> components = new ConcurrentHashMap<>();


    public static void main(String[] args) {
        // audit will not be reinitialised, so init here
        final Audit audit = new Audit();
        components.put(Audit.class, audit);
        final String configPath = args != null && args.length > 0 ? args[0] : "config.xml";
        init(configPath);
        final ConfigWatcher configWatcher = new ConfigWatcher(configPath);
        components.put(ConfigWatcher.class, configWatcher);
    }

    public static synchronized void init(final String configPath) {
        final Config config = new Config(new ConfigLoader().load(configPath));
        final Audit audit = (Audit) components.get(Audit.class);
        final List<Emailer> emailers = config.getEmailSendTime().stream()
                .map(time -> new Emailer(config.getEmail().isEmailReport(), config.getEmail().getTo(), config.getEmail().getFrom(), time, audit))
                .collect(Collectors.toList());
        components.putIfAbsent(Emailer.class, emailers);
        final List<Mover> movers = config.getMovers().stream().map(Mover::new).collect(Collectors.toList());

        if (config.moveFiles()) {
            final MoverThread moverThread = initMoverThread(config, audit);
            final FileChangeWatcher fileChangeWatcher = initFileChangeWatcher(movers, moverThread);
            final List<FileWatcher> fileWatchers = initFileWatchers(config, moverThread, fileChangeWatcher);
            components.putIfAbsent(FileChangeWatcher.class, fileChangeWatcher);
            components.putIfAbsent(MoverThread.class, moverThread);
            components.putIfAbsent(FileWatcher.class, fileWatchers);
            logger.info("File moving successfully initiated");
        }
        if (config.downloadFiles()) {
            final List<Ssh> sshs = initSshs(config);
            final Syncer syncer = initSyncer(config, movers, sshs, audit);
            components.putIfAbsent(Ssh.class, sshs);
            components.putIfAbsent(Syncer.class, syncer);
            logger.info("File downloading successfully initiated");
        }

        if (!config.moveFiles() && !config.downloadFiles()) {
            logger.info("No operations configured, shutting down");
            return;
        }

        emailers.forEach(Thread::start);

        if (config.runServer()) {
            final Server server = new Server(config.getPort(), audit);
            components.putIfAbsent(Server.class, server);
        }
    }

    private static List<Ssh> initSshs(final Config config) {
        return config.getServers().stream()
            .flatMap(server -> server.getDirectories().getDirectory().stream()
                    .map(dir -> new Ssh(server.getHost(), server.getPort(),
                            dir.getDirectory(), dir.getRealDirectory(), server.getUser(), server.getPass(),
                            server.getHostKey(), config.getFilePermissions())))
            .collect(Collectors.toList());
    }

    private static FileChangeWatcher initFileChangeWatcher(final List<Mover> movers, final MoverThread moverThread) {
        return new FileChangeWatcher(movers, moverThread);
    }

    public static synchronized void reinit(final String configPath) {
        shutdownAll();
        init(configPath);
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
            final List<Ssh> sshs = (List<Ssh>) components.remove(Ssh.class);
            final Syncer syncer = (Syncer) components.remove(Syncer.class);
            syncer.shutdown();
        }
        if (components.containsKey(Server.class)) {
            final Server server = (Server) components.remove(Server.class);
            server.shutdown();
            server.interrupt();
        }
        // should contain audit and config watcher
        if (components.size() != 2) {
            throw new RuntimeException("Did not remove all components - shutdown not successful.");
        }
    }

    private static MoverThread initMoverThread(final Config config, final Audit audit) {
        return new MoverThread(config.getFilePermissions(), config.getDeleteDuplicateFiles(), audit);
    }

    private static List<FileWatcher> initFileWatchers(final Config config, final MoverThread moverThread, final FileChangeWatcher fileChangeWatcher) {
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
        return watchers;
    }

    private static Syncer initSyncer(final Config config, final List<Mover> movers, final List<Ssh> sshs, final Audit audit) {
        final SyncedFiles syncedFiles = new SyncedFiles(Paths.get(config.getPassivateLocation()));
        final Syncer syncer = new Syncer(config.getWatchDir(), sshs, syncedFiles, config.getSyncFrequency(),
                config.shouldDepassivateEachTime(), config.getMinimumFreeSpaceForDownload(), config.getFilePermissions(),
                config.downloadsMustMatchMover(), movers, audit);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // finish any pending downloads before shutting down vm
                syncer.shutdown();
            }
        });
        return syncer;
    }

}
