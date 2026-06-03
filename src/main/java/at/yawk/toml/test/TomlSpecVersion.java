package at.yawk.toml.test;

/** TOML language versions covered by the embedded upstream test lists. */
public enum TomlSpecVersion {
    /** TOML 1.0.0 test selection. */
    TOML_1_0_0("1.0.0"),
    /** TOML 1.1.0 test selection. */
    TOML_1_1_0("1.1.0");

    private final String version;

    TomlSpecVersion(String version) {
        this.version = version;
    }

    /**
     * Returns the upstream TOML version string, for example {@code 1.0.0}.
     *
     * @return upstream TOML version string
     */
    public String version() {
        return version;
    }

}
