#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash scripts/install_generated_test_runner.sh

What it prepares:
  1. Resolves Java 21
  2. Validates local JUnit 5 compile/runtime jars from ~/.m2
  3. Writes a minimal GeneratedJUnitRunner.java under ASCEND_AGENT_HOME
  4. Compiles the runner for later execute-mode use
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

AGENT_HOME="${ASCEND_AGENT_HOME:-$(pwd)/.ascend_agent}"
RUNNER_HOME="${ASCEND_AGENT_RUNNER_HOME:-$AGENT_HOME/tools/generated-test-runner}"
RUNNER_SRC_DIR="$RUNNER_HOME/src"
RUNNER_CLASSES_DIR="$RUNNER_HOME/classes"
RUNNER_SOURCE="$RUNNER_SRC_DIR/GeneratedJUnitRunner.java"
RUNNER_CLASSPATH_FILE="$RUNNER_HOME/junit.classpath"
DEFAULT_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"

extract_java_major() {
  local java_bin="$1"
  "$java_bin" -version 2>&1 | awk -F '[\".]' '/version/ { if ($2 == "1") { print $3 } else { print $2 } exit }'
}

resolve_javac_bin() {
  local default_candidate="$DEFAULT_JAVA_HOME/bin/javac"
  local env_candidate=""
  local command_candidate=""
  local major=""

  if [[ -x "$default_candidate" ]]; then
    major="$(extract_java_major "$DEFAULT_JAVA_HOME/bin/java")"
    if [[ -n "$major" && "$major" -ge 21 ]]; then
      printf '%s\n' "$default_candidate"
      return 0
    fi
  fi

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/javac" && -x "${JAVA_HOME}/bin/java" ]]; then
    env_candidate="${JAVA_HOME}/bin/javac"
    major="$(extract_java_major "${JAVA_HOME}/bin/java")"
    if [[ -n "$major" && "$major" -ge 21 ]]; then
      printf '%s\n' "$env_candidate"
      return 0
    fi
  fi

  command_candidate="$(command -v javac || true)"
  if [[ -n "$command_candidate" ]]; then
    local java_bin
    java_bin="$(dirname "$command_candidate")/java"
    if [[ -x "$java_bin" ]]; then
      major="$(extract_java_major "$java_bin")"
      if [[ -n "$major" && "$major" -ge 21 ]]; then
        printf '%s\n' "$command_candidate"
        return 0
      fi
    fi
  fi
  return 1
}

JAVAC_BIN="$(resolve_javac_bin || true)"
if [[ -z "$JAVAC_BIN" || ! -x "$JAVAC_BIN" ]]; then
  echo "javac for Java 21 not found; install Java 21 first" >&2
  exit 1
fi

JUNIT_API="$HOME/.m2/repository/org/junit/jupiter/junit-jupiter-api/5.10.1/junit-jupiter-api-5.10.1.jar"
JUNIT_COMMONS="$HOME/.m2/repository/org/junit/platform/junit-platform-commons/1.10.1/junit-platform-commons-1.10.1.jar"
OPENTEST4J="$HOME/.m2/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar"
APIGUARDIAN="$HOME/.m2/repository/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"

for dep in "$JUNIT_API" "$JUNIT_COMMONS" "$OPENTEST4J" "$APIGUARDIAN"; do
  if [[ ! -f "$dep" ]]; then
    echo "missing dependency: $dep" >&2
    echo "run 'mvn -q -DskipTests test-compile' or 'mvn -q -DskipTests package' first" >&2
    exit 1
  fi
done

mkdir -p "$RUNNER_SRC_DIR" "$RUNNER_CLASSES_DIR"

cat >"$RUNNER_SOURCE" <<'EOF'
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class GeneratedJUnitRunner {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: GeneratedJUnitRunner <fqcn>");
            System.exit(2);
        }

        Class<?> testClass = Class.forName(args[0]);
        invokeBeforeAll(testClass);

        Object instance = testClass.getDeclaredConstructor().newInstance();
        int passed = 0;
        for (Method method : testClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Test.class)) {
                continue;
            }
            method.setAccessible(true);
            try {
                method.invoke(instance);
                System.out.println("PASS " + method.getName());
                passed++;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                System.out.println("FAIL " + method.getName() + " -> "
                        + cause.getClass().getName() + ": " + cause.getMessage());
                cause.printStackTrace(System.out);
                System.exit(1);
            }
        }

        if (passed == 0) {
            System.err.println("no @Test method found");
            System.exit(3);
        }

        System.out.println("PASSED " + passed);
    }

    private static void invokeBeforeAll(Class<?> testClass) throws Exception {
        for (Method method : testClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(BeforeAll.class)) {
                continue;
            }
            method.setAccessible(true);
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                System.out.println("BEFORE_ALL_FAIL " + method.getName() + " -> "
                        + cause.getClass().getName() + ": " + cause.getMessage());
                cause.printStackTrace(System.out);
                System.exit(1);
            }
        }
    }
}
EOF

RUNNER_CP="$JUNIT_API:$JUNIT_COMMONS:$OPENTEST4J:$APIGUARDIAN"
"$JAVAC_BIN" -cp "$RUNNER_CP" -d "$RUNNER_CLASSES_DIR" "$RUNNER_SOURCE"
printf '%s\n' "$RUNNER_CP" >"$RUNNER_CLASSPATH_FILE"

echo "runner source: $RUNNER_SOURCE"
echo "runner classes: $RUNNER_CLASSES_DIR"
echo "runner classpath file: $RUNNER_CLASSPATH_FILE"
echo "javac: $JAVAC_BIN"
