package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class Validate extends MoveOperator implements StatefulOperator {

    public Validate(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getMethod() {
        return "validate";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return false;
    }

    @Override
    public Path operateStatefully(final Path from, final Path to, final List<Path> previousPaths) throws IOException {
        // we want to compare the path we've been given to the one second to last in the list
        if (previousPaths.size() < 2) {
            return to;
        }
        final Path previous = previousPaths.get(previousPaths.size() - 2);
        final String previousHash;
        final String currentHash;
        try {
            previousHash = hash(previous);
            currentHash = hash(from);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        logger.debug("Previous hash: " + previousHash + "; new hash: " + currentHash);
        if (!previousHash.equals(currentHash)) {
            throw new RuntimeException("Hashes of files " + from + ", " + previous + " do not match; " + previousHash + ", " + currentHash);
        }
        return to;
    }

    private String hash(final Path path) throws IOException, NoSuchAlgorithmException {
        try (final InputStream inputStream = Files.newInputStream(path)) {
            final MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
            int r;
            final byte[] b = new byte[1024];
            while ((r = inputStream.read(b)) != -1) {
                messageDigest.update(b, 0, r);
            }
            return new String(messageDigest.digest());
        }
    }
}
