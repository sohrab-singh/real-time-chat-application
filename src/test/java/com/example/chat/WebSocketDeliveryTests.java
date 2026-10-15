package com.example.chat;

import com.example.chat.service.DirectMessageService;
import com.example.chat.service.UserService;
import com.example.chat.ws.SessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketDeliveryTests {

    @LocalServerPort
    int port;

    @Autowired
    DirectMessageService dms;

    @Autowired
    UserService users;

    @Autowired
    SessionRegistry registry;

    @Autowired
    JsonMapper json;

    private final List<WebSocketSession> open = new ArrayList<>();

    @AfterEach
    void closeSessions() throws Exception {
        for (WebSocketSession s : open) {
            s.close();
        }
    }

    /** Connects as the user and returns a queue of the text frames it receives. */
    private BlockingQueue<String> connect(String user) throws Exception {
        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        WebSocketSession session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                inbox.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws?user=" + user).get(5, TimeUnit.SECONDS);
        open.add(session);
        // The client handshake can finish before the server has registered the session
        long deadline = System.currentTimeMillis() + 5_000;
        while (registry.sessionsOf(user).isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(registry.sessionsOf(user)).isNotEmpty();
        return inbox;
    }

    private HttpResponse<String> send(Long conversationId, String sender, String content) throws Exception {
        String body = json.writeValueAsString(Map.of("sender", sender, "content", content));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/conversations/" + conversationId + "/messages"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void membersReceiveTheMessageAndNonMemberDoesNot() throws Exception {
        String alice = "alice-" + System.nanoTime();
        String bob = "bob-" + System.nanoTime();
        String carol = "carol-" + System.nanoTime();
        Long dmId = dms.getOrCreate(alice, bob).id();
        users.getOrCreate(carol);

        BlockingQueue<String> aliceInbox = connect(alice);
        BlockingQueue<String> bobInbox = connect(bob);
        BlockingQueue<String> carolInbox = connect(carol);

        HttpResponse<String> response = send(dmId, alice, "hello bob");
        assertThat(response.statusCode()).isEqualTo(201);

        for (BlockingQueue<String> inbox : List.of(aliceInbox, bobInbox)) {
            String frame = inbox.poll(5, TimeUnit.SECONDS);
            assertThat(frame).as("member should receive the push").isNotNull();
            JsonNode node = json.readTree(frame);
            assertThat(node.get("type").asString()).isEqualTo("message");
            assertThat(node.get("message").get("conversationId").asLong()).isEqualTo(dmId);
            assertThat(node.get("message").get("seq").asLong()).isEqualTo(1);
            assertThat(node.get("message").get("sender").asString()).isEqualTo(alice);
            assertThat(node.get("message").get("content").asString()).isEqualTo("hello bob");
        }
        // Members got theirs, so a push to carol would have arrived by now as well
        assertThat(carolInbox.poll(500, TimeUnit.MILLISECONDS)).as("non-member must not receive").isNull();
    }

    @Test
    void rejectedSendPushesNothing() throws Exception {
        String alice = "alice-" + System.nanoTime();
        String bob = "bob-" + System.nanoTime();
        String carol = "carol-" + System.nanoTime();
        Long dmId = dms.getOrCreate(alice, bob).id();
        users.getOrCreate(carol);
        BlockingQueue<String> aliceInbox = connect(alice);

        assertThat(send(dmId, carol, "let me in").statusCode()).isEqualTo(403);
        assertThat(aliceInbox.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }
}
