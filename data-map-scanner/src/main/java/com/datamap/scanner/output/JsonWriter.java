package com.datamap.scanner.output;

import com.datamap.scanner.model.ScanResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String toJson(ScanResult result) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    public static void write(ScanResult result, Path outFile) throws Exception {
        Files.write(outFile, toJson(result).getBytes(StandardCharsets.UTF_8));
    }
}
