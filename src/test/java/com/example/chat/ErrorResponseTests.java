package com.example.chat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorResponseTests {

    @LocalServerPort
    int port;

    @Autowired
    JsonMapper json;

    @ParameterizedTest(name = "{0} {1} -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            PUT    | /api/rooms/999999/members/alice          |                                   | 404
            PUT    | /api/rooms/abc/members/alice             |                                   | 400
            PUT    | /api/rooms/1/members/not%20valid         |                                   | 400
            POST   | /api/dms                                 | {not json                         | 400
            POST   | /api/dms                                 | {"userA":"same","userB":"same"}   | 400
            POST   | /api/conversations/999999/messages       | {"sender":"x","content":"hi"}     | 403
            GET    | /api/conversations/1/messages            |                                   | 400
            GET    | /api/conversations/1/messages?user=x&limit=0 |                               | 400
            DELETE | /api/rooms                               |                                   | 405
            GET    | /api/nope                                |                                   | 404
            """)
    void errorsAreProblemJsonWithTheRightStatus(String method, String path, String body, int status) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));

        HttpResponse<String> response;
        try (HttpClient http = HttpClient.newHttpClient()) {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        JsonNode problem = json.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("title").asString()).isNotBlank();
        assertThat(problem.get("instance").asString()).isEqualTo(path.replaceAll("\\?.*", ""));
    }
}
