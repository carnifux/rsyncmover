package com.carnifex.rsyncmover.mover.operators;


import java.io.IOException;
import java.nio.file.Path;

public class MoveSymlink extends MoveOperator {

    private final Move move;
    private final Symlink symlink;

    public MoveSymlink() {
        this.move = new Move();
        this.symlink = new Symlink();
    }

    @Override
    protected void operate(final Path from, final Path to) throws IOException {
        logger.info("Moving and creating symlink for " + from + ", " + to);
        move.operate(from, to);
        symlink.operate(to, from);
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
