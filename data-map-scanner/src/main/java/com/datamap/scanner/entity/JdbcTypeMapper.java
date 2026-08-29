package com.datamap.scanner.entity;

public class JdbcTypeMapper {
    public static String map(String javaTypeName) {
        String t = javaTypeName;
        int dot = t.lastIndexOf('.');
        if (dot >= 0) t = t.substring(dot + 1);
        switch (t) {
            case "String": return "VARCHAR";
            case "Long": case "long": case "Integer": case "int": return "BIGINT";
            case "BigDecimal": return "DECIMAL";
            case "Date": case "LocalDateTime": return "DATETIME";
            case "Boolean": case "boolean": return "TINYINT";
            case "byte": return "BLOB";
            case "Double": case "double": return "DOUBLE";
            case "Float": case "float": return "FLOAT";
            default: return "VARCHAR";
        }
    }
}
