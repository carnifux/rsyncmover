package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover.AdditionalArguments;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover.DontMatchMoverByName;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            for (Mover m : mover.getMovers().getMover()) {
                if (m.getAdditionalArguments() == null) {
                    m.setAdditionalArguments(new AdditionalArguments());
                }
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
            if (mover.getAudit() == null) {
                mover.setAudit(new RsyncMover.Audit());
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
        final Map<String, List<Mover>> moversByName = current.getMovers().getMover().stream()
                .collect(Collectors.groupingBy(Mover::getName));
        for (final Mover mover : defaultMover.getMovers().getMover()) {
            final List<Mover> movers = moversByName.get(mover.getName());
            if (movers != null) {
                for (Mover other : movers) {
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
                        if (mover.getDontMatchMoverByName() != null) {
                            for (final String s : mover.getDontMatchMoverByName().getName()) {
                                if (other.getDontMatchMoverByName() == null) {
                                    other.setDontMatchMoverByName(new DontMatchMoverByName());
                                }
                                if (!other.getDontMatchMoverByName().getName().contains(s)) {
                                    other.getDontMatchMoverByName().getName().add(s);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (final Mover mover : moversByName.values().stream().flatMap(Collection::stream).filter(m -> m.getDontMatchMoverByName() != null).collect(Collectors.toList())) {
            final Set<String> dontMatch = new HashSet<>();
            for (final String name : mover.getDontMatchMoverByName().getName()) {
                final List<Mover> movers = moversByName.get(name);
                movers.forEach(m -> {
                    if (m.getPatterns() != null) {
                        dontMatch.addAll(m.getPatterns().getPattern());
                    }
                });
            }
            if (mover.getDontMatchPatterns() == null) {
                mover.setDontMatchPatterns(new DontMatchPatterns());
            }
            dontMatch.addAll(mover.getDontMatchPatterns().getPattern());
            mover.getDontMatchPatterns().getPattern().clear();
            mover.getDontMatchPatterns().getPattern().addAll(dontMatch);
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
