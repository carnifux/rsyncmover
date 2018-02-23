package com.carnifex.rsyncmover.notifications.impl;

import com.carnifex.rsyncmover.audit.entry.Entry;
import com.carnifex.rsyncmover.notifications.Notifier;
import com.github.sheigutn.pushbullet.items.push.sendable.defaults.SendableNotePush;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Pushbullet extends Notifier {

    private final Logger logger = LogManager.getLogger(getClass());
    private final com.github.sheigutn.pushbullet.Pushbullet client;

    public Pushbullet(final String apiKey) {
        this.client = new com.github.sheigutn.pushbullet.Pushbullet(apiKey);
    }

    @Override
    public void notify(final Entry entry) {
        client.push(new SendableNotePush(entry.getType().name(), entry.format()));
        logger.info("Pushed entry {} to pushbullet", entry.format().replace("\n", " "));
    }
}
