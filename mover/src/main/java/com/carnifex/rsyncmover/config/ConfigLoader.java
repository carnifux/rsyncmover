package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover.DontMatchPatterns;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover.Extensions;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover.Patterns;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConfigLoader {

    private static final Logger logger = LogManager.getLogger();
    private static final String defaultConfigPath = "/config.xml";

    public RsyncMover load(final String configPath) {
        try {
            final Path path = Paths.get(configPath);
            // load user config first
            final RsyncMover mover = load0(path);
            // make config null safe
            if (mover.getMovers() == null) {
                mover.setMovers(new RsyncMover.Movers());
            }
            if (mover.getServers() == null) {
                mover.setServers(new RsyncMover.Servers());
            }
            if (mover.getEmailSummary() == null) {
                mover.setEmailSummary(new RsyncMover.EmailSummary());
            }
            if (mover.getWebServer() == null) {
                mover.setWebServer(new RsyncMover.WebServer());
            }
            if (mover.getMovers().isUseDefaultMatching()) {
                updateMover(mover, loadBase());
            }
            return mover;
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateMover(final RsyncMover current, final RsyncMover defaultMover) {
        final Map<String, Mover> moversByName = current.getMovers().getMover().stream()
                .collect(Collectors.toMap(Mover::getName, Function.identity()));
        for (final Mover mover : defaultMover.getMovers().getMover()) {
            final Mover other = moversByName.get(mover.getName());
            if (other != null) {
                other.setPartialMatch(mover.isPartialMatch());
                if (mover.getPatterns() != null) {
                    for (final String s : mover.getPatterns().getPattern()) {
                        if (other.getPatterns() == null) {
                            other.setPatterns(new Patterns());
                        }
                        if (!other.getPatterns().getPattern().contains(s)) {
                            other.getPatterns().getPattern().add(s);
                        }
                    }
                }
                if (mover.getExtensions() != null) {
                    for (final String s : mover.getExtensions().getExtension()) {
                        if (other.getExtensions() == null) {
                            other.setExtensions(new Extensions());
                        }
                        if (!other.getExtensions().getExtension().contains(s)) {
                            other.getExtensions().getExtension().add(s);
                        }
                    }
                }
                if (mover.getDontMatchPatterns() != null) {
                    for (final String s : mover.getDontMatchPatterns().getPattern()) {
                        if (other.getDontMatchPatterns() == null) {
                            other.setDontMatchPatterns(new DontMatchPatterns());
                        }
                        if (!other.getDontMatchPatterns().getPattern().contains(s)) {
                            other.getDontMatchPatterns().getPattern().add(s);
                        }
                    }
                }
            }
        }
    }

    private RsyncMover loadBase() throws JAXBException {
        logger.info("Loading default config from resources");
        final Unmarshaller unmarshaller = getUnmarshaller();
        return (RsyncMover) unmarshaller.unmarshal(ConfigLoader.class.getResourceAsStream(defaultConfigPath));
    }

    private RsyncMover load0(final Path path) throws JAXBException {
        logger.info("Loading provided config file");
        final Unmarshaller unmarshaller = getUnmarshaller();
        return (RsyncMover) unmarshaller.unmarshal(path.toFile());
    }

    private Unmarshaller getUnmarshaller() throws JAXBException {
        final JAXBContext context = JAXBContext.newInstance(RsyncMover.class);
        return context.createUnmarshaller();
    }
}
