package com.mugloar.dragons.mugloar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Upstream errors arrive as full HTML pages. What ends up in an exception message has to stay
 * short and single-line, because it travels to the UI.
 */
class ErrorSnippetTest {

    @Test
    @DisplayName("collapses the newlines of an HTML error page onto one line")
    void flattensMultiLineHtml() {
        String html = """
                <!DOCTYPE html>
                <title>404 Not Found</title>
                <h1>Not Found</h1>
                """;

        String snippet = RestMugloarClient.snippet(html.getBytes(StandardCharsets.UTF_8));

        assertThat(snippet)
                .doesNotContain("\n")
                .isEqualTo("<!DOCTYPE html> <title>404 Not Found</title> <h1>Not Found</h1>");
    }

    @Test
    void truncatesALongPageAndMarksIt() {
        String snippet = RestMugloarClient.snippet("x".repeat(500).getBytes(StandardCharsets.UTF_8));

        assertThat(snippet).hasSize(201).endsWith("…");
    }

    @Test
    void leavesAShortBodyIntact() {
        String snippet = RestMugloarClient.snippet("error code: 1010".getBytes(StandardCharsets.UTF_8));

        assertThat(snippet).isEqualTo("error code: 1010");
    }
}
