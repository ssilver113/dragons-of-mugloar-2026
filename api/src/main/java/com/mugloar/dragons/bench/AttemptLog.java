package com.mugloar.dragons.bench;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes every solve to a CSV, one row per attempt — the corpus the success model is refitted from.
 *
 * <p>Flushed per row rather than buffered to the end, so a run stopped halfway still leaves usable
 * data. The volume is a few thousand short lines against tens of minutes of network waiting, so the
 * cost does not register.
 */
final class AttemptLog implements Closeable {

    private static final String HEADER =
            "game,turn,level,lives,gold,reward,label,tier,expires_in,estimate,score,success";

    private final Path file;
    private final BufferedWriter writer;
    private final AtomicLong rows = new AtomicLong();

    private AttemptLog(Path file, BufferedWriter writer) {
        this.file = file;
        this.writer = writer;
    }

    static AttemptLog open(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        writer.write(HEADER);
        writer.newLine();
        writer.flush();
        return new AttemptLog(file, writer);
    }

    synchronized void record(SolveAttempt attempt) {
        try {
            writer.write(row(attempt));
            writer.newLine();
            writer.flush();
            rows.incrementAndGet();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not append to " + file, e);
        }
    }

    Path file() {
        return file;
    }

    long rows() {
        return rows.get();
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }

    /** The label is the only free text, and it is quoted rather than trusted to stay comma-free. */
    private static String row(SolveAttempt a) {
        return String.format(
                Locale.ROOT,
                "%s,%d,%d,%d,%d,%d,\"%s\",%s,%d,%.4f,%.2f,%d",
                a.gameId(), a.turn(), a.level(), a.lives(), a.gold(), a.reward(),
                a.label().replace('"', '\''), a.tier(), a.expiresIn(),
                a.estimate(), a.score(), a.success() ? 1 : 0);
    }
}
