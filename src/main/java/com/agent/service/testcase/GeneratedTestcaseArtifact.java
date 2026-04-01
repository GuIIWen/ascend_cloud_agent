package com.agent.service.testcase;

import java.nio.file.Path;
import java.util.Objects;

public class GeneratedTestcaseArtifact {

    private final String fqcn;
    private final Path sourceFile;
    private final Path classesDirectory;
    private final Path compileLogFile;

    public GeneratedTestcaseArtifact(
            String fqcn,
            Path sourceFile,
            Path classesDirectory,
            Path compileLogFile) {
        this.fqcn = Objects.requireNonNull(fqcn, "fqcn");
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.classesDirectory = Objects.requireNonNull(classesDirectory, "classesDirectory");
        this.compileLogFile = Objects.requireNonNull(compileLogFile, "compileLogFile");
    }

    public String getFqcn() {
        return fqcn;
    }

    public Path getSourceFile() {
        return sourceFile;
    }

    public Path getClassesDirectory() {
        return classesDirectory;
    }

    public Path getCompileLogFile() {
        return compileLogFile;
    }
}
