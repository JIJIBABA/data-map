package com.datamap.scanner.persist;

import com.datamap.scanner.model.ScanResult;
import com.datamap.scanner.output.JsonWriter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ApiSubmitter {
    public static int submit(ScanResult result, String apiUrl) throws Exception {
        String json = JsonWriter.toJson(result);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }
}
