package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class FileBotSymlink extends MoveOperator {

    private final Move move;
    private final FileBot fileBot;
    private final Symlink symlink;

    public FileBotSymlink(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.move = new Move(audit, additionalArguments);
        this.fileBot = new FileBot(audit, additionalArguments);
        this.symlink = new Symlink(audit, additionalArguments);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        move.operate(from, to);
        final Path fromFilebot = fileBot.operate(null, to);
        symlink.operate(fromFilebot, from);
        return fromFilebot;
    }

    @Override
    public String getMethod() {
        return "filebot+move+symlink";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
