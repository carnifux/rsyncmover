package com.carnifex.rsyncmover.mover.operators;


import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface StatefulOperator {

    Path operateStatefully(final Path from, final Path to, final List<Path> previousPaths) throws IOException;
}
