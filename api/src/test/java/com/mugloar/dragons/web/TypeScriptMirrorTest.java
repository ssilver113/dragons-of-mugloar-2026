package com.mugloar.dragons.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mugloar.dragons.ads.AdFlag;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.solver.MoveType;
import com.mugloar.dragons.solver.Reason;
import com.mugloar.dragons.solver.Verdict;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The browser's view types are written by hand, so nothing but this test stops a renamed record
 * component from reaching the client as a silently missing field. The published schema is the
 * subject rather than the records themselves, because the schema is what a consumer would read
 * and it carries Jackson's naming rather than Java's.
 *
 * <p>What it does not check: nullability, which springdoc does not emit, and interfaces the schema
 * has no opinion about — {@code ProblemDetail} is Spring's own body and never becomes a component.
 * The direction that matters is covered, in that every published schema must have a mirror with
 * the same property names and the same shapes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TypeScriptMirrorTest {

    /**
     * Two of these five never reach the schema — {@code probabilityTier} crosses the wire as a
     * bare string, and the error vocabulary lives in a problem body — so all five are read off the
     * classes instead. Pairing each with a client name is the assertion, not an implementation
     * detail of it.
     */
    private static final Map<Class<? extends Enum<?>>, String> ENUM_MIRRORS = Map.of(
            AdFlag.class, "AdFlag",
            MoveType.class, "MoveType",
            Reason.class, "Reason",
            Verdict.class, "Verdict",
            Probability.Tier.class, "ProbabilityTier");

    /** Neither is a thing the server can send: they are what a failed and an abandoned fetch become. */
    private static final List<String> BROWSER_ONLY_ERROR_CODES =
            List.of("NETWORK_ERROR", "REQUEST_TIMEOUT");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyPublishedSchemaHasAMirrorWithTheSameProperties() throws Exception {
        Mirror mirror = Mirror.parse();
        JsonNode schemas = publishedSchemas();
        assertThat(schemas.isEmpty()).as("published component schemas").isFalse();

        schemas.properties().forEach(schema -> {
            String view = schema.getKey();
            Map<String, String> declared = mirror.interfaces().get(view);
            assertThat(declared)
                    .as("%s is published but has no interface in %s", view, Mirror.FILE)
                    .isNotNull();

            JsonNode properties = schema.getValue().path("properties");
            List<String> published = new ArrayList<>();
            properties.properties().forEach(property -> published.add(property.getKey()));
            assertThat(declared.keySet())
                    .as("properties of %s", view)
                    .containsExactlyInAnyOrderElementsOf(published);

            properties.properties().forEach(property -> {
                String wire = tsTypeOf(property.getValue());
                String written = declared.get(property.getKey());
                assertThat(satisfies(written, wire, mirror.unions().keySet()))
                        .as("%s.%s is %s on the wire but %s in %s",
                                view, property.getKey(), wire, written, Mirror.FILE)
                        .isTrue();
            });
        });
    }

    @Test
    void everyEnumTheClientNamesHasTheSameConstants() throws Exception {
        Mirror mirror = Mirror.parse();

        ENUM_MIRRORS.forEach((type, alias) -> {
            List<String> union = mirror.unions().get(alias);
            assertThat(union)
                    .as("%s mirrors %s, and %s declares no such union",
                            alias, type.getSimpleName(), Mirror.FILE)
                    .isNotNull();
            assertThat(union)
                    .as("members of %s", alias)
                    .containsExactlyInAnyOrderElementsOf(namesOf(type));
        });
    }

    @Test
    void theErrorVocabularyIsTheServersPlusTheTwoTheBrowserAdds() throws Exception {
        List<String> expected = new ArrayList<>(namesOf(ErrorCode.class));
        expected.addAll(BROWSER_ONLY_ERROR_CODES);

        assertThat(Mirror.parse().errorCodes())
                .as("ERROR_CODES in %s", Mirror.FILE)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private JsonNode publishedSchemas() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new ObjectMapper().readTree(body).path("components").path("schemas");
    }

    private static List<String> namesOf(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }

    /** The TypeScript a schema node would be written as, nullability aside. */
    private static String tsTypeOf(JsonNode node) {
        if (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            return ref.substring(ref.lastIndexOf('/') + 1);
        }
        return switch (node.path("type").asText("")) {
            case "string" -> "string";
            case "integer", "number" -> "number";
            case "boolean" -> "boolean";
            case "array" -> tsTypeOf(node.path("items")) + "[]";
            default -> throw new AssertionError("no TypeScript mapping for schema node " + node);
        };
    }

    /**
     * A written type satisfies the wire type when it is that type, or narrows it: a union of string
     * literals is a claim the client makes about a string, and the schema does not contradict it.
     * Nullability is stripped rather than checked, because the schema carries none to check against.
     */
    private static boolean satisfies(String written, String wire, Set<String> literalUnions) {
        String bare = written.replaceAll("\\|\\s*null", "").trim();
        if (bare.equals(wire)) {
            return true;
        }
        if (wire.equals("string")) {
            return literalUnions.contains(bare);
        }
        if (wire.equals("string[]") && bare.endsWith("[]")) {
            return literalUnions.contains(bare.substring(0, bare.length() - 2));
        }
        return false;
    }

    /**
     * Enough of a TypeScript reader for one file of plain interfaces and string unions. It refuses
     * to run on a file it cannot find or did not understand, so a mirror that moved fails here
     * rather than passing on an empty reading.
     */
    private record Mirror(
            Map<String, Map<String, String>> interfaces,
            Map<String, List<String>> unions,
            List<String> errorCodes) {

        private static final String FILE = "web/src/api/types.ts";

        private static final Pattern COMMENT =
                Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);
        private static final Pattern INTERFACE =
                Pattern.compile("export interface (\\w+) \\{([^}]*)}");
        private static final Pattern PROPERTY = Pattern.compile("^(\\w+)\\??:\\s*(.+?),?$");
        private static final Pattern UNION =
                Pattern.compile("export type (\\w+) =([^\\n]*(?:\\n\\s*\\|[^\\n]*)*)");
        private static final Pattern CODES =
                Pattern.compile("export const ERROR_CODES = \\[([^]]*)]");
        private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

        static Mirror parse() throws IOException {
            String source = COMMENT.matcher(Files.readString(locate())).replaceAll("");

            Map<String, Map<String, String>> interfaces = new LinkedHashMap<>();
            Matcher blocks = INTERFACE.matcher(source);
            while (blocks.find()) {
                interfaces.put(blocks.group(1), propertiesOf(blocks.group(2)));
            }

            Map<String, List<String>> unions = new LinkedHashMap<>();
            Matcher aliases = UNION.matcher(source);
            while (aliases.find()) {
                List<String> members = literalsIn(aliases.group(2));
                if (!members.isEmpty()) {
                    unions.put(aliases.group(1), members);
                }
            }

            Matcher codes = CODES.matcher(source);
            assertThat(codes.find()).as("ERROR_CODES in %s", FILE).isTrue();

            Mirror mirror = new Mirror(interfaces, unions, literalsIn(codes.group(1)));
            assertThat(mirror.interfaces()).as("interfaces read from %s", FILE).isNotEmpty();
            assertThat(mirror.unions()).as("string unions read from %s", FILE).isNotEmpty();
            return mirror;
        }

        private static Path locate() {
            Path beside = Path.of("..").resolve(FILE);
            Path path = Files.exists(beside) ? beside : Path.of(FILE);
            assertThat(path)
                    .as("the mirrors this test guards, looked for at %s", path.toAbsolutePath())
                    .exists();
            return path;
        }

        private static Map<String, String> propertiesOf(String body) {
            Map<String, String> properties = new LinkedHashMap<>();
            for (String line : body.split("\n")) {
                Matcher property = PROPERTY.matcher(line.trim());
                if (property.matches()) {
                    properties.put(property.group(1), property.group(2).trim());
                }
            }
            return properties;
        }

        private static List<String> literalsIn(String source) {
            List<String> found = new ArrayList<>();
            Matcher literal = LITERAL.matcher(source);
            while (literal.find()) {
                found.add(literal.group(1));
            }
            return found;
        }
    }
}
