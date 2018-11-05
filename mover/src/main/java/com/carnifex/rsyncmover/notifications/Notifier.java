package com.carnifex.rsyncmover.notifications;


import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.carnifex.rsyncmover.audit.Type;
import com.carnifex.rsyncmover.audit.entry.Entry;
import com.carnifex.rsyncmover.beans.RsyncMover.Notification.Agent;
import com.carnifex.rsyncmover.beans.RsyncMover.Notification.Agent.Params.Param;
import com.carnifex.rsyncmover.notifications.impl.Pushbullet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Notifier {

    private static final Logger logger = LogManager.getLogger(Notifier.class);

    private static final Map<String, Notifier> cachedNotifiers = new ConcurrentHashMap<>();

    private final List<Type> supportedTypes;

    protected Notifier(final List<Type> supportedTypes) {
        this.supportedTypes = supportedTypes;
    }

    protected abstract void notifyInternal(final Entry entry);

    public void notify(final Entry entry) {
        if (supportedTypes.contains(entry.getType())) {
            notifyInternal(entry);
        }
    }

    public static void notifiyAll(final Entry entry) {
        cachedNotifiers.values().forEach(n -> n.notify(entry));
    }

    public static Notifier create(final Agent agent) {
        if (!agent.isEnabled()) {
            return null;
        }
        final List<Type> types;
        if (agent.getNotifyOnTypes() != null && !agent.getNotifyOnTypes().getType().isEmpty()) {
            types = agent.getNotifyOnTypes().getType().stream().map(Type::valueOf).collect(Collectors.toList());
        } else {
            types = Arrays.asList(Type.values());
        }
        switch (agent.getType()) {
            case "pushbullet": {
                return cachedNotifiers.computeIfAbsent(agent.getName(), type -> {
                    final List<Param> params = agent.getParams().getParam();
                    final List<Param> apiKey = params.stream()
                            .filter(param -> param.getKey().equals("apiKey"))
                            .collect(Collectors.toList());
                    if (apiKey.size() == 1) {
                        logger.info("Created notifier of class " + Pushbullet.class + " for declared type '" + agent.getType() + "', " +
                                "notifying on types " + types.stream().map(Type::name).collect(Collectors.joining(", ")));
                        return new Pushbullet(types, apiKey.get(0).getValue());
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
