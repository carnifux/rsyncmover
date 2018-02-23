package com.carnifex.rsyncmover.notifications;


import com.carnifex.rsyncmover.audit.entry.Entry;
import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.notifications.impl.Pushbullet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class Notifier {

    private static final Logger logger = LogManager.getLogger(Notifier.class);

    private static final Map<String, Notifier> cachedNotifiers = new ConcurrentHashMap<>();

    public abstract void notify(final Entry entry);

    public static Notifier create(final RsyncMover.Notification.Agent agent) {
        if (!agent.isEnabled()) {
            return null;
        }
        switch (agent.getType()) {
            case "pushbullet": {
                return cachedNotifiers.computeIfAbsent(agent.getType(), type -> {
                    final List<RsyncMover.Notification.Agent.Params.Param> params = agent.getParams().getParam();
                    final List<RsyncMover.Notification.Agent.Params.Param> apiKey = params.stream()
                            .filter(param -> param.getKey().equals("apiKey"))
                            .collect(Collectors.toList());
                    if (apiKey.size() == 1) {
                        logger.info("Created notifier of class " + Pushbullet.class + " for declared type '" + agent.getType() + "'");
                        return new Pushbullet(apiKey.get(0).getValue());
                    }
                    throw new IllegalArgumentException("Unable to create Pushbullet notifier from " + agent.toString());
                });
            }
            default:
                logger.warn("Unknown notifier type {}", agent.getType());
                return null;
        }
    }

    public static Notifier find(final String agentName) {
        return cachedNotifiers.computeIfAbsent(agentName, __ -> {
            throw new IllegalArgumentException("Unable to find notifier type " + agentName);
        });
    }

    public static void shutdown() {
        cachedNotifiers.clear();
    }
}
