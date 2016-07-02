package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


public class BeetsMove extends MoveOperator {

    private final Beets beets;
    private final Move move;

    private BeetsMove() {
        this.beets = null;
        this.move = null;
    }

    private BeetsMove(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
        this.beets = (Beets) MoveOperator.create("beets", additionalArguments, audit);
        this.move = (Move) MoveOperator.create("move", additionalArguments, audit);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        final Path tempFolder = Files.createDirectories(Paths.get(to.getParent().toString() + Beets.tempPath));
        final Path temp = Paths.get(tempFolder.toString() + File.separator + to.getFileName());
        move.operate(from, temp);
        final Path path = beets.operate(from, temp);
        Files.deleteIfExists(tempFolder);
        return path;
    }

    @Override
    public String getMethod() {
        return "beets+move";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
