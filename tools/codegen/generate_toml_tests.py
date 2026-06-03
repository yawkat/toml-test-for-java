#!/usr/bin/env python3
"""Generate Java metadata for the embedded upstream toml-test suite."""

from __future__ import annotations

import sys
from collections import OrderedDict
from pathlib import Path

RESOURCE_PREFIX = "tests/"
HEADER = """package at.yawk.toml.test;

import static at.yawk.toml.test.TomlSpecVersion.TOML_1_0_0;
import static at.yawk.toml.test.TomlSpecVersion.TOML_1_1_0;

import java.util.List;
import java.util.Set;

final class GeneratedTomlTests {
    static final List<TomlTestCase> ALL = List.of(
"""
FOOTER = """
    );

    private GeneratedTomlTests() {
    }
}
"""


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        raise SystemExit("Usage: generate_toml_tests.py <toml-test-dir> <generated-sources-dir>")

    upstream_root = Path(argv[1])
    output_root = Path(argv[2])
    tests_root = upstream_root / "tests"

    versions_by_path: OrderedDict[str, set[str]] = OrderedDict()
    load_version_list(tests_root, "1.0.0", versions_by_path)
    load_version_list(tests_root, "1.1.0", versions_by_path)

    toml_paths = sorted(path for path in versions_by_path if path.endswith(".toml"))
    lines = [HEADER]
    for index, toml_path in enumerate(toml_paths):
        suffix = "\n" if index + 1 == len(toml_paths) else ",\n"
        lines.append(format_case(versions_by_path, toml_path) + suffix)
    lines.append(FOOTER)

    output = output_root / "at" / "yawk" / "toml" / "test" / "GeneratedTomlTests.java"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("".join(lines), encoding="utf-8")
    return 0


def load_version_list(tests_root: Path, version: str, versions_by_path: OrderedDict[str, set[str]]) -> None:
    for line in (tests_root / f"files-toml-{version}").read_text(encoding="utf-8").splitlines():
        if line:
            versions_by_path.setdefault(line, set()).add(version)


def format_case(versions_by_path: OrderedDict[str, set[str]], toml_path: str) -> str:
    valid = toml_path.startswith("valid/")
    test_id = toml_path.removesuffix(".toml")
    category = toml_path.split("/")[1] if len(toml_path.split("/")) > 2 else ""
    expected_json_path = ""
    if valid:
        expected_json_path = toml_path.removesuffix(".toml") + ".json"
        if expected_json_path not in versions_by_path:
            raise ValueError(f"Expected JSON is missing from upstream file lists: {expected_json_path}")

    return (
        "            new TomlTestCase("
        f"{java_literal(test_id)}, "
        f"{str(valid).lower()}, "
        f"{java_literal(category)}, "
        f"{version_set(versions_by_path[toml_path])}, "
        f"{java_literal(RESOURCE_PREFIX + toml_path)}, "
        f"{nullable_literal('' if not expected_json_path else RESOURCE_PREFIX + expected_json_path)})"
    )


def version_set(versions: set[str]) -> str:
    constants = []
    for version in sorted(versions):
        if version == "1.0.0":
            constants.append("TOML_1_0_0")
        elif version == "1.1.0":
            constants.append("TOML_1_1_0")
        else:
            raise ValueError(f"Unknown TOML spec version: {version}")
    return "Set.of(" + ", ".join(constants) + ")"


def nullable_literal(value: str) -> str:
    if not value:
        return "null"
    return java_literal(value)


def java_literal(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"'


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
