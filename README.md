# TOML Test for Java

Java 17 wrapper around the official [`toml-test`](https://github.com/toml-lang/toml-test) suite.
The jar is self-contained: it embeds the upstream `toml-test` v2.2.0 fixtures from the `toml-test` git submodule and exposes a small generated API for enumerating test metadata and loading fixture bytes from the classpath.

```java
import static org.junit.jupiter.api.Assertions.assertNotNull;

import at.yawk.toml.test.TomlTestCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MyTomlParserTest {
    @ParameterizedTest
    @MethodSource("at.yawk.toml.test.TomlTestSuite#validToml100")
    void parsesOfficialToml10Cases(TomlTestCase test) {
        byte[] toml = test.tomlBytes();
        String expectedJson = test.expectedJson();
        assertNotNull(expectedJson);

        // Parse toml with your implementation and compare with expectedJson.
    }
}
```

Valid cases include TOML input plus expected tagged JSON. Invalid cases include TOML input that compliant decoders should reject. Choose the list for the TOML version your parser is running in; a TOML 1.0.0 conformance test should use the TOML 1.0.0 valid and invalid lists, while TOML 1.1.0 mode should use the TOML 1.1.0 lists. Some inputs that are invalid TOML 1.0.0 are valid TOML 1.1.0, so a parser should not be expected to pass both version suites at once. Version-specific membership is generated at build time by `uv run --locked` from upstream `tests/files-toml-1.0.0` and `tests/files-toml-1.1.0` instead of inferring from directory names.

## Versioning

Release tags are intended to use `v<wrapper-version>-<upstream-toml-test-version>`, for example `v1.0-2.2.0`, producing Maven version `1.0-2.2.0` through `maven-git-versioning-extension`.

## Building

Use the Maven Wrapper. The build also requires `uv` because Maven runs the Python code generator through `uv run --locked`; install `uv` locally or use the GitHub Actions workflow, which installs it before running Maven.

```bash
./mvnw -B -ntp verify
```
