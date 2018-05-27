package com.carnifex.rsyncmover.audit.entry;

import com.carnifex.rsyncmover.Utilities;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;


public class MovedEntryTest {

    private static final Logger logger = LoggerFactory.getLogger(MovedEntryTest.class);

    @Test
    public void format_normal() throws Exception {
        if (!Utilities.isRunningOnWindows()) {
            logger.warn("Not running on windows, skipping test");
            return;
        }
        final MovedEntry entry = new MovedEntry("D:\\dl\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "D:\\tv\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "move");
        assertEquals("move: Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv -> \\tv", entry.format());
    }

    @Test
    public void format_normal_nested() throws Exception {
        if (!Utilities.isRunningOnWindows()) {
            logger.warn("Not running on windows, skipping test");
            return;
        }
        final MovedEntry entry = new MovedEntry("D:\\test\\dl\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "D:\\test\\tv\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "move");
        assertEquals("move: Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv -> \\tv", entry.format());
    }

    @Test
    public void format_filebot_folders() throws Exception {
        if (!Utilities.isRunningOnWindows()) {
            logger.warn("Not running on windows, skipping test");
            return;
        }
        final MovedEntry entry = new MovedEntry("D:\\dl\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "D:\\tv\\Silicon Valley\\Season 3\\Silicon Valley - 3x01 - Founder Friendly.mkv", "filebot+move");
        assertEquals("filebot+move: Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv -> \\tv\\Silicon Valley\\Season 3\\Silicon Valley - 3x01 - Founder Friendly.mkv", entry.format());
    }

    @Test
    @Ignore
    public void format_music() throws Exception {
        final MovedEntry move = new MovedEntry("\\dl\\folder", "\\music\\_unsorted\\new2016\\folder", "move");
        assertEquals("_unsorted\\new2016", move.format());
    }
}