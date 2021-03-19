package com.carnifex.rsyncmover.mover.operators;

import junit.framework.TestCase;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TvSeriesTest extends TestCase {

    @Test
    public void testTvSeries() {
        final TvSeries tvSeries = new TvSeries();
        // folder
        assertEquals("prefixes/Travel Man 48 Hours In/Season 4", tvSeries.getTvPathIfWeCan("prefixes/Travel.Man.48.Hours.In.S04.1080p.AMZN.WEB-DL.DD+2.0.H.264-monkee"));
        // single ep, dont do it
        assertNull(tvSeries.getTvPathIfWeCan("prefixes/i.may.destroy.you.s01e01.1080p.web.h264-btx.mkv"));
        // movie, nope
        assertNull(tvSeries.getTvPathIfWeCan("prefixes/Zack.Snyders.Justice.League.2021.1080p.HMAX.WEB-DL.DDP5.1.Atmos.H.264-MZABI.mkv"));
    }

    @Test
    public void test() throws Exception {
        final Path operate = new TvSeries() {
            @Override
            void doMove(final Path from, final Path p) throws IOException {

            }
        }.operate(Paths.get("/tv/Rob.and.Romesh.vs.S01.HDTV.AAC2.0.x264-TVCUK"), Paths.get("/tv/Rob.and.Romesh.vs.S01.HDTV.AAC2.0.x264-TVCUK"));
        assertEquals(Paths.get("/tv/Rob and Romesh vs/Season 1"), operate);
    }

}