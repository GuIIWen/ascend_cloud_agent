package com.agent.service.testcase;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 对LLM生成的测试用例做最小可交付收口。
 */
class GeneratedTestcasePostProcessor {

    private static final Pattern TODO_PATTERN = Pattern.compile("(?i)\\bTODO\\b");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)(auth_token_placeholder|project_id_placeholder|access_key_placeholder|secret_key_placeholder|placeholder)");

    private final JavaParser javaParser = new JavaParser();

    String process(String javaCode) {
        if (javaCode == null || javaCode.trim().isEmpty()) {
            throw new IllegalStateException("Generated testcase code must not be blank");
        }

        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaCode);
        CompilationUnit compilationUnit = parseResult.getResult()
                .orElseThrow(() -> new IllegalStateException("Generated testcase code is not valid Java syntax"));

        ClassOrInterfaceDeclaration testClass = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() -> new IllegalStateException("Generated testcase code must contain a class"));

        boolean helperRequired = rewriteCommonPlaceholderFields(testClass);
        if (helperRequired) {
            ensureAssumptionsImport(compilationUnit);
            ensureRequiredConfigMethod(testClass);
        }

        String normalized = compilationUnit.toString();
        if (TODO_PATTERN.matcher(normalized).find()) {
            throw new IllegalStateException("Generated testcase code contains TODO placeholder");
        }
        if (PLACEHOLDER_PATTERN.matcher(normalized).find()) {
            throw new IllegalStateException("Generated testcase code contains unresolved placeholder");
        }

        ParseResult<CompilationUnit> reparsed = javaParser.parse(normalized);
        if (reparsed.getResult().isEmpty()) {
            throw new IllegalStateException("Generated testcase code failed validation after normalization");
        }
        return normalized.trim();
    }

    private boolean rewriteCommonPlaceholderFields(ClassOrInterfaceDeclaration testClass) {
        boolean helperRequired = false;
        for (FieldDeclaration field : testClass.getFields()) {
            if (!field.isStatic()) {
                continue;
            }
            if (!field.getElementType().isClassOrInterfaceType()
                    || !"String".equals(field.getElementType().asClassOrInterfaceType().getNameAsString())) {
                continue;
            }

            for (VariableDeclarator variable : field.getVariables()) {
                Optional<StringLiteralExpr> literalExpr = variable.getInitializer()
                        .flatMap(init -> init.toString().startsWith("\"")
                                ? init.toStringLiteralExpr()
                                : Optional.empty());
                if (literalExpr.isEmpty()) {
                    continue;
                }

                String fieldName = variable.getNameAsString();
                String literalValue = literalExpr.get().asString();
                if (!looksLikePlaceholder(literalValue)) {
                    continue;
                }

                MethodCallExpr replacement = switch (fieldName) {
                    case "AUTH_TOKEN", "ACCESS_TOKEN", "TOKEN" ->
                            requiredConfigCall("HUAWEICLOUD_AUTH_TOKEN", "hwcloud.auth.token");
                    case "PROJECT_ID" ->
                            requiredConfigCall("HUAWEICLOUD_PROJECT_ID", "hwcloud.project.id");
                    case "BASE_URL" ->
                            requiredConfigCall("HUAWEICLOUD_BASE_URL", "hwcloud.base.url");
                    default -> null;
                };

                if (replacement != null) {
                    variable.setInitializer(replacement);
                    helperRequired = true;
                }
            }
        }
        return helperRequired;
    }

    private MethodCallExpr requiredConfigCall(String envKey, String propertyKey) {
        return new MethodCallExpr("requiredConfig")
                .addArgument(new StringLiteralExpr(envKey))
                .addArgument(new StringLiteralExpr(propertyKey));
    }

    private boolean looksLikePlaceholder(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("placeholder")
                || normalized.startsWith("your_")
                || normalized.startsWith("replace_with_")
                || normalized.contains("to_be_filled");
    }

    private void ensureAssumptionsImport(CompilationUnit compilationUnit) {
        boolean alreadyImported = compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .anyMatch("org.junit.jupiter.api.Assumptions"::equals);
        if (!alreadyImported) {
            compilationUnit.addImport("org.junit.jupiter.api.Assumptions");
        }
    }

    private void ensureRequiredConfigMethod(ClassOrInterfaceDeclaration testClass) {
        boolean exists = testClass.getMethodsByName("requiredConfig").stream()
                .anyMatch(method -> method.getParameters().size() == 2);
        if (exists) {
            return;
        }

        MethodDeclaration method = testClass.addMethod("requiredConfig", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        method.setType("String");
        method.addParameter("String", "envKey");
        method.addParameter("String", "propertyKey");
        method.createBody()
                .addStatement("String value = System.getenv(envKey);")
                .addStatement("if (value == null || value.isBlank()) { value = System.getProperty(propertyKey); }")
                .addStatement("Assumptions.assumeTrue(value != null && !value.isBlank(), "
                        + "\"Missing required config: env \" + envKey + \" or -D\" + propertyKey);")
                .addStatement("return value;");
    }
}
