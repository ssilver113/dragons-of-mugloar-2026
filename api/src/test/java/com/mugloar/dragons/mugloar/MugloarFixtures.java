package com.mugloar.dragons.mugloar;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads the recorded upstream payloads under {@code src/test/resources/mugloar}. */
final class MugloarFixtures {

    private MugloarFixtures() {
    }

    static String load(String name) {
        try (InputStream in = MugloarFixtures.class.getResourceAsStream("/mugloar/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No such fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
