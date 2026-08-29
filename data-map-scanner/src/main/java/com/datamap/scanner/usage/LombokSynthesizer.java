package com.datamap.scanner.usage;

import javax.lang.model.element.TypeElement;

public class LombokSynthesizer {
    /** @Data/@Getter/@Setter 注解的实体，getter/setter 由 Lombok 生成（源码无 setter 方法）。 */
    public static boolean hasGetterSetter(TypeElement entity) {
        for (javax.lang.model.element.AnnotationMirror m : entity.getAnnotationMirrors()) {
            String s = m.getAnnotationType().toString();
            int i = s.lastIndexOf('.');
            String n = i >= 0 ? s.substring(i + 1) : s;
            if (n.equals("Data") || n.equals("Getter") || n.equals("Setter")) return true;
        }
        return false;
    }
}
