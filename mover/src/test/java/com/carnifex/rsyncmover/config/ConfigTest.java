package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigTest {

    @Test
    public void testFreeSpace_Simple() throws Exception {
        final Config c = createFreeSpaceConfig("1000");
        assertEquals(1000L, c.getMinimumFreeSpaceForDownload());
    }

    @Test
    public void testFreeSpace_KMGT() throws Exception {
        assertEquals(2L * 1024L, createFreeSpaceConfig("2k").getMinimumFreeSpaceForDownload());
        assertEquals(2L * 1024L * 1024L, createFreeSpaceConfig("2m").getMinimumFreeSpaceForDownload());
        assertEquals(2L * 1024L * 1024L * 1024L, createFreeSpaceConfig("2G").getMinimumFreeSpaceForDownload());
        assertEquals(2L * 1024L * 1024L * 1024L * 1024L, createFreeSpaceConfig("2t").getMinimumFreeSpaceForDownload());
    }
    
    @Test
    public void testFreeSpace_decimals() throws Exception {
        assertEquals((long) (1.5 * 1024L), createFreeSpaceConfig("1.5k").getMinimumFreeSpaceForDownload());
    }

    @Test
    public void testFieldName() {
        assertEquals("test", createFreeSpaceConfig("").findFieldName("isTest"));
        assertEquals("test", createFreeSpaceConfig("").findFieldName("getTest"));
    }


    private Config createFreeSpaceConfig(final String s) {
        final RsyncMover rsyncMover = new RsyncMover();
        rsyncMover.setServers(new RsyncMover.Servers());
        rsyncMover.getServers().setMinimumFreeSpaceToDownload(s);
        return new Config(rsyncMover);
    }
}