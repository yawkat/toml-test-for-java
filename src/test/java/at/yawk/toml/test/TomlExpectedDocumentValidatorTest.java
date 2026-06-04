package at.yawk.toml.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TomlExpectedDocumentValidatorTest {
    @Test
    void validatesExpectedJsonMapAgainstDocument() {
        TestValidator validator = new TestValidator(Map.of(
                "answer", tagged("string", "You are not drinking enough whisky.")));
        TomlTestCase test = find("valid/string/simple");

        validator.validate(test, Map.of("answer", "You are not drinking enough whisky."));

        assertNotNull(validator.parsedJson);
        assertTrue(validator.parsedJson.contains("You are not drinking enough whisky."));
    }

    @Test
    void reportsPathForMismatchedScalar() {
        TestValidator validator = new TestValidator(Map.of("outer", Map.of("value", tagged("integer", "3"))));
        TomlTestCase test = validTestCase();

        AssertionError error = assertThrows(AssertionError.class, () ->
                validator.validate(test, Map.of("outer", Map.of("value", 4))));

        assertTrue(error.getMessage().contains("$.outer.value"), error.getMessage());
        assertTrue(error.getMessage().contains("integer"), error.getMessage());
    }

    @Test
    void validatesArraysAndNumericEquivalence() {
        TestValidator validator = new TestValidator(Map.of(
                "array", List.of(
                        tagged("integer", "1"),
                        tagged("float", "1.10"),
                        tagged("float", "inf"),
                        tagged("float", "-inf"),
                        tagged("float", "nan"))));
        TomlTestCase test = validTestCase();

        validator.validate(test, Map.of("array", List.of(
                BigInteger.ONE,
                new BigDecimal("1.1"),
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                Double.NaN)));
    }

    @Test
    void rejectsUnsupportedActualIntegerTypeWithAssertionMessage() {
        TestValidator validator = new TestValidator(Map.of("integer", tagged("integer", "1")));
        TomlTestCase test = validTestCase();

        AssertionError error = assertThrows(AssertionError.class, () ->
                validator.validate(test, Map.of("integer", new BigDecimal("1"))));

        assertTrue(error.getMessage().contains("$.integer"), error.getMessage());
        assertTrue(error.getMessage().contains("Expected integer 1"), error.getMessage());
    }

    @Test
    void rejectsOverflowingFiniteNumberForExpectedInfinity() {
        TestValidator validator = new TestValidator(Map.of("float", tagged("float", "inf")));
        TomlTestCase test = validTestCase();

        AssertionError error = assertThrows(AssertionError.class, () ->
                validator.validate(test, Map.of("float", new BigDecimal("1e9999"))));

        assertTrue(error.getMessage().contains("$.float"), error.getMessage());
        assertTrue(error.getMessage().contains("Expected float inf"), error.getMessage());
    }

    @Test
    void rejectsIntegralActualValueForExpectedFloat() {
        TestValidator validator = new TestValidator(Map.of("float", tagged("float", "1")));
        TomlTestCase test = validTestCase();

        AssertionError error = assertThrows(AssertionError.class, () -> validator.validate(test, Map.of("float", 1)));

        assertTrue(error.getMessage().contains("$.float"), error.getMessage());
    }

    @Test
    void supportsTablesWithTypeAndValueKeys() {
        TestValidator validator = new TestValidator(Map.of(
                "type", tagged("string", "table key"),
                "value", tagged("integer", "7")));
        TomlTestCase test = validTestCase();

        validator.validate(test, Map.of("type", "table key", "value", 7));
    }

    @Test
    void failsForMissingActualKeys() {
        TestValidator validator = new TestValidator(Map.of("present", tagged("bool", "true")));
        TomlTestCase test = validTestCase();

        AssertionError error = assertThrows(AssertionError.class, () -> validator.validate(test, Map.of()));

        assertTrue(error.getMessage().contains("$.present"), error.getMessage());
        assertTrue(error.getMessage().contains("Missing key"), error.getMessage());
    }

    @Test
    void rejectsInvalidTestCases() {
        TestValidator validator = new TestValidator(Map.of());
        TomlTestCase test = find("invalid/encoding/bad-utf8-at-end");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> validator.validate(test, Map.of()));

        assertTrue(error.getMessage().contains("does not have expected JSON"), error.getMessage());
    }

    @Test
    void rejectsMalformedExpectedScalar() {
        TestValidator validator = new TestValidator(Map.of("bad", Map.of("type", "integer")));
        TomlTestCase test = validTestCase();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                validator.validate(test, Map.of("bad", 1)));

        assertTrue(error.getMessage().contains("$.bad"), error.getMessage());
        assertTrue(error.getMessage().contains("Expected JSON object must not contain only one of type/value"), error.getMessage());
    }

    @Test
    void scalarValidationCanBeCustomized() {
        TomlExpectedDocumentValidator validator = new TestValidator(Map.of(
                "date", tagged("datetime", "1987-07-05T17:45:00Z"))) {
            @Override
            protected void validateOffsetDateTime(String path, String expected, Object actual) {
                assertEquals("$.date", path);
                assertEquals(OffsetDateTime.parse(expected), actual);
            }
        };
        TomlTestCase test = validTestCase();

        assertDoesNotThrow(() -> validator.validate(test, Map.of(
                "date", OffsetDateTime.parse("1987-07-05T17:45:00Z"))));
    }

    @Test
    void rejectsNonCanonicalScalarTypeAliases() {
        TestValidator validator = new TestValidator(Map.of("date", tagged("local date", "2006-06-01")));
        TomlTestCase test = validTestCase();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                validator.validate(test, Map.of("date", "2006-06-01")));

        assertTrue(error.getMessage().contains("Unknown scalar type"), error.getMessage());
    }

    private static Map<String, String> tagged(String type, String value) {
        return Map.of("type", type, "value", value);
    }

    private static TomlTestCase validTestCase() {
        return find("valid/string/simple");
    }

    private static TomlTestCase find(String id) {
        return TomlTestSuite.all().stream()
                .filter(test -> test.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static class TestValidator extends TomlExpectedDocumentValidator {
        private final Map<String, ?> expected;
        private String parsedJson;

        private TestValidator(Map<String, ?> expected) {
            this.expected = expected;
        }

        @Override
        protected Map<String, ?> parseExpectedJson(String expectedJson) {
            assertFalse(expectedJson.isBlank());
            parsedJson = expectedJson;
            return expected;
        }
    }
}
