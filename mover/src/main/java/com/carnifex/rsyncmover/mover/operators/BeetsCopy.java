package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


public class BeetsCopy extends MoveOperator{

    private final Beets beets;
    private final Copy copy;

    private BeetsCopy() {
        this.beets = null;
        this.copy = null;
    }

    private BeetsCopy(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.beets = (Beets) MoveOperator.create("beets", additionalArguments, audit);
        this.copy = (Copy) MoveOperator.create("copy", additionalArguments, audit);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        final Path tempFolder = Files.createDirectories(Paths.get(to.getParent().toString() + Beets.tempPath));
        final Path temp = Paths.get(tempFolder.toString() + File.separator + to.getFileName());
        copy.operate(from, temp);
        final Path path = beets.operate(from, temp);
        Files.deleteIfExists(tempFolder);
        return path;
    }

    @Override
    public String getMethod() {
        return "beets+copy";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
