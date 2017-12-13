package com.carnifex.rsyncmover.sync;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;


public class SftpTest {

    @Test
    public void testFormatSize() {
        final Sftp sftp = new Sftp("", 10, "/", "/", "", "", "", Collections.emptySet(), -1);
        assertEquals("1B", sftp.formatSize(1));
        assertEquals("1000B", sftp.formatSize(1000));
        assertEquals("100000B", sftp.formatSize(100000));
        assertEquals("1MB", sftp.formatSize(1_000_000));
        assertEquals("100MB", sftp.formatSize(100_000_000));
        assertEquals("1GB", sftp.formatSize(1_000_000_000));
        assertEquals("100GB", sftp.formatSize(100_000_000_000L));
        assertEquals("1000GB", sftp.formatSize(1_000_000_000_000L));
    }
}