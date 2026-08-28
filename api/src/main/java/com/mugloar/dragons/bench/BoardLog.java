package com.mugloar.dragons.bench;

import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.solver.AdOption;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes every ad on every board to a CSV, one row per ad per turn.
 *
 * <p>Distinct from {@link AttemptLog}, and the distinction is the whole point. The attempt corpus
 * holds only the ads the solver chose, which is exactly the wrong sample for asking what a board
 * offers — it is filtered by the estimate under test. This holds the board as posted, including
 * every ad rejected, so the offline world's generator can be built from what the game deals rather
 * than from what the bot liked.
 *
 * <p>It costs nothing upstream. The strategy already scores the whole board to choose one ad, and
 * carries all of it on the decision, so recording is a read of something already in hand.
 */
final class BoardLog implements Closeable {

    private static final String HEADER = "game,turn,level,expires_in,reward,label,message";

    private final Path file;
    private final BufferedWriter writer;
    private final AtomicLong rows = new AtomicLong();

    private BoardLog(Path file, BufferedWriter writer) {
        this.file = file;
        this.writer = writer;
    }

    static BoardLog open(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        writer.write(HEADER);
        writer.newLine();
        writer.flush();
        return new BoardLog(file, writer);
    }

    /** The state is the one the board was posted against, so level and turn belong to the ads. */
    synchronized void record(GameState state, List<AdOption> board) {
        try {
            for (AdOption ad : board) {
                writer.write(row(state, ad));
                writer.newLine();
                rows.incrementAndGet();
            }
            // Flushed per board rather than per row: a board is what a stopped run would want whole.
            writer.flush();
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

    private static String row(GameState state, AdOption ad) {
        return String.format(
                Locale.ROOT,
                "%s,%d,%d,%d,%d,%s,%s",
                state.gameId(), state.turn(), state.level(), ad.expiresIn(), ad.reward(),
                quoted(ad.probability()), quoted(ad.message()));
    }

    /** Ad text is free-form and does contain commas, so both fields are quoted, RFC 4180 style. */
    private static String quoted(String text) {
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
