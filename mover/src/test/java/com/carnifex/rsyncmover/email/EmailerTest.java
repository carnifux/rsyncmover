package com.carnifex.rsyncmover.email;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalTime;


public class EmailerTest {

    @Test
    public void nextEmailTime_inOneMinute() throws Exception {
        final Emailer emailer = new Emailer(false, null, null, LocalTime.now().plusMinutes(1), null);
        final long next = emailer.nextEmailTime() / 1000L / 60L;
        Assert.assertTrue(next > 0);
    }

    @Test
    public void nextEmailTime_now() throws Exception {
        final Emailer emailer = new Emailer(false, null, null, LocalTime.now(), null);
        final long next = emailer.nextEmailTime() / 1000L / 60L;
        Assert.assertTrue(next > 0);
    }

    @Test
    public void nextEmailTime_oneMinuteAgo() throws Exception {
        final Emailer emailer = new Emailer(false, null, null, LocalTime.now().minusMinutes(1), null);
        final long next = emailer.nextEmailTime() / 1000L / 60L;
        Assert.assertTrue(next > 0);
    }

    @Test
    public void nextEmailTime_inAnHour() throws Exception {
        final Emailer emailer = new Emailer(false, null, null, LocalTime.now().plusHours(1), null);
        final long next = emailer.nextEmailTime() / 1000L / 60L;
        Assert.assertTrue(next > 0);
    }
}