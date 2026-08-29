package com.datamap.scanner.callgraph;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.javac.JavaParser;
import org.junit.jupiter.api.Test;
import javax.lang.model.element.TypeElement;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

public class SpringInterfaceBinderTest {
    private AnalysisContext ctx() throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/demo");
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        return JavaParser.parse(files, "");
    }

    @Test
    public void bindsInterfaceToSingleConcreteImpl() throws Exception {
        AnalysisContext ctx = ctx();
        Map<TypeElement, TypeElement> bind = SpringInterfaceBinder.bind(ctx);

        TypeElement service = ctx.elements.getTypeElement("com.example.service.OrderService");
        TypeElement impl = ctx.elements.getTypeElement("com.example.service.impl.OrderServiceImpl");
        assertNotNull(service);
        assertNotNull(impl);

        assertTrue(bind.containsKey(service), "OrderService should be bound");
        assertEquals(impl, bind.get(service), "OrderService should map to its single concrete impl");
    }
}
