package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public class FileBotMove extends MoveOperator {

    private final Move move;
    private final FileBot fileBot;

    private FileBotMove() {
        this.move = null;
        this.fileBot = null;
    }

    private FileBotMove(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.move = (Move) MoveOperator.create("move", additionalArguments, audit);
        this.fileBot = (FileBot) MoveOperator.create("filebot", additionalArguments, audit);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        move.operate(from, to);
        return fileBot.operate(null, to);
    }

    @Override
    public String getMethod() {
        return "filebot+move";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
