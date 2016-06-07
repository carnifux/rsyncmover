package com.carnifex.rsyncmover.mover.operators;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileBot extends MoveOperator {

    private final Move move;
    private final List<String> additionalArguments;
    private String filebotPath = "filebot";

    public FileBot(final List<String> additionalArguments) {
        this.move = new Move();
        this.additionalArguments = additionalArguments;
        final Iterator<String> iter = additionalArguments.iterator();
        while (iter.hasNext()) {
            final String next = iter.next();
            if (next.contains("filebot")) {
                iter.remove();
                filebotPath = next;
            }
        }
    }

    @Override
    protected void operate(final Path from, final Path to) throws IOException {
        move.operate(from, to);
        exec(filebotPath, "-rename", to.toString());
    }

    private void exec(final String... args) throws IOException {
        final String[] argArray = new String[args.length + additionalArguments.size()];
        System.arraycopy(args, 0, argArray, 0, args.length);
        for (int i = args.length; i < argArray.length; i++) {
            argArray[i] = additionalArguments.get(0);
        }
        final String s = Stream.of(argArray).collect(Collectors.joining(" "));
        final Process exec = Runtime.getRuntime().exec(s);
        new BufferedReader(new InputStreamReader(exec.getInputStream())).lines().forEach(logger::trace);
        final String errors = new BufferedReader(new InputStreamReader(exec.getErrorStream())).lines().collect(Collectors.joining("\n"));
        if (errors != null && errors.length() == 0) {
            throw new RuntimeException(errors);
        }
        exec.destroy();
    }

    @Override
    public String getMethod() {
        return "FileBot";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return false;
    }
}
