package com.carnifex.rsyncmover.mover.operators;

import com.carnifex.rsyncmover.audit.Audit;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TvSeries extends MoveOperator {

    private static final Logger logger = Logger.getLogger(TvSeries.class);

    protected TvSeries() {
        super();
    }

    protected TvSeries(final Audit audit, final List<String> additionalArguments) {
        super(audit, additionalArguments);
    }

    @Override
    protected Path operate(final Path from, final Path to) throws IOException {
        if (!from.equals(to)) {
            // should only ever be used after another move op
            throw new IllegalArgumentException("use this after fastmove or something");
        }
        // only dirs
        // positively testing for a regular file avoids issues in unit tests
        if (Files.isRegularFile(from)) {
            return from;
        }

        final String tvPathIfWeCan = getTvPathIfWeCan(to.toString());
        if (tvPathIfWeCan != null) {
            logger.info("Moving to " + tvPathIfWeCan);
            final Path p = Paths.get(tvPathIfWeCan);
            doMove(from, p);
            return p;
        }
        return from;
    }

    void doMove(final Path from, final Path p) throws IOException {
        if (from.toFile().isDirectory()) {
            FileUtils.moveDirectory(from.toFile(), p.toFile());
        } else {
            Files.move(from, p);
        }
    }

    // visible for testing
    String getTvPathIfWeCan(final String in) {
        if (in.endsWith(File.separator)) {
            return getTvPathIfWeCan(in.substring(0, in.length() - 2));
        }
        final String firstBit = in.substring(0, in.lastIndexOf(File.separator));
        final String lastBit = in.substring(in.lastIndexOf(File.separator) + 1);
        final Matcher m = Pattern.compile("([\\w.]+)\\.S(\\d\\d).+").matcher(lastBit);
        if (m.matches()) {
            final String series = m.group(1).replace(".", " ");
            // strip the 0s
            final String season = "Season " + Integer.valueOf(m.group(2)).toString();

            return firstBit + File.separator + series + File.separator + season;
        }
        return null;
    }


    @Override
    public String getMethod() {
        return "tvseries";
    }

    @Override
    public boolean shouldSetFilePermissions() {
        return true;
    }
}
