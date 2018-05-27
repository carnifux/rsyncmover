package com.carnifex.rsyncmover;

public class Utilities {

    private Utilities() {}

    public static boolean isRunningOnWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
