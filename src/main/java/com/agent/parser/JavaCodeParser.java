package com.agent.parser;

import com.agent.model.ApiMetadata;
import com.agent.model.DocumentSourceType;
import com.agent.model.Parameter;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Java代码解析器 - 提取API元数据
 */
public class JavaCodeParser {
    private static final Logger logger = LoggerFactory.getLogger(JavaCodeParser.class);
    private final JavaParser javaParser;

    public JavaCodeParser() {
        this.javaParser = new JavaParser();
    }

    public List<ApiMetadata> parse(Path javaFile) throws IOException {
        List<ApiMetadata> results = new ArrayList<>();

        try {
            CompilationUnit cu = javaParser.parse(javaFile).getResult()
                    .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String className = classDecl.getNameAsString();

                classDecl.getMethods().forEach(method -> {
                    ApiMetadata metadata = extractMethodMetadata(method, className, javaFile.toString());
                    results.add(metadata);
                });
            });
        } catch (Exception e) {
            logger.error("Error parsing file: {}", javaFile, e);
            throw new IOException("Parse error: " + javaFile, e);
        }

        return results;
    }

    private ApiMetadata extractMethodMetadata(MethodDeclaration method, String className, String filePath) {
        String methodName = method.getNameAsString();
        String signature = method.getSignature().asString();
        String returnType = method.getType().asString();

        List<Parameter> parameters = new ArrayList<>();
        method.getParameters().forEach(param -> {
            parameters.add(new Parameter(
                    param.getNameAsString(),
                    param.getType().asString(),
                    "",  // Parameter-level Javadoc not directly accessible in JavaParser
                    false  // default to not required
            ));
        });

        List<String> exceptions = new ArrayList<>();
        method.getThrownExceptions().forEach(ex -> exceptions.add(ex.asString()));

        String description = method.getJavadoc()
                .map(j -> j.getDescription().toText())
                .orElse(className + "." + methodName);

        return ApiMetadata.builder()
                .apiId(UUID.randomUUID().toString())
                .className(className)
                .methodName(methodName)
                .signature(signature)
                .returnType(returnType)
                .parameters(parameters)
                .exceptions(exceptions)
                .description(description)
                .sourceType(DocumentSourceType.INTERNAL_CODE)
                .sourceLocation(filePath)
                .build();
    }
}
