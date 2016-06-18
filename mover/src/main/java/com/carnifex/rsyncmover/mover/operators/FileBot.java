package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileBot extends MoveOperator {

    private final List<String> additionalArguments;
    private String filebotPath = "filebot";
    private final Pattern moveTargetRegex = Pattern.compile("\\[MOVE\\].*?\\[.*?\\] to \\[(.*?)\\]");

    public FileBot(final Audit audit, final List<String> additionalArguments) {
        super(audit);
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
    protected Path operate(final Path from, final Path to) throws IOException {
        // if we couldn't find the filebot path,
        return exec(filebotPath, "-rename", "\"" + to.toString() + "\"").orElse(to);
    }

    private Optional<Path> exec(final String... args) throws IOException {
        final String[] argArray = new String[args.length + additionalArguments.size()];
        System.arraycopy(args, 0, argArray, 0, args.length);
        for (int i = args.length, j = 0; i < argArray.length; i++) {
            argArray[i] = additionalArguments.get(j++);
        }
        final String cmd = Stream.of(argArray).collect(Collectors.joining(" "));
        logger.trace("Executing \"" + cmd + "\"");
        final Process exec = Runtime.getRuntime().exec(cmd);
        final Optional<Path> newPath = findNewPath(new BufferedReader(new InputStreamReader(exec.getInputStream())).lines());
        final String errors = new BufferedReader(new InputStreamReader(exec.getErrorStream())).lines().collect(Collectors.joining("\n"));
        if (errors != null && errors.length() > 0) {
            final String errorString = "Errors returned from filebot: \n" + errors;
            logger.error(errorString);
            audit.add(new ErrorEntry(errorString, null));
        }
        exec.destroy();
        return newPath;
    }

    // visible for testing
    Optional<Path> findNewPath(final Stream<String> output) {
        final List<String> paths = output.peek(logger::trace)
                .filter(line -> line.startsWith("[MOVE]"))
                .map(moveTargetRegex::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .collect(Collectors.toList());
        if (paths.size() == 1) {
            return Optional.of(Paths.get(paths.get(0)));
        }
        logger.error("Unable to find filebot path");
        return Optional.empty();
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
