package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class MoveSymlink extends MoveOperator {

    private final Move move;
    private final Symlink symlink;

    private MoveSymlink() {
        this.move = null;
        this.symlink = null;
    }

    private MoveSymlink(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.move = (Move) MoveOperator.create("move", additionalArguments, audit);
        this.symlink = (Symlink) MoveOperator.create("symlink", additionalArguments, audit);
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
