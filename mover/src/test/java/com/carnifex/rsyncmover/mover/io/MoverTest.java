package com.carnifex.rsyncmover.mover.io;

import com.carnifex.rsyncmover.beans.RsyncMover.Movers;
import org.junit.Test;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.assertEquals;

/**
 * Created by carni on 28/03/2016.
 */
public class MoverTest {

    @Test
    public void getTarget_Simple() throws Exception {
        final String simplePath = "/test";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("/dir");
        assertEquals(Paths.get("/dir/test"), new Mover(mover).getTarget(Paths.get(simplePath)));
    }

    @Test
    public void getTarget_Windows() throws Exception {
        final String simplePath = "/test";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("D:\\dir\\");
        assertEquals(Paths.get("D:\\dir\\test"), new Mover(mover).getTarget(Paths.get(simplePath)));
    }

    @Test
    public void getTarget_DateReplace() throws Exception {
        final String simplePath = "/test";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("/dir/dir$yyyy$");
        assertEquals(Paths.get("/dir/dir" + LocalDate.now().getYear() + "/test"),
                new Mover(mover).getTarget(Paths.get(simplePath)));
    }

    @Test
    public void getTarget_DateReplace_Multiple() throws Exception {
        final String simplePath = "/test";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("/dir/dir$yyyy$/dir$MM$");
        assertEquals(Paths.get("/dir/dir" + LocalDate.now().getYear() + "/dir" + LocalDate.now().format(DateTimeFormatter.ofPattern("MM")) + "/test"),
                new Mover(mover).getTarget(Paths.get(simplePath)));
    }

    @Test
    public void getTarget_RegexMatch() throws Exception {
        final String simplePath = "/test";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("/dir/%(\\w)%1/%");
        assertEquals(Paths.get("/dir/t/test"), new Mover(mover).getTarget(Paths.get(simplePath)));
    }

    @Test
    public void getTarget_RegexMatch_NoMatch() throws Exception {
        final String simplePath = "/test";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("/dir/%(\\d)%1/%");
        assertEquals(Paths.get("/dir/test"), new Mover(mover).getTarget(Paths.get(simplePath)));
    }

    @Test
    public void getTarget_RegexMatchMultipleGroups() throws Exception {
        final String simplePath = "/test";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("/dir/%(\\w)(\\w{2})%1/2/%");
        assertEquals(Paths.get("/dir/t/es/test"), new Mover(mover).getTarget(Paths.get(simplePath)));
    }

    @Test
    public void getTarget_RegexMatchMultiple() throws Exception {
        final String simplePath = "/test - 2016";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("/dir/%(\\w)%1/%%(\\d+)%Year 1/%");
        assertEquals(Paths.get("/dir/t/Year 2016/test - 2016"), new Mover(mover).getTarget(Paths.get(simplePath)));
    }

    @Test
    public void getTarget_RegexMatchSeason() throws Exception {
        final String simplePath = "/Tv.Show.s01e04.title.mkv";
        final Movers.Mover mover = new Movers.Mover();
        mover.setPartialMatch(true);
        mover.setTargetDirectory("/dir/%(.*?)\\.?s(\\d{2})e\\d{2}%1/Season 2/%");
        assertEquals(Paths.get("/dir/Tv.Show/Season 01/Tv.Show.s01e04.title.mkv"), new Mover(mover).getTarget(Paths.get(simplePath)));
    }
}