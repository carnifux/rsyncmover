package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public class FileBotCopy extends MoveOperator {

    private final Copy copy;
    private final FileBot fileBot;

    private FileBotCopy() {
        this.copy = null;
        this.fileBot = null;
    }

    private FileBotCopy(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.copy = (Copy) MoveOperator.create("copy", additionalArguments, audit);
        this.fileBot = (FileBot) MoveOperator.create("filebot", additionalArguments, audit);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        copy.operate(from, to);
        return fileBot.operate(null, to);
    }

    @Override
    public String getMethod() {
        return "filebot+copy";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return false;
    }
}
