package com.carnifex.rsyncmover.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TotalDownloaded {

    private static final Logger logger = LoggerFactory.getLogger(TotalDownloaded.class);
    private transient BigInteger bytesDownloaded;
    private final Path persistLocation;
    private final transient Thread updateThread;
    private final BlockingQueue<BigInteger> queue = new LinkedBlockingQueue<>();
    private final transient Thread additionThread;

    public TotalDownloaded(final String persistLocation) {
        if (persistLocation == null) {
            logger.info("Not creating total downloaded tracker");
            this.persistLocation = null;
            this.updateThread = null;
            this.additionThread = null;
        } else {
            this.persistLocation = Paths.get(persistLocation);
            if (!Files.exists(this.persistLocation)) {
                try {
                    Files.createFile(this.persistLocation);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.updateThread = new Thread(this::update);
            this.updateThread.start();
            this.additionThread = new Thread(this::increment0);
            this.additionThread.start();
        }
    }

    private void update() {
        for (;;) {
            try {
                if (bytesDownloaded == null) {
                    bytesDownloaded = read();
                }

                Thread.sleep(5 * 60 * 1000); // 5 mins
                write();
            } catch (final InterruptedException e) {
                logger.error(e.getMessage(), e);
                break;
            } catch (final Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private void increment0() {
        for (;;) {
            try {
                final BigInteger val = queue.take();
                this.bytesDownloaded = this.bytesDownloaded.add(val);
            } catch (final InterruptedException e) {
                logger.error(e.getMessage(), e);
                break;
            }
        }
    }

    public void increment(final BigInteger bytes) {
        queue.add(bytes);
    }

    public BigInteger get() {
        return this.bytesDownloaded;
    }

    private BigInteger read() throws IOException {
        return Files.lines(this.persistLocation)
                .limit(1)
                .findFirst()
                .map(BigInteger::new)
                .orElse(BigInteger.ZERO);
    }

    private void write() throws IOException {
        Files.write(this.persistLocation, this.bytesDownloaded.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void shutdown() {
        if (this.persistLocation == null) {
            return;
        }
        this.updateThread.interrupt();
        this.additionThread.interrupt();
        try {
            write();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
