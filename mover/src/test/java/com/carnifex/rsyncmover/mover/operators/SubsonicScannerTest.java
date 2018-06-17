package com.carnifex.rsyncmover.mover.operators;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;


public class SubsonicScannerTest {

    @Test
    @Ignore
    public void testSubsonicScanner_real() throws IOException {
        final SubsonicScanner scanner = new SubsonicScanner(null, Arrays.asList("user:admin", "password:adminqz12314r", "url:http://totoro:8080/subsonic"));
        scanner.operate(null, null);
    }
}