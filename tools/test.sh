#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -d "$test_dir" app/src/io/freemytv/DebloatPolicy.java app/src/io/freemytv/HomeCommands.java app/src/io/freemytv/MemoryStats.java tests/*Test.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$test_dir" io.freemytv.DebloatPolicyTest
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$test_dir" io.freemytv.CompatibilityTest
