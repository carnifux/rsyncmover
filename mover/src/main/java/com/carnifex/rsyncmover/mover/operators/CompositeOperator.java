package com.carnifex.rsyncmover.mover.operators;


import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompositeOperator extends MoveOperator {

    private final List<MoveOperator> composites;
    private final String compositeName;
    private final boolean shouldSetFilePermissions;

    public CompositeOperator() {
        this.composites = null;
        this.compositeName = "emptyComposite";
        this.shouldSetFilePermissions = false;
    }

    public CompositeOperator(final List<MoveOperator> composites) {
        this.composites = composites;
        this.compositeName = composites.stream().map(op -> op.getMethod()).collect(Collectors.joining("+"));
        this.shouldSetFilePermissions = composites.stream().anyMatch(op -> op.shouldSetFilePermissions());
        logger.info("Instantiated composite operator " + getMethod());
    }

    @Override
    protected Path operate(Path from, Path to) throws IOException {
        final List<Path> results = new ArrayList<>();
        Path result = from;
        results.add(result);
        for (final MoveOperator operator : composites) {
            logger.debug("Composite operator " + compositeName + " using operator " + operator.getMethod() + " from " + result + " to " + from);
            if (operator instanceof StatefulOperator) {
                result = ((StatefulOperator) operator).operateStatefully(result, to, results);
            } else {
                result = operator.operate(result, to);
            }
            result = result.toAbsolutePath();
            results.add(result);
        }
        return result;
    }

    @Override
    public String getMethod() {
        return compositeName;
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return shouldSetFilePermissions;
    }


}
