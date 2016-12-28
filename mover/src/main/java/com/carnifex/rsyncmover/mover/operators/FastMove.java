package com.carnifex.rsyncmover.mover.operators;


import com.carnifex.rsyncmover.audit.Audit;
import com.carnifex.rsyncmover.mover.Permissions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Collectors;

public class FastMove extends MoveOperator {

    public FastMove() {
        super();
    }

    public FastMove(Audit audit, List<String> additionalArguments) {
        super(audit, additionalArguments);
        if (isWindows) {
            throw new IllegalArgumentException("Cannot use " + getMethod() + " operator on windows");
        }
    }

    @Override
    protected Path operate(Path from, Path to) throws IOException {
        final String[] args = new String[] { "mv", from.toAbsolutePath().toString(), to.toAbsolutePath().toString() };
        final Process process = Runtime.getRuntime().exec(args);
        final List<String> stdout = new BufferedReader(new InputStreamReader(process.getInputStream())).lines().collect(Collectors.toList());
        final List<String> errors = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().collect(Collectors.toList());
        if (!errors.isEmpty()) {
            throw new RuntimeException();
        }
        // set permissions to something reasonable
        Permissions.setPermissions(to, PosixFilePermissions.fromString("rwxr--r--"));
        return to;
    }

    @Override
    public String getMethod() {
        return "fastmove";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
