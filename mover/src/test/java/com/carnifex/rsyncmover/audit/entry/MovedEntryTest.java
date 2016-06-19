package com.carnifex.rsyncmover.audit.entry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class MovedEntryTest {

    @Test
    public void format_normal() throws Exception {
        final MovedEntry entry = new MovedEntry("D:\\dl\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "D:\\tv\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "move");
        assertEquals("move: Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv -> \\tv", entry.format());
    }

    @Test
    public void format_normal_nested() throws Exception {
        final MovedEntry entry = new MovedEntry("D:\\test\\dl\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "D:\\test\\tv\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "move");
        assertEquals("move: Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv -> \\tv", entry.format());
    }

    @Test
    public void format_filebot_folders() throws Exception {
        final MovedEntry entry = new MovedEntry("D:\\dl\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv", "D:\\tv\\Silicon Valley\\Season 3\\Silicon Valley - 3x01 - Founder Friendly.mkv", "filebot+move");
        assertEquals("filebot+move: Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv -> \\tv\\Silicon Valley\\Season 3\\Silicon Valley - 3x01 - Founder Friendly.mkv", entry.format());
    }
}