package at.yawk.toml.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TomlTestSuiteTest {
    @Test
    void exposesExpectedCounts() {
        assertTrue(TomlTestSuite.all().size() >= 700);
        assertTrue(TomlTestSuite.validToml10().size() >= 200);
        assertTrue(TomlTestSuite.invalidToml10().size() >= 450);
        assertTrue(TomlTestSuite.validToml11().size() >= 200);
        assertTrue(TomlTestSuite.invalidToml11().size() >= 450);
    }

    @ParameterizedTest
    @MethodSource("at.yawk.toml.test.TomlTestSuite#validToml10")
    void methodSourceCanIterateValidToml10Cases(TomlTestCase test) {
        assertTrue(test.valid(), test.id());
        assertTrue(test.supports(TomlSpecVersion.TOML_1_0_0), test.id());
        assertNotNull(test.expectedJson(), test.id());
    }

    @Test
    void generatedMetadataIsValidAndResourcesExist() {
        for (TomlTestCase test : TomlTestSuite.all()) {
            assertFalse(test.id().isBlank(), test.id());
            assertFalse(test.tomlResourcePath().isBlank(), test.id());
            assertFalse(test.tomlSpecVersions().isEmpty(), test.id());
            assertTrue(test.id().startsWith(test.valid() ? "valid/" : "invalid/"), test.id());
            assertTrue(test.tomlResourcePath().endsWith(test.id() + ".toml"), test.id());
            assertTrue(test.tomlBytes().length >= 0, test.id());
            if (test.valid()) {
                assertNotNull(test.expectedJsonResourcePath(), test.id());
                assertTrue(test.expectedJsonResourcePath().endsWith(test.id() + ".json"), test.id());
                assertNotNull(test.expectedJson(), test.id());
            } else {
                assertNull(test.expectedJsonResourcePath(), test.id());
                assertNull(test.expectedJson(), test.id());
            }
        }
    }

    @Test
    void versionListsMatchUpstreamResources() throws IOException {
        assertVersionList(TomlSpecVersion.TOML_1_0_0, "files-toml-1.0.0");
        assertVersionList(TomlSpecVersion.TOML_1_1_0, "files-toml-1.1.0");
    }

    @Test
    void preservesEdgeCaseFixtureBytes() {
        TomlTestCase emptyNothing = find("valid/empty-nothing");
        assertArrayEquals(new byte[0], emptyNothing.tomlBytes());

        TomlTestCase crlf = find("valid/empty-crlf");
        assertArrayEquals(new byte[] {'\r', '\n'}, crlf.tomlBytes());

        TomlTestCase badUtf8 = find("invalid/encoding/bad-utf8-at-end");
        byte[] bytes = badUtf8.tomlBytes();
        assertTrue(bytes.length > 0);
        assertEquals((byte) 0xda, bytes[bytes.length - 1]);
    }

    @Test
    void loadsResourcesFromLibraryClassLoader() {
        TomlTestCase simple = find("valid/string/simple");
        assertTrue(new String(simple.tomlBytes(), StandardCharsets.UTF_8).contains("You are not drinking enough whisky."));
        String expectedJson = simple.expectedJson();
        assertNotNull(expectedJson);
        assertTrue(expectedJson.contains("You are not drinking enough whisky."));
    }

    @Test
    void publicListsAreImmutable() {
        List<TomlTestCase> all = TomlTestSuite.all();
        assertFalse(all.isEmpty());
        assertThrows(UnsupportedOperationException.class, all::clear);
    }

    private static TomlTestCase find(String id) {
        return TomlTestSuite.all().stream()
                .filter(test -> test.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static void assertVersionList(TomlSpecVersion version, String resourceName) throws IOException {
        Path resourceRoot = Path.of("toml-test/tests");
        List<String> lines = Files.readAllLines(resourceRoot.resolve(resourceName), StandardCharsets.UTF_8);
        Set<String> expectedTomlIds = new HashSet<>();
        Set<String> expectedJsonResources = new HashSet<>();
        for (String line : lines) {
            if (line.endsWith(".toml")) {
                expectedTomlIds.add(line.substring(0, line.length() - ".toml".length()));
            } else if (line.endsWith(".json")) {
                expectedJsonResources.add("tests/" + line);
            }
        }
        Set<String> actualTomlIds = new HashSet<>();
        Set<String> actualJsonResources = new HashSet<>();
        for (TomlTestCase test : TomlTestSuite.forVersion(version)) {
            actualTomlIds.add(test.id());
            if (test.expectedJsonResourcePath() != null) {
                actualJsonResources.add(test.expectedJsonResourcePath());
            }
        }
        assertEquals(expectedTomlIds, actualTomlIds);
        assertEquals(expectedJsonResources, actualJsonResources);
    }
}
