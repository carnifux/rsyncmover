package com.carnifex.rsyncmover.mover.operators;

import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;


public class FileBotTest {

    @Test
    public void findNewPath() throws Exception {
        final List<String> output = Arrays.asList("Rename episodes using [TheTVDB]",
                "Auto-detected query: [Silicon Valley]", "Fetching episode data for [Silicon Valley]",
                "Fetching episode data for [Silicon Valley Rebels]", "Fetching episode data for [Start-ups: Silicon Valley]",
                "[MOVE] Rename [D:\\tv\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv] to [D:\\tv\\Silicon Valley - 3x01 - Founder Friendly.mkv]",
                "Processed 0 files", "Failure (�_�)", "");
        final FileBot filebot = (FileBot) MoveOperator.create("filebot", Collections.emptyList(), null);
        final Path newPath = filebot.findNewPath(output).get();
        assertEquals("D:\\tv\\Silicon Valley - 3x01 - Founder Friendly.mkv", newPath.toString());
    }


    @Test
    public void findNewPath_folders() throws Exception {
        final List<String> output = Arrays.asList("Rename episodes using [TheTVDB]",
                "Auto-detected query: [Silicon Valley]", "Fetching episode data for [Silicon Valley]",
                "Fetching episode data for [Silicon Valley Rebels]", "Fetching episode data for [Start-ups: Silicon Valley]",
                "[MOVE] Rename [D:\\tv\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv] to [D:\\tv\\Silicon Valley\\Season 3\\Silicon Valley - 3x01 - Founder Friendly.mkv]",
                "Processed 0 files", "Failure (�_�)", "");
        final FileBot filebot = (FileBot) MoveOperator.create("filebot", Collections.emptyList(), null);
        final Path newPath = filebot.findNewPath(output).get();
        assertEquals("D:\\tv\\Silicon Valley\\Season 3\\Silicon Valley - 3x01 - Founder Friendly.mkv", newPath.toString());
    }

    @Test
    public void findNewPath_moveFolder() throws Exception {
        final FileBot fileBot = (FileBot) MoveOperator.create("filebot", Collections.emptyList(), null);
        final Optional<Path> newPath = fileBot.findNewPath(Arrays.asList("[MOVE] Rename [" + File.separator + "tv" + File.separator + "The.Simpsons.S12.DVDRip.x264-CtrlSD" + File.separator + "" +
                "The.Simpsons.S12E21.Simpsons.Tall.Tales.DVDrip.x264-CtrlSD.mkv] to " +
                "[" + File.separator + "tv" + File.separator + "The.Simpsons.S12.DVDRip.x264-CtrlSD" + File.separator + ".." + File.separator + "The Simpsons" + File.separator + "Season 12" + File.separator + "The" +
                " Simpsons - S12E21 - Simpsons Tall Tales.mkv]"));
        assertEquals("" + File.separator + "tv" + File.separator + "The Simpsons" + File.separator + "Season 12", newPath.get().toString());
    }

    @Test
    public void testAdditionalArgumentsHandling_noChange() throws Exception {
        final FileBot filebot = (FileBot) MoveOperator.create("filebot", Arrays.asList("filebot", "-non-strict", "--format", "{n}"), null);
        assertEquals(Arrays.asList("-non-strict", "--format", "{n}"), filebot.additionalArguments);
    }

    @Test
    public void testAdditionalArgumentsHandling_splitFormat() throws Exception {
        final FileBot filebot = (FileBot) MoveOperator.create("filebot", Arrays.asList("filebot", "-non-strict", "--format {n}"), null);
        assertEquals(Arrays.asList("-non-strict", "--format", "{n}"), filebot.additionalArguments);
    }

    @Test
    public void testAdditionalArgumentsHandling_addFormat() throws Exception {
        final FileBot filebot = (FileBot) MoveOperator.create("filebot", Arrays.asList("filebot", "-non-strict"), null);
        assertEquals(Arrays.asList("-non-strict", "--format", "{n}/Season {s}/{n} - {s00e00} - {t}"), filebot.additionalArguments);
    }

    @Test(expected = RuntimeException.class)
    public void testAdditionalArgumentsHandling_multipleFormats() throws Exception {
        MoveOperator.create("filebot", Arrays.asList("filebot", "-non-strict", "--format", "--format {n}"), null);
    }

    @Test
    public void buildArgArray_folders() throws Exception {
        final FileBot fileBot = (FileBot) MoveOperator.create("filebot", Collections.emptyList(), null);
        final String[] args = fileBot.buildArgArray(true, "filebot", "-rename", "/tv/The.Simpsons.S12.DVDRip.x264-CtrlSD");
        assertArrayEquals(new String[] { "filebot", "-rename", "/tv/The.Simpsons.S12.DVDRip.x264-CtrlSD",
                "--format", "../{n}/Season {s}/{n} - {s00e00} - {t}" }, args);
    }
}