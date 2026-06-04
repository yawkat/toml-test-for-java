package at.yawk.toml.test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoublePredicate;
import org.jspecify.annotations.Nullable;

/**
 * Validator for the tagged JSON expected-output format used by the upstream {@code toml-test} suite.
 *
 * <p>The upstream suite stores expected parser output as JSON, but this library intentionally does not depend on a JSON
 * parser. Implementations provide JSON parsing by overriding {@link #parseExpectedJson(String)} and returning the usual
 * JSON object model of nested {@link Map}, {@link List}, strings, numbers, booleans, and {@code null}. The final
 * {@link #validate(TomlTestCase, Map)} method then compares that expected structure with an actual parser result.</p>
 *
 * <p>Expected scalar values are represented by JSON objects with string {@code type} and {@code value} members. Container
 * objects and arrays are compared recursively. Scalar validation methods are protected and non-final so implementations
 * can adapt parser-specific values, for example date/time values represented as {@code java.time} objects instead of
 * strings.</p>
 */
public abstract class TomlExpectedDocumentValidator {
    /**
     * Parses expected tagged JSON text into a JSON-like object model.
     *
     * @param expectedJson expected JSON text from {@link TomlTestCase#expectedJson()}
     * @return parsed top-level JSON object
     */
    protected abstract Map<String, ?> parseExpectedJson(String expectedJson);

    /**
     * Validate an actual TOML parser result against the expected output for a valid test case.
     *
     * @param test valid TOML test case with expected tagged JSON
     * @param actualDocument actual parser result represented as nested maps, lists, and scalars
     * @throws IllegalArgumentException if {@code test} is invalid or the expected JSON model is malformed
     * @throws AssertionError if {@code actualDocument} does not match the expected output
     */
    public final void validate(TomlTestCase test, Map<String, ?> actualDocument) {
        Objects.requireNonNull(test, "test");
        Objects.requireNonNull(actualDocument, "actualDocument");
        String expectedJson = test.expectedJson();
        if (expectedJson == null) {
            throw new IllegalArgumentException("Test case " + test.id() + " does not have expected JSON");
        }
        Map<String, ?> expectedDocument = Objects.requireNonNull(parseExpectedJson(expectedJson), "parseExpectedJson");
        validateMap("$", expectedDocument, actualDocument);
    }

    /**
     * Validate a TOML string value.
     *
     * @param path path to the value being validated
     * @param expected expected string value from tagged JSON
     * @param actual actual parser value
     */
    protected void validateString(String path, String expected, Object actual) {
        if (!expected.equals(actual)) {
            fail(path, "Expected string " + quote(expected) + " but was " + describe(actual));
        }
    }

    /**
     * Validate a TOML integer value.
     *
     * @param path path to the value being validated
     * @param expected expected integer value from tagged JSON
     * @param actual actual parser value
     */
    protected void validateInteger(String path, String expected, Object actual) {
        BigInteger expectedInteger = parseBigInteger(path, expected);
        BigInteger actualInteger = toBigInteger(actual);
        if (actualInteger == null || !expectedInteger.equals(actualInteger)) {
            fail(path, "Expected integer " + expected + " but was " + describe(actual));
        }
    }

    /**
     * Validate a TOML float value.
     *
     * @param path path to the value being validated
     * @param expected expected float value from tagged JSON
     * @param actual actual parser value
     */
    protected void validateFloat(String path, String expected, Object actual) {
        switch (expected) {
            case "inf" -> validateSpecialFloat(path, "inf", actual, number -> number > 0 && Double.isInfinite(number));
            case "-inf" -> validateSpecialFloat(path, "-inf", actual, number -> number < 0 && Double.isInfinite(number));
            case "nan" -> validateSpecialFloat(path, "nan", actual, Double::isNaN);
            default -> validateFiniteFloat(path, expected, actual);
        }
    }

    /**
     * Validate a TOML boolean value.
     *
     * @param path path to the value being validated
     * @param expected expected boolean value from tagged JSON
     * @param actual actual parser value
     */
    protected void validateBoolean(String path, String expected, Object actual) {
        boolean expectedBoolean = switch (expected) {
            case "true" -> true;
            case "false" -> false;
            default -> throw malformed(path, "Invalid boolean value: " + expected);
        };
        if (!(actual instanceof Boolean actualBoolean) || actualBoolean != expectedBoolean) {
            fail(path, "Expected boolean " + expected + " but was " + describe(actual));
        }
    }

    /**
     * Validate a TOML offset date-time value.
     *
     * @param path path to the value being validated
     * @param expected expected date-time value from tagged JSON
     * @param actual actual parser value
     */
    protected void validateOffsetDateTime(String path, String expected, Object actual) {
        validateString(path, expected, actual);
    }

    /**
     * Validate a TOML local date-time value.
     *
     * @param path path to the value being validated
     * @param expected expected local date-time value from tagged JSON
     * @param actual actual parser value
     */
    protected void validateLocalDateTime(String path, String expected, Object actual) {
        validateString(path, expected, actual);
    }

    /**
     * Validate a TOML local date value.
     *
     * @param path path to the value being validated
     * @param expected expected local date value from tagged JSON
     * @param actual actual parser value
     */
    protected void validateLocalDate(String path, String expected, Object actual) {
        validateString(path, expected, actual);
    }

    /**
     * Validate a TOML local time value.
     *
     * @param path path to the value being validated
     * @param expected expected local time value from tagged JSON
     * @param actual actual parser value
     */
    protected void validateLocalTime(String path, String expected, Object actual) {
        validateString(path, expected, actual);
    }

    private void validateValue(String path, Object expected, Object actual) {
        if (expected instanceof Map<?, ?> expectedMap) {
            validateExpectedMap(path, expectedMap, actual);
        } else if (expected instanceof List<?> expectedList) {
            validateList(path, expectedList, actual);
        } else {
            throw malformed(path, "Expected JSON value must be an object or array, got " + describe(expected));
        }
    }

    private void validateExpectedMap(String path, Map<?, ?> expectedMap, Object actual) {
        Object typeObject = expectedMap.get("type");
        Object valueObject = expectedMap.get("value");
        boolean scalarLike = typeObject instanceof String || valueObject instanceof String;
        if (scalarLike) {
            if (!(typeObject instanceof String) || !(valueObject instanceof String)) {
                throw malformed(path, "Expected JSON object must not contain only one of type/value");
            }
            validateScalar(path, expectedMap, actual);
            return;
        }
        if (actual instanceof Map<?, ?> actualMap) {
            validateMap(path, expectedMap, actualMap);
        } else {
            fail(path, "Expected table but was " + describe(actual));
        }
    }

    private void validateMap(String path, Map<?, ?> expectedMap, Map<?, ?> actualMap) {
        for (Object key : actualMap.keySet()) {
            if (!(key instanceof String)) {
                fail(path, "Actual table contains non-string key " + describe(key));
            }
        }
        for (Object key : expectedMap.keySet()) {
            if (!(key instanceof String)) {
                throw malformed(path, "Expected JSON object contains non-string key " + describe(key));
            }
        }
        if (actualMap.size() != expectedMap.size()) {
            for (Object key : expectedMap.keySet()) {
                if (!actualMap.containsKey(key)) {
                    fail(childPath(path, (String) key), "Missing key");
                }
            }
            for (Object key : actualMap.keySet()) {
                if (!expectedMap.containsKey(key)) {
                    fail(childPath(path, (String) key), "Unexpected key");
                }
            }
        }
        for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
            String key = (String) entry.getKey();
            if (!actualMap.containsKey(key)) {
                fail(childPath(path, key), "Missing key");
            }
            validateValue(childPath(path, key), entry.getValue(), actualMap.get(key));
        }
    }

    private void validateList(String path, List<?> expectedList, Object actual) {
        if (actual instanceof List<?> actualList) {
            if (expectedList.size() != actualList.size()) {
                fail(path, "Expected array with " + expectedList.size() + " elements but had " + actualList.size());
            }
            for (int i = 0; i < expectedList.size(); i++) {
                validateValue(path + "[" + i + "]", expectedList.get(i), actualList.get(i));
            }
        } else {
            fail(path, "Expected array but was " + describe(actual));
        }
    }

    private void validateScalar(String path, Map<?, ?> expectedMap, Object actual) {
        Object typeObject = expectedMap.get("type");
        Object valueObject = expectedMap.get("value");
        if (!(typeObject instanceof String type)) {
            throw malformed(path, "Expected scalar type must be a string");
        }
        if (!(valueObject instanceof String value)) {
            throw malformed(path, "Expected scalar value must be a string");
        }
        if (expectedMap.size() != 2) {
            throw malformed(path, "Expected scalar object must contain only type and value");
        }
        switch (type) {
            case "string" -> validateString(path, value, actual);
            case "integer" -> validateInteger(path, value, actual);
            case "float" -> validateFloat(path, value, actual);
            case "bool" -> validateBoolean(path, value, actual);
            case "datetime" -> validateOffsetDateTime(path, value, actual);
            case "datetime-local" -> validateLocalDateTime(path, value, actual);
            case "date-local" -> validateLocalDate(path, value, actual);
            case "time-local" -> validateLocalTime(path, value, actual);
            default -> throw malformed(path, "Unknown scalar type: " + type);
        }
    }

    private static void validateSpecialFloat(String path, String expected, Object actual, DoublePredicate matches) {
        double actualValue;
        if (actual instanceof Double doubleValue) {
            actualValue = doubleValue;
        } else if (actual instanceof Float floatValue) {
            actualValue = floatValue.doubleValue();
        } else {
            fail(path, "Expected float " + expected + " but was " + describe(actual));
            return;
        }
        if (!matches.test(actualValue)) {
            fail(path, "Expected float " + expected + " but was " + describe(actual));
        }
    }

    private static void validateFiniteFloat(String path, String expected, Object actual) {
        BigDecimal expectedDecimal = parseBigDecimal(path, expected);
        BigDecimal actualDecimal = toBigDecimal(actual);
        if (actualDecimal == null || expectedDecimal.compareTo(actualDecimal) != 0) {
            fail(path, "Expected float " + expected + " but was " + describe(actual));
        }
    }

    private static BigInteger parseBigInteger(String path, String value) {
        try {
            return new BigInteger(value);
        } catch (NumberFormatException e) {
            throw malformed(path, "Invalid integer value: " + value, e);
        }
    }

    private static BigDecimal parseBigDecimal(String path, String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw malformed(path, "Invalid float value: " + value, e);
        }
    }

    private static @Nullable BigInteger toBigInteger(Object value) {
        if (value instanceof BigInteger bigInteger) {
            return bigInteger;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        return null;
    }

    private static @Nullable BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Float || value instanceof Double) {
            double doubleValue = ((Number) value).doubleValue();
            if (!Double.isFinite(doubleValue)) {
                return null;
            }
            return BigDecimal.valueOf(doubleValue);
        }
        return null;
    }

    private static String childPath(String path, String key) {
        if (isSimpleIdentifier(key)) {
            return path + "." + key;
        }
        return path + "[" + quote(key) + "]";
    }

    private static boolean isSimpleIdentifier(String key) {
        if (key.isEmpty()) {
            return false;
        }
        char first = key.charAt(0);
        if (!isIdentifierStart(first)) {
            return false;
        }
        for (int i = 1; i < key.length(); i++) {
            if (!isIdentifierPart(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifierStart(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || c >= '0' && c <= '9';
    }

    private static IllegalArgumentException malformed(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }

    private static IllegalArgumentException malformed(String path, String message, Throwable cause) {
        return new IllegalArgumentException(path + ": " + message, cause);
    }

    private static void fail(String path, String message) {
        throw new AssertionError(path + ": " + message);
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return quote(string);
        }
        return value + " (" + value.getClass().getName() + ")";
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
