package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public class FileBotCopy extends MoveOperator {

    private final Copy copy;
    private final FileBot fileBot;

    public FileBotCopy(final Audit audit, final List<String> additionalArguments) {
        super(audit);
        this.copy = new Copy(audit);
        this.fileBot = new FileBot(audit, additionalArguments);
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
