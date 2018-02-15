package com.carnifex.rsyncmover.config;


import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ConfigLoaderTest {

    private static RsyncMover config;

    @BeforeClass
    public static void setup() throws Exception {
        final URL systemResource = ClassLoader.getSystemResource("empty_config.xml");
        final String config = new BufferedReader(new InputStreamReader(systemResource.openStream())).lines().collect(Collectors.joining());
        ConfigLoaderTest.config = new ConfigLoader().load(config);
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