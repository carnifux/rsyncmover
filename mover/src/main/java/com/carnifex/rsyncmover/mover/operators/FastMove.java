package com.carnifex.rsyncmover.mover.operators;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.carnifex.rsyncmover.audit.Audit;

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
        final List<String> stderr = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().collect(Collectors.toList());
        if (!stderr.isEmpty()) {
            throw new RuntimeException(String.join(System.lineSeparator(), stderr));
        }
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
