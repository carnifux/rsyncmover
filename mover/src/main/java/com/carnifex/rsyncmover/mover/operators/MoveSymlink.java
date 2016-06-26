package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class MoveSymlink extends MoveOperator {

    private final Move move;
    private final Symlink symlink;

    public MoveSymlink(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.move = new Move(audit, additionalArguments);
        this.symlink = new Symlink(audit, additionalArguments);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        logger.info("Moving and creating symlink for " + from + ", " + to);
        move.operate(from, to);
        symlink.operate(to, from);
        return to;
    }

    @Override
    public String getMethod() {
        return "move+symlink";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
