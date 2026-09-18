package com.datamap.scanner.persist;

import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.output.JsonWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class ApiSubmitter {
    public static int submit(ScanResult result, String apiUrl) throws Exception {
        String json = JsonWriter.toJson(result);
        HttpURLConnection conn = (HttpURLConnection) URI.create(apiUrl).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        // 触发请求并消费响应流，避免连接泄漏
        try {
            conn.getInputStream().close();
        } catch (IOException e) {
            // 4xx/5xx 时getErrorStream才可读；忽略响应体，只取状态码
            try { if (conn.getErrorStream() != null) conn.getErrorStream().close(); } catch (IOException ignore) {}
        }
        return conn.getResponseCode();
    }
}
