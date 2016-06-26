package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public class FileBotMove extends MoveOperator {

    private final Move move;
    private final FileBot fileBot;

    public FileBotMove(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.move = new Move(audit, additionalArguments);
        this.fileBot = new FileBot(audit, additionalArguments);
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
