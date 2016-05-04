package com.carnifex.rsyncmover.audit;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;


public class AuditTest {

    @Test
    public void upTime_short() throws Exception {
        final Audit audit = new Audit();
        final String uptime = audit.makeUptime(LocalDateTime.now().minusHours(1));
        Assert.assertEquals("Uptime: 0 days 1 hour 0 minutes<br /><br />", uptime);
    }

    @Test
    public void upTime_long() throws Exception {
        final Audit audit = new Audit();
        final String uptime = audit.makeUptime(LocalDateTime.now().minusMinutes(57).minusHours(17).minusDays(14).minusMonths(4).minusYears(1));
        Assert.assertEquals("Uptime: 1 year 4 months 14 days 17 hours 57 minutes<br /><br />", uptime);
    }
}