package com.carnifex.rsyncmover.mover.io;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.audit.entry.Entry;
import com.carnifex.rsyncmover.mover.operators.MoveOperator;
import com.carnifex.rsyncmover.notifications.Notifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Mover {

    private static final Logger logger = LogManager.getLogger();
    private static final int DEFAULT_PRIORITY = 0;

    private final String name;
    private final List<Pattern> patterns;
    private final List<Pattern> negativePatterns;
    private final List<String> extensions;
    private final boolean partialMatch;
    private final Target target;
    private final MoveOperator operator;
    private final int priority;
    private List<Notifier> notifiers;

    public Mover(final com.carnifex.rsyncmover.beans.RsyncMover.Movers.Mover mover, final Audit audit) {
        this.name = mover.getName();
        this.patterns = mover.getPatterns() != null ? mover.getPatterns().getPattern().stream().map(Pattern::compile).collect(Collectors.toList())
                : Collections.emptyList();
        this.negativePatterns = mover.getDontMatchPatterns() != null ? mover.getDontMatchPatterns().getPattern()
                .stream().map(Pattern::compile).collect(Collectors.toList()) : Collections.emptyList();
        this.extensions = mover.getExtensions() != null ? mover.getExtensions().getExtension().stream().map(String::toLowerCase).collect(Collectors.toList()) : Collections.emptyList();
        this.partialMatch = mover.isPartialMatch() != null ? mover.isPartialMatch() : false;
        this.target = new Target(mover.getTargetDirectory());
        this.operator = MoveOperator.create(mover.getMoveOperators() != null ? mover.getMoveOperators().getMoveOperator() : Collections.emptyList(), audit);
        this.priority = mover.getPriority() != null ? mover.getPriority() : DEFAULT_PRIORITY;
        this.notifiers = Boolean.TRUE.equals(mover.isNotify()) && mover.getAgents() != null
                ? mover.getAgents().getAgent().stream().map(Notifier::find).collect(Collectors.toList())
                : Collections.emptyList();
        logger.info("Mover for target directory " + target.partialPaths.stream().collect(Collectors.joining()) + " with move operation "
                + operator.getMethod() + " successfully initialized");
    }

    public String getName() {
        return name;
    }

    public Path getTarget(final Path path) {
        return Paths.get(target.getPath(path.getFileName().toString()));
    }

    public boolean shouldSubmit(final Path path) {
        final File file = path.toFile();
        if (file.isDirectory()) {
            final File[] original = file.listFiles();
            if (original == null) {
                return false;
            }
            final long filtered = Stream.of(original).filter(f -> shouldSubmit(f.toPath())).count();
            return filtered != 0 && (partialMatch || original.length == filtered);
        }
        final String name = file.getName().toLowerCase();
        final boolean matchesPatterns = patterns.isEmpty()
                || patterns.stream()
                .anyMatch(pattern -> {
                    final Matcher matcher = pattern.matcher(name);
                    final boolean matches = partialMatch ? matcher.find() : matcher.matches();
                    final String msg = String.format("%s: %s matched filename %s against regex %s", this.getName(), matches ? "Positively" : "Negatively", name, pattern);
                    if (matches) {
                        logger.debug(msg);
                    } else {
                        logger.trace(msg);
                    }
                    return matches;
                });
        final boolean doesntMatchNegativePatterns = negativePatterns.isEmpty()
                || negativePatterns.stream()
                .noneMatch(pattern -> {
                    final Matcher matcher = pattern.matcher(name);
                    final boolean matches = partialMatch ? matcher.find() : matcher.matches();
                    final String msg = String.format("%s: %s matched filename %s against negative regex %s", this.getName(), matches ? "Positively" : "Negatively", name, pattern);
                    if (matches) {
                        logger.trace(msg);
                    } else {
                        logger.debug(msg);
                    }
                    return matches;
                });
        final boolean extensionsCorrect = extensions.isEmpty() || extensions.stream().anyMatch(name::endsWith);
        return matchesPatterns && extensionsCorrect && doesntMatchNegativePatterns;
    }

    public MoveOperator getMoveOperator() {
        return operator;
    }

    public int getPriority() {
        return priority;
    }

    public void notify(final Entry entry) {
        notifiers.forEach(notifier -> notifier.notify(entry));
    }

    public static final class Target {

        private static final Pattern timeRegex = Pattern.compile("(.*?)((?:\\$.+?\\$)|(?:%.+?%.+?%))(.*)");
        private static final Pattern regexRegex = Pattern.compile("%(.+?)%(.+?)%");

        private final List<String> partialPaths;

        public Target(final String directory) {
            partialPaths = new ArrayList<>();

            resolvePath(directory);
            // test the regexes
            getPath("test.file");
        }

        public String getPath(final String filename) {
            if (partialPaths.size() == 1) {
                return partialPaths.get(0) + File.separator + filename;
            }

            return partialPaths.stream()
                    .map(path -> {
                        if (path.startsWith("%")) {
                            final List<String> regexPair = getRegexPair(path);
                            final Matcher matcher = Pattern.compile(regexPair.get(0)).matcher(filename);
                            if (matcher.find()) {
                                String result = regexPair.get(1);
                                for (int i = 1; i <= matcher.groupCount(); i++) {
                                    result = result.replaceFirst(String.valueOf(i), matcher.group(i));
                                }
                                return result;
                            }
                            return "";
                        }
                        return path;
                    })
                    .map(path -> {
                        if (path.startsWith("$")) {
                            return LocalDateTime.now().format(DateTimeFormatter.ofPattern(path.substring(1, path.length() - 1)));
                        }
                        return path;
                    })
                    .collect(Collectors.joining()) + File.separator + filename;
        }

        private List<String> getRegexPair(final String input) {
            final Matcher matcher = regexRegex.matcher(input);
            if (matcher.matches()) {
                return Arrays.asList(matcher.group(1), matcher.group(2));
            }
            throw new PatternSyntaxException("Malformed regex replacement", input, -1);
        }

        private void resolvePath(final String directory) {
            final Matcher matcher = timeRegex.matcher(directory);
            if (matcher.find()) {
                final String start = matcher.group(1);
                final String group = matcher.group(2);
                final String rest = matcher.group(3);

                partialPaths.add(start);
                partialPaths.add(group);
                resolvePath(rest);
            } else {
                partialPaths.add(directory);
            }
        }


    }
}
