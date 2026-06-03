package at.yawk.toml.test;

import java.util.List;
import java.util.Objects;

/** Entry point for enumerating the embedded upstream {@code toml-test} cases. */
public final class TomlTestSuite {
    private static final List<TomlTestCase> ALL_TESTS = GeneratedTomlTests.ALL;
    private static final List<TomlTestCase> VALID_TOML_10 = filter(TomlSpecVersion.TOML_1_0_0, true);
    private static final List<TomlTestCase> INVALID_TOML_10 = filter(TomlSpecVersion.TOML_1_0_0, false);
    private static final List<TomlTestCase> VALID_TOML_11 = filter(TomlSpecVersion.TOML_1_1_0, true);
    private static final List<TomlTestCase> INVALID_TOML_11 = filter(TomlSpecVersion.TOML_1_1_0, false);

    private TomlTestSuite() {
    }

    /**
     * Returns all embedded TOML test cases from the union of supported upstream TOML-version file lists.
     *
     * @return immutable list of all test cases
     */
    public static List<TomlTestCase> all() {
        return ALL_TESTS;
    }

    /**
     * Returns all test cases that upstream marks as applicable to the requested TOML version.
     *
     * @param version TOML version to select
     * @return immutable list of cases for the version
     */
    public static List<TomlTestCase> forVersion(TomlSpecVersion version) {
        Objects.requireNonNull(version, "version");
        return ALL_TESTS.stream().filter(test -> test.supports(version)).toList();
    }

    /**
     * Returns valid TOML 1.0.0 test cases.
     *
     * @return immutable list of valid TOML 1.0.0 cases
     */
    public static List<TomlTestCase> validToml10() {
        return VALID_TOML_10;
    }

    /**
     * Returns invalid TOML 1.0.0 test cases.
     *
     * @return immutable list of invalid TOML 1.0.0 cases
     */
    public static List<TomlTestCase> invalidToml10() {
        return INVALID_TOML_10;
    }

    /**
     * Returns valid TOML 1.1.0 test cases.
     *
     * @return immutable list of valid TOML 1.1.0 cases
     */
    public static List<TomlTestCase> validToml11() {
        return VALID_TOML_11;
    }

    /**
     * Returns invalid TOML 1.1.0 test cases.
     *
     * @return immutable list of invalid TOML 1.1.0 cases
     */
    public static List<TomlTestCase> invalidToml11() {
        return INVALID_TOML_11;
    }

    private static List<TomlTestCase> filter(TomlSpecVersion version, boolean valid) {
        return ALL_TESTS.stream().filter(test -> test.valid() == valid && test.supports(version)).toList();
    }
}
