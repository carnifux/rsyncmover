package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ConfigLoaderTest {

    private static RsyncMover config;

    @BeforeClass
    public static void setup() throws Exception {
        config = new ConfigLoader().load(ClassLoader.getSystemResource("empty_config.xml").getPath().replaceAll("/\\w:", ""));
    }

    @Test
    public void testDontMatchByName() throws Exception {
        final Mover tv = config.getMovers().getMover().stream().filter(m -> m.getName().equals("tv")).findFirst().get();
        final Mover movies = config.getMovers().getMover().stream().filter(m -> m.getName().equals("movies")).findFirst().get();
        final List<String> tvPatterns = tv.getPatterns().getPattern().stream().sorted().collect(Collectors.toList());
        final List<String> moviePatterns = movies.getDontMatchPatterns().getPattern().stream().sorted().collect(Collectors.toList());
        assertEquals(tvPatterns, moviePatterns);
    }
}