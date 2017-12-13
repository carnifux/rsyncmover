package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.DuplicateEntry;
import com.carnifex.rsyncmover.audit.entry.ErrorEntry;

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
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileBot extends MoveOperator {

    private static final String RENAME = "-rename";
    // visible for testing
    final List<String> additionalArguments;
    private String filebotPath = "filebot";
    private final Pattern moveTargetRegex = Pattern.compile("\\[MOVE\\].*?\\[.*?\\] to \\[(.*?)\\]");
    private final Pattern formatDetectionRegex = Pattern.compile("--format");
    private final Pattern formatModificationRegex = Pattern.compile("(\"*)(.*)");
    private final Pattern pathFindingRegex;
    private final Move move;

    private FileBot() {
        this.additionalArguments = null;
        this.pathFindingRegex = null;
        this.move = null;
    }

    private FileBot(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.additionalArguments = new ArrayList<>(additionalArguments != null ? additionalArguments : Collections.emptyList());
        this.pathFindingRegex = Pattern.compile("(.*)\\" + File.separator + "(.*)\\" + File.separator +
                "..\\" + File.separator + "(.*)\\" + File.separator + "(.*\\..*)");
        final Iterator<String> iter = this.additionalArguments.iterator();
        while (iter.hasNext()) {
            final String next = iter.next();
            if (next.contains("filebot")) {
                iter.remove();
                filebotPath = next;
                break;
            }
        }
        final List<String> containsFormat = this.additionalArguments.stream().filter(arg -> arg.contains("format")).collect(Collectors.toList());
        if (containsFormat.isEmpty()) {
            logger.info("Filebot mover augmenting arguments list with format argument \"{n}/Season {s}/{n} - {s00e00} - {t}\"");
            this.additionalArguments.add("--format");
            this.additionalArguments.add("{n}/Season {s}/{n} - {s00e00} - {t}");
        } else {
            if (containsFormat.size() == 1) {
                if (containsFormat.get(0).matches("--format\\s+.+")) {
                    final Iterator<String> iter2 = this.additionalArguments.iterator();
                    while (iter2.hasNext()) {
                        final String next = iter2.next();
                        if (next.contains("--format")) {
                            iter2.remove();
                            final String[] split = next.split("--format\\s+");
                            this.additionalArguments.add("--format");
                            this.additionalArguments.add(split[1]);
                            logger.info("Split filebot format arguments into \"--format\" and \"" + split[1] + "\"");
                            break;
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Filebot format arguments \"" + containsFormat.stream().collect(Collectors.joining(", ")) + " invalid - only one format option allowed");
            }
        }
        this.move = (Move) MoveOperator.create("move", additionalArguments, audit);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        // if we couldn't find the filebot path, then return the file we moved
        return exec(to, filebotPath, RENAME, preparePath(to)).orElse(to);
    }

    private String preparePath(final Path to) {
        return isWindows ? "\"" + to.toString() + "\"" : to.toString().replaceAll(" ", "\\ ");
    }

    private Optional<Path> exec(final Path to, final String... args) throws IOException {
        final boolean isDirectory = Files.isDirectory(to);
        final String[] argArray = buildArgArray(isDirectory, args);
        logger.trace("Executing \"" + Stream.of(argArray).collect(Collectors.joining(" ")) + "\"");
        final Process exec = Runtime.getRuntime().exec(argArray);
        final List<String> stdout = new BufferedReader(new InputStreamReader(exec.getInputStream())).lines().collect(Collectors.toList());
        final Optional<Path> newPath = findNewPath(stdout);
        final String errors = new BufferedReader(new InputStreamReader(exec.getErrorStream())).lines().collect(Collectors.joining("\n"));
        if (errors != null && errors.length() > 0) {
            final String errorString = "Errors returned from filebot: \n" + errors;
            logger.error(errorString);
            audit.add(new ErrorEntry(errorString, null));
        }

        if (!newPath.isPresent()) {
            // if it already exists, delete the file we moved
            if (stdout.stream().filter(line -> line.contains("already exists")).count() != 0) {
                logger.warn("Deleting duplicate file " + to.toString() + " - already moved by filebot");
                final boolean deleted = Files.deleteIfExists(to);
                if (deleted) {
                    audit.add(new DuplicateEntry(to.toString()));
                } else {
                    final String msg = "Unable to delete duplicate file " + to.toString();
                    logger.error(msg);
                    audit.add(new ErrorEntry(msg));
                }
            }
        }

        if (isDirectory) {
            // any remaining files, put them in the new location
            if (newPath.isPresent() && Files.list(to).count() > 0) {
                Files.list(to).forEach(path -> {
                    try {
                        move.operate(path, newPath.get().resolve(path.getFileName()));
                    } catch (IOException e) {
                        final String msg = "Exception moving extra files in filebot operation";
                        audit.add(new ErrorEntry(msg, e));
                        logger.error(msg, e);
                    }
                });
            }
            // delete the now empty folder
            if (Files.list(to).count() == 0) {
                logger.info("Filebot mover deleting now empty directory " + to.toString());
                Files.deleteIfExists(to);
            } else {
                final String msg = "Directory not empty after filebot move, not deleting: " + to.toString();
                audit.add(new ErrorEntry(msg));
                logger.warn(msg);
            }
        }
        exec.destroy();
        return newPath;
    }

    // visible for testing
    String[] buildArgArray(final boolean isDirectory, final String... args) {
        final String[] argArray = new String[args.length + additionalArguments.size()];
        System.arraycopy(args, 0, argArray, 0, args.length);
        for (int i = args.length, j = 0; i < argArray.length; i++) {
            argArray[i] = additionalArguments.get(j++);
        }
        // hack to get filebot to deal with folders correctly
        if (isDirectory) {
            for (int i = 0; i < argArray.length; i++) {
                final Matcher matcher = formatDetectionRegex.matcher(argArray[i]);
                if (matcher.find() && i + 1 < argArray.length) {
                    final Matcher modification = formatModificationRegex.matcher(argArray[i + 1]);
                    if (modification.find()) {
                        argArray[i + 1] = modification.group(1) + "../" + modification.group(2);
                        logger.info("Augmented format argument for filebot directory; now reads \"" + argArray[i + 1] + "\"");
                        break;
                    }
                }
            }
        }
        return argArray;
    }

    // visible for testing
    Optional<Path> findNewPath(final List<String> stdout) {
        final List<String> lines = stdout.stream()
                .peek(logger::trace)
                .filter(line -> line.startsWith("[MOVE]"))
                .map(moveTargetRegex::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .collect(Collectors.toList());
        if (lines.size() > 0) {
            final boolean isFolder = lines.get(0).contains(".." + File.separator);
            if (lines.size() == 1 && !isFolder) {
                return Optional.of(Paths.get(lines.get(0)));
            }
            if (isFolder) {
                final List<String> corrected = lines.stream()
                        .map(path -> {
                            final Matcher matcher = pathFindingRegex.matcher(path);
                            if (matcher.find()) {
                                return matcher.group(1) + File.separator + matcher.group(3);
                            }
                            return null;
                        })
                        .filter(Objects::nonNull)
                        .distinct()
                        .collect(Collectors.toList());
                if (corrected.size() == 1) {
                    logger.info("Resolved filebot folder new path to " + corrected.get(0));
                    return Optional.of(Paths.get(corrected.get(0)));
                }
            }
        }
        logger.error("Unable to find filebot path");
        return Optional.empty();
    }

    @Override
    public String getMethod() {
        return "filebot";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
