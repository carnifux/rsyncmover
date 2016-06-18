package com.carnifex.rsyncmover.mover.operators;

import org.junit.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;


public class FileBotTest {
    @Test
    public void findNewPath() throws Exception {
        final Stream<String> output = Stream.of("Rename episodes using [TheTVDB]",
                "Auto-detected query: [Silicon Valley]", "Fetching episode data for [Silicon Valley]",
                "Fetching episode data for [Silicon Valley Rebels]", "Fetching episode data for [Start-ups: Silicon Valley]",
                "[MOVE] Rename [D:\\tv\\Silicon.Valley.S03E01.720p.HDTV.x264-SVA.mkv] to [D:\\tv\\Silicon Valley - 3x01 - Founder Friendly.mkv]",
                "Processed 0 files", "Failure (�_�)", "");
        final FileBot filebot = new FileBot(null, Collections.emptyList());
        final Path newPath = filebot.findNewPath(output).get();
        assertEquals("D:\\tv\\Silicon Valley - 3x01 - Founder Friendly.mkv", newPath.toString());
    }

}