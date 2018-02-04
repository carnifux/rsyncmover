package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.beans.RsyncMover;
import com.carnifex.rsyncmover.mover.Permissions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;

import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class MoveOperator {

    protected static final Logger logger = LogManager.getLogger();
    private static final Map<String, Class<? extends MoveOperator>> operators = new HashMap<>();
    private static final Map<String, MoveOperator> instantiatedOperators = new HashMap<>();
    private static final String PACKAGE_NAME = MoveOperator.class.getPackage().getName();
    public static final String DEFAULT_OPERATOR = "move";
    protected final Audit audit;
    protected final boolean isWindows;

    protected abstract Path operate(final Path from, final Path to) throws IOException;
    public abstract String getMethod();
    public abstract boolean shouldSetFilePermissions();

    protected MoveOperator() {
        this(null, null);
    }

    protected MoveOperator(final Audit audit, final List<String> additionalArguments) {
        this.audit = audit;
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    }

    public Path move(final Path from, final Path to, final Set<PosixFilePermission> filePermissions, final Set<PosixFilePermission> folderPermissions, final UserPrincipal userPrincipal) throws IOException {
        final long startTime = System.currentTimeMillis();
        if (from.equals(to)) {
            final String msg = getMethod() + ": Origin and destination are the same, not moving: " + to;
            logger.info(msg);
            throw new IOException(msg);
        }
        if (to.toFile().exists()) {
            final String msg = getMethod() + ": File already exists, not moving: " + to;
            logger.error(msg);
            throw new IOException(msg);
        }

        final Path parent = to.getParent();
        final boolean mkdirs = parent.toFile().mkdirs();
        if (mkdirs) {
            logger.debug(getMethod() + ": Created directories for move " + parent.toString());
            if (filePermissions != null && !isWindows) {
                Permissions.setPermissions(parent, filePermissions, folderPermissions, userPrincipal);
            }
        }
        final Path path = operate(from, to);
        logger.info("Moved " + from + " in " + (System.currentTimeMillis() - startTime) / 1000 + "s");
        if (filePermissions != null && shouldSetFilePermissions() && !isWindows) {
            Permissions.setPermissions(path, filePermissions, folderPermissions, userPrincipal);
        }
        return path;
    }

    public static MoveOperator create(final List<RsyncMover.Movers.Mover.MoveOperators.MoveOperator> operators, final Audit audit) {
        if (operators.size() == 1) {
            return create(operators.get(0).getOperator(), operators.get(0).getAdditionalArguments().getArg(), audit);
        }
        final List<MoveOperator> ops = operators.stream().map(op -> create(op.getOperator(), op.getAdditionalArguments().getArg(), audit)).collect(Collectors.toList());
        return new CompositeOperator(ops);
    }

    protected static MoveOperator create(final String value, final List<String> additionalArguments, final Audit audit)  {
        if (operators.isEmpty()) {
            try {
                init();
                logger.info("Discovered movers " + operators.keySet().stream().collect(Collectors.joining(", ")));
            } catch (Exception e) {
                logger.error("Error introspecting MoveOperator types", e);
                throw new RuntimeException(e);
            }
        }
        final String key = value + (additionalArguments != null ? additionalArguments.stream().collect(Collectors.joining()) : "");
        final MoveOperator operator = instantiatedOperators.get(key);
        if (operator == null) {
            logger.debug("Instantiating new mover for key " + key);
            final Class<? extends MoveOperator> operatorClass = operators.get(value);
            try {
                final Constructor<? extends MoveOperator> constructor = operatorClass.getDeclaredConstructor(Audit.class, List.class);
                constructor.setAccessible(true);
                final MoveOperator newOperator = constructor.newInstance(audit, additionalArguments);
                instantiatedOperators.put(key, newOperator);
                return newOperator;
            } catch (Exception e) {
                logger.error("Error instantiating move operator for key \"" + key + "\"");
                throw new RuntimeException(e);
            }
        } else {
            logger.debug("Returning previously instantiated mover " + key);
            return operator;
        }
    }

    @SuppressWarnings("unchecked")
    private static void init() throws Exception {
        // temporarily disable logging
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final Collection<Appender> appenders = context.getRootLogger().getAppenders().values();
        appenders.forEach(appender -> context.getRootLogger().removeAppender(appender));
        try {
            final StandardJavaFileManager fm = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null);
            final Iterable<JavaFileObject> classes = fm.list(StandardLocation.CLASS_PATH, PACKAGE_NAME, Collections.singleton(Kind.CLASS), false);
            classes.forEach(fileObject -> {
                try {
                    final String[] split = fileObject.getName().replace(".class", "").replace(")", "").split(Pattern.quote(File.separator));
                    final Class<?> clazz = Class.forName(PACKAGE_NAME + "." + split[split.length - 1]);
                    if (MoveOperator.class.isAssignableFrom(clazz) && clazz != MoveOperator.class) {
                        MoveOperator op;
                        try {
                            final Constructor<?> constructor = clazz.getDeclaredConstructor();
                            constructor.setAccessible(true);
                            op = (MoveOperator) constructor.newInstance();
                        } catch (NoSuchMethodException e) {
                            logger.trace("No default constructor, ok to use normal constructor", e);
                            final Constructor<?> constructor = clazz.getDeclaredConstructor(Audit.class, List.class);
                            constructor.setAccessible(true);
                            op = (MoveOperator) constructor.newInstance(null, null);
                        }
                        operators.put(op.getMethod(), (Class<? extends MoveOperator>) clazz);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            appenders.forEach(appender -> context.getRootLogger().addAppender(appender));
        }
    }

}
