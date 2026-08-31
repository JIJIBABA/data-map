package com.datamap.scanner.javac;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class JavaParserTest {

    private AnalysisContext parseFixtures() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void parsesAllFixtureUnits() throws Exception {
        AnalysisContext ctx = parseFixtures();
        assertEquals(5, ctx.units.size());
    }

    @Test
    public void resolvesLocalFieldElement() throws Exception {
        AnalysisContext ctx = parseFixtures();
        // 找 Order 类里 userId 字段，验证能通过元素名找到（字段在本地可解析）
        boolean found = false;
        for (CompilationUnitTree cu : ctx.units) {
            if (cu.getSourceFile().getName().endsWith("Order.java")) {
                Tree classTree = cu.getTypeDecls().get(0);
                Element classElement = ctx.resolve(cu, classTree);
                assertTrue(classElement instanceof TypeElement,
                        "Order 类的 ClassTree 应解析为 TypeElement");
                for (Element e : ctx.elements.getAllMembers((TypeElement) classElement)) {
                    if (e.getSimpleName().contentEquals("userId")) { found = true; }
                }
            }
        }
        assertTrue(found);
    }
}
