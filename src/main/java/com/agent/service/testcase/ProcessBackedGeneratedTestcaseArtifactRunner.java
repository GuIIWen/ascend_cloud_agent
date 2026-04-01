package com.agent.service.testcase;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessBackedGeneratedTestcaseArtifactRunner implements GeneratedTestcaseArtifactRunner {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*;", Pattern.MULTILINE);
    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Path DEFAULT_JAVA_HOME = Paths.get("/usr/lib/jvm/java-21-openjdk-amd64");
    private static final String RUNNER_CLASS_NAME = "GeneratedJUnitRunner";
    private static final String RUNNER_SOURCE = """
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
            """;
    private static final List<String> JUNIT_DEPENDENCY_PATHS = List.of(
            "org/junit/jupiter/junit-jupiter-api/5.10.1/junit-jupiter-api-5.10.1.jar",
            "org/junit/platform/junit-platform-commons/1.10.1/junit-platform-commons-1.10.1.jar",
            "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar",
            "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar");

    private final Path runnerHome;
    private final Path mavenRepository;

    public ProcessBackedGeneratedTestcaseArtifactRunner(Path runnerHome) {
        this(runnerHome, Paths.get(System.getProperty("user.home"), ".m2", "repository"));
    }

    ProcessBackedGeneratedTestcaseArtifactRunner(Path runnerHome, Path mavenRepository) {
        this.runnerHome = Objects.requireNonNull(runnerHome, "runnerHome");
        this.mavenRepository = Objects.requireNonNull(mavenRepository, "mavenRepository");
    }

    @Override
    public GeneratedTestcaseArtifact compile(Path runDirectory, String javaTestCode) {
        Objects.requireNonNull(runDirectory, "runDirectory");
        if (javaTestCode == null || javaTestCode.trim().isEmpty()) {
            throw new IllegalArgumentException("javaTestCode must not be blank");
        }

        ensureDirectory(runDirectory.resolve("src"));
        ensureDirectory(runDirectory.resolve("classes"));
        ensureRunnerInstalled();

        String className = extractPublicClassName(javaTestCode);
        String packageName = extractPackageName(javaTestCode);
        String fqcn = packageName == null ? className : packageName + "." + className;
        Path sourceDirectory = packageName == null
                ? runDirectory.resolve("src")
                : runDirectory.resolve("src").resolve(packageName.replace('.', '/'));
        ensureDirectory(sourceDirectory);
        Path sourceFile = sourceDirectory.resolve(className + ".java");
        Path compileLogFile = runDirectory.resolve("compile.log");
        writeString(sourceFile, javaTestCode);

        String runnerClasspath = buildRunnerClasspath();
        Path runnerClassesDirectory = runnerHome.resolve("classes");
        compileJava(
                sourceFile,
                runDirectory.resolve("classes"),
                runnerClasspath + java.io.File.pathSeparator + runnerClassesDirectory,
                compileLogFile);
        return new GeneratedTestcaseArtifact(
                fqcn,
                sourceFile,
                runDirectory.resolve("classes"),
                compileLogFile);
    }

    @Override
    public Map<String, Object> runTests(
            Path runDirectory,
            GeneratedTestcaseArtifact artifact,
            ProvisionedTestResources provisionedResources) {
        Objects.requireNonNull(runDirectory, "runDirectory");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(provisionedResources, "provisionedResources");

        Path stdoutFile = runDirectory.resolve("test.stdout.log");
        Path stderrFile = runDirectory.resolve("test.stderr.log");
        String runnerClasspath = buildRunnerClasspath();
        Path runnerClassesDirectory = runnerHome.resolve("classes");

        List<String> command = new ArrayList<>();
        command.add(resolveJavaBinary().toString());
        for (Map.Entry<String, String> entry : provisionedResources.getSystemProperties().entrySet()) {
            command.add("-D" + entry.getKey() + "=" + entry.getValue());
        }
        command.add("-cp");
        command.add(
                runnerClassesDirectory
                        + java.io.File.pathSeparator
                        + artifact.getClassesDirectory()
                        + java.io.File.pathSeparator
                        + runnerClasspath);
        command.add(RUNNER_CLASS_NAME);
        command.add(artifact.getFqcn());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(runDirectory.toFile());
        processBuilder.redirectOutput(stdoutFile.toFile());
        processBuilder.redirectError(stderrFile.toFile());
        processBuilder.environment().putAll(provisionedResources.getEnvironmentVariables());

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("stdoutFile", stdoutFile.toString());
            details.put("stderrFile", stderrFile.toString());
            details.put("exitCode", exitCode);
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "Generated testcase execution failed with exitCode "
                                + exitCode
                                + ". See "
                                + stdoutFile);
            }
            return details;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start generated testcase runner", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Generated testcase runner interrupted", ex);
        }
    }

    private void ensureRunnerInstalled() {
        ensureDirectory(runnerHome.resolve("src"));
        ensureDirectory(runnerHome.resolve("classes"));
        writeString(runnerHome.resolve("src").resolve(RUNNER_CLASS_NAME + ".java"), RUNNER_SOURCE);
        Path classpathFile = runnerHome.resolve("junit.classpath");
        String runnerClasspath = buildRunnerClasspath();
        writeString(classpathFile, runnerClasspath + System.lineSeparator());
        compileJava(
                runnerHome.resolve("src").resolve(RUNNER_CLASS_NAME + ".java"),
                runnerHome.resolve("classes"),
                runnerClasspath,
                runnerHome.resolve("runner-compile.log"));
    }

    private void compileJava(Path sourceFile, Path outputDirectory, String classpath, Path logFile) {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        if (javaCompiler == null) {
            throw new IllegalStateException("Java compiler not available; run the service on a JDK");
        }
        ensureDirectory(outputDirectory);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     javaCompiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));
            List<String> options = List.of(
                    "--release", "21",
                    "-cp", classpath,
                    "-d", outputDirectory.toString());
            boolean ok = javaCompiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    options,
                    null,
                    compilationUnits).call();
            writeDiagnostics(logFile, diagnostics.getDiagnostics());
            if (!ok) {
                throw new IllegalStateException("Compilation failed. See " + logFile);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to compile " + sourceFile, ex);
        }
    }

    private void writeDiagnostics(Path logFile, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        StringBuilder builder = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            builder.append(diagnostic.getKind())
                    .append(": ")
                    .append(diagnostic.getMessage(null))
                    .append(" @ ")
                    .append(diagnostic.getLineNumber())
                    .append(':')
                    .append(diagnostic.getColumnNumber())
                    .append(System.lineSeparator());
        }
        writeString(logFile, builder.toString());
    }

    private String buildRunnerClasspath() {
        List<String> classpathEntries = new ArrayList<>();
        List<String> missingDependencies = new ArrayList<>();
        for (String relativePath : JUNIT_DEPENDENCY_PATHS) {
            Path dependency = mavenRepository.resolve(relativePath);
            if (Files.exists(dependency)) {
                classpathEntries.add(dependency.toString());
            } else {
                missingDependencies.add(dependency.toString());
            }
        }
        if (!missingDependencies.isEmpty()) {
            throw new IllegalStateException(
                    "Missing generated testcase runtime dependencies: "
                            + String.join(", ", missingDependencies)
                            + ". Run 'mvn -q -DskipTests test-compile' first.");
        }
        return String.join(java.io.File.pathSeparator, classpathEntries);
    }

    private Path resolveJavaBinary() {
        Path defaultCandidate = DEFAULT_JAVA_HOME.resolve("bin").resolve("java");
        if (Files.isExecutable(defaultCandidate)) {
            return defaultCandidate;
        }
        Path javaHomeCandidate = Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java");
        if (Files.isExecutable(javaHomeCandidate)) {
            return javaHomeCandidate;
        }
        throw new IllegalStateException("Java executable not found for generated testcase execution");
    }

    private String extractPackageName(String javaTestCode) {
        Matcher matcher = PACKAGE_PATTERN.matcher(javaTestCode);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractPublicClassName(String javaTestCode) {
        Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(javaTestCode);
        if (!matcher.find()) {
            throw new IllegalStateException("Generated testcase code does not contain a public class");
        }
        String className = matcher.group(1);
        if (matcher.find()) {
            throw new IllegalStateException("Generated testcase code contains multiple public classes");
        }
        return className;
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create directory: " + directory, ex);
        }
    }

    private void writeString(Path file, String value) {
        try {
            Files.writeString(file, value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write file: " + file, ex);
        }
    }
}
