package com.datamap.scanner.persist;

import com.datamap.scanner.model.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

public class ApiSubmitterTest {
    @Test
    public void postsJsonToServer() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/scan/result", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes()));
            ex.sendResponseHeaders(200, -1); ex.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        ScanResult r = new ScanResult(new ScanProject("demo", "", ""), "FULL", List.of());
        int code = ApiSubmitter.submit(r, "http://localhost:" + port + "/api/scan/result");
        server.stop(0);
        assertEquals(200, code);
        assertTrue(body.get().contains("\"appName\""));
    }
}
