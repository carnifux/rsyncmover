package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;

public class MoveSymlink extends MoveOperator {

    private final Move move;
    private final Symlink symlink;

    public MoveSymlink(final Audit audit) {
        super(audit);
        this.move = new Move(audit);
        this.symlink = new Symlink(audit);
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
