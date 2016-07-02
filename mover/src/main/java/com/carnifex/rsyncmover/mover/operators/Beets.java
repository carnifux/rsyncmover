package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;
import com.carnifex.rsyncmover.mover.io.Mover;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Beets extends MoveOperator {

    static final String tempPath = File.separator + "beetstemp";
    private final Move move;
    private final List<String> additionalArguments;
    private String beetsLocation = "beet";
    private String untaggedLocation;
    private final boolean isWindows;

    private Beets() {
        this.move = null;
        this.additionalArguments = null;
        this.isWindows = false;
    }

    private Beets(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.additionalArguments = new ArrayList<>(additionalArguments != null ? additionalArguments : Collections.emptyList());
        final Iterator<String> iter = this.additionalArguments.iterator();
        while (iter.hasNext()) {
            final String next = iter.next();
            if (next.contains("beet")) {
                beetsLocation = next;
                iter.remove();
            }
            if (next.startsWith("untagged-location")) {
                this.untaggedLocation = next.split(": ")[1];
                iter.remove();
            }
        }
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        this.move = (Move) MoveOperator.create("move", Collections.emptyList(), audit);

        try {
            final Process exec = Runtime.getRuntime().exec(new String[]{beetsLocation, "config", "-p"});
            final String configOutput = new BufferedReader(new InputStreamReader(exec.getInputStream())).lines().collect(Collectors.joining("\n"));
            logger.info("Beets config located at: " + configOutput);
        } catch (IOException e) {
            final String msg = "Error getting beets config location - is it installed?";
            logger.error(msg, e);
            audit.add(new ErrorEntry(msg, e));
        }
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        return exec(to, beetsLocation, "-l", Files.createTempFile("library", ".blb").toString(), "import", "-C", "-q", preparePath(to));
    }

    private Path exec(final Path to, final String... args) throws IOException {
        final String[] argArray = buildArgArray(args);
        logger.trace("Executing \"" + Stream.of(argArray).collect(Collectors.joining(" ")) + "\"");
        final Process exec = Runtime.getRuntime().exec(argArray);
        final List<String> stdout = new BufferedReader(new InputStreamReader(exec.getInputStream())).lines()
                .peek(logger::trace).collect(Collectors.toList());
        final String errors = new BufferedReader(new InputStreamReader(exec.getErrorStream())).lines().collect(Collectors.joining("\n"));
        if (errors != null && errors.length() > 0) {
            final String errorString = "Errors returned from beets: \n" + errors;
            logger.error(errorString);
            audit.add(new ErrorEntry(errorString, null));
        }
        final Path newPath = moveOutOfTemp(to);
        if (stdout.contains("Skipping.")) {
            return handleSkip(newPath, stdout);
        }
        return to;
    }

    private Path moveOutOfTemp(final Path to) throws IOException {
        final Path path = Paths.get(to.toString().replace(tempPath, ""));
        return move.operate(to, path);
    }

    private Path handleSkip(final Path to, final List<String> stdout) throws IOException {
        if (untaggedLocation == null) {
            return to;
        }
        logger.info("Beets unable to match " + to.getFileName().toString() + ", moving to untagged location");
        final Path untaggedPath = Paths.get(new Mover.Target(untaggedLocation).getPath(to.getFileName().toString()));
        return move.operate(to, untaggedPath);
    }

    private String[] buildArgArray(final String[] args) {
        final String[] argArray = new String[args.length + additionalArguments.size()];
        System.arraycopy(args, 0, argArray, 0, args.length);
        for (int i = args.length, j = 0; i < argArray.length; i++) {
            argArray[i] = additionalArguments.get(j++);
        }
        return argArray;
    }

    private String preparePath(final Path to) {
        return isWindows ? "\"" + to.toString() + "\"" : to.toString().replaceAll(" ", "\\ ");
    }

    @Override
    public String getMethod() {
        return "beets";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
