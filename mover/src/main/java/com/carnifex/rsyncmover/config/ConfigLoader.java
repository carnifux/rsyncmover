package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover.*;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover.MoveOperators.MoveOperator;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover.MoveOperators.MoveOperator.AdditionalArguments;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    public RsyncMover load(final Path configPath) {
        try {
            // load user config first
            final RsyncMover mover = loadFromFile(configPath);
            return load(mover);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public RsyncMover load(final String config) {
        try {
            final RsyncMover mover = loadFromString(config);
            return load(mover);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RsyncMover load(final RsyncMover mover) throws JAXBException, SAXException, MalformedURLException {
        // make config null safe
        if (mover.getMovers() == null) {
            mover.setMovers(new RsyncMover.Movers());
        }
        for (final Mover m : mover.getMovers().getMover()) {
            if ((m.getMoveOperator() != null || m.getAdditionalArguments() != null)
                    && (m.getMoveOperators() != null && m.getMoveOperators() != null &&
                    !m.getMoveOperators().getMoveOperator().isEmpty())) {
                throw new IllegalArgumentException("Cannot have both a basic move operator and the extended list");
            }
            if (m.getMoveOperator() == null && m.getMoveOperators() == null) {
                m.setMoveOperator(com.carnifex.rsyncmover.mover.operators.MoveOperator.DEFAULT_OPERATOR);
            }
            if (m.getMoveOperator() != null) {
                final MoveOperators moveOperators = new MoveOperators();
                final String[] split = m.getMoveOperator().split("\\+");
                for (final String s : split) {
                    final MoveOperator op = new MoveOperator();
                    op.setOperator(s);
                    op.setAdditionalArguments(new AdditionalArguments());
                    if (m.getAdditionalArguments() != null) {
                        op.getAdditionalArguments().getArg().addAll(m.getAdditionalArguments().getArg());
                    }
                    moveOperators.getMoveOperator().add(op);
                }
                m.setMoveOperators(moveOperators);
            }
            m.getMoveOperators().getMoveOperator().forEach(op -> {
                if (op.getAdditionalArguments() == null) {
                    op.setAdditionalArguments(new AdditionalArguments());
                }
            });
            if (m.getPatterns() == null) {
                m.setPatterns(new Patterns());
            }
            if (m.getDontMatchPatterns() == null) {
                m.setDontMatchPatterns(new DontMatchPatterns());
            }
            if (m.isNotify() == null) {
                m.setNotify(false);
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
        if (mover.getNotification() == null) {
            mover.setNotification(new RsyncMover.Notification());
        }
        for (final Mover m : mover.getMovers().getMover()) {
            if (m.getDontMatchMoverByName() != null && !m.getDontMatchMoverByName().getName().isEmpty()) {
                final List<Mover> moversNotToMatch = mover.getMovers().getMover().stream()
                        .filter(m_ -> m.getDontMatchMoverByName().getName().contains(m_.getName())).collect(Collectors.toList());
                moversNotToMatch.forEach(m_ -> {
                    if (m_.getDontMatchPatterns() != null) {
                        m_.getDontMatchPatterns().getPattern().forEach(p -> {
                            if (!m.getPatterns().getPattern().contains(p)) {
                                m.getPatterns().getPattern().add(p);
                            }
                        });
                    }
                    if (m_.getPatterns() != null) {
                        m_.getPatterns().getPattern().forEach(p -> {
                            if (!m.getDontMatchPatterns().getPattern().contains(p)) {
                                m.getDontMatchPatterns().getPattern().add(p);
                            }
                        });
                    }
                });
            }
        }
        if (Boolean.TRUE.equals(mover.getMovers().isUseDefaultMatching())) {
            updateMover(mover, loadBase());
        }
        return mover;
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

    private RsyncMover loadBase() throws JAXBException, MalformedURLException, SAXException {
        logger.info("Loading default config from resources");
        final Unmarshaller unmarshaller = getUnmarshaller();
        return (RsyncMover) unmarshaller.unmarshal(ConfigLoader.class.getResourceAsStream(defaultConfigPath));
    }

    private RsyncMover loadFromFile(final Path path) throws JAXBException, IOException, SAXException {
        return loadFromString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    private RsyncMover loadFromString(final String string) throws JAXBException, SAXException, MalformedURLException {
        logger.info("Loading provided config file");
        try {
            final Unmarshaller unmarshaller = getUnmarshaller();
            unmarshaller.setSchema(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(getClass().getResource("/beans.xsd")));
            unmarshaller.unmarshal(new StringReader(string));
        } catch (Exception e) {
            logger.error("Config validation failed", e);
            logger.warn("Loading potentially invalid config");
        }
        final Unmarshaller unmarshaller = getUnmarshaller();
        return (RsyncMover) unmarshaller.unmarshal(new StringReader(string));
    }

    private Unmarshaller getUnmarshaller() throws JAXBException, MalformedURLException, SAXException {
        final JAXBContext context = JAXBContext.newInstance(RsyncMover.class);
        return context.createUnmarshaller();
    }
}
