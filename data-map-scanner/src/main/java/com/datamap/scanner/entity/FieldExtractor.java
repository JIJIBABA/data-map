package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.ScanField;
import com.datamap.scanner.util.DocTreeText;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

public class FieldExtractor {
    public static List<ScanField> extract(TypeElement entity, AnalysisContext ctx) {
        List<ScanField> fields = new ArrayList<>();
        for (VariableElement field : fieldsOf(entity, ctx)) {
            if (field.getKind() != ElementKind.FIELD) continue;
            if (field.getModifiers().contains(Modifier.STATIC)) continue;
            String column = camelToSnake(field.getSimpleName().toString());
            boolean pk = hasAnnotation(ctx, field, "TableId");
            String comment = DocTreeText.fullBody(field, ctx);
            String jdbcType = JdbcTypeMapper.map(field.asType().toString());
            fields.add(new ScanField(column, comment, jdbcType, pk, CommonFieldFilter.isBusiness(column)));
        }
        return fields;
    }

    private static List<VariableElement> fieldsOf(TypeElement entity, AnalysisContext ctx) {
        List<VariableElement> list = new ArrayList<>();
        for (Element e : ctx.elements.getAllMembers(entity)) {
            if (e instanceof VariableElement) list.add((VariableElement) e);
        }
        return list;
    }

    static boolean hasAnnotation(AnalysisContext ctx, Element e, String simpleAnnoName) {
        for (javax.lang.model.element.AnnotationMirror m : e.getAnnotationMirrors()) {
            String q = m.getAnnotationType().toString();
            if (EntityResolver.simpleName(q).equals(simpleAnnoName)) return true;
        }
        return false;
    }

    static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) { sb.append('_').append(Character.toLowerCase(c)); }
            else sb.append(c);
        }
        return sb.toString();
    }
}
