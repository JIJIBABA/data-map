package com.datamap.scanner.persist;

import com.datamap.scanner.model.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

public class ApiSubmitterTest {
    @Test
    public void postsJsonToServer() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/scan/result", ex -> {
            body.set(new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(200, -1); ex.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        ScanResult r = new ScanResult(new ScanProject("demo", "", ""), "FULL", Collections.emptyList());
        int code = ApiSubmitter.submit(r, "http://localhost:" + port + "/api/scan/result");
        server.stop(0);
        assertEquals(200, code);
        assertTrue(body.get().contains("\"appName\""));
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
