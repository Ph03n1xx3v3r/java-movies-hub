package ru.practicum.moviehub.http;

import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer();
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) server.stop();
    }

    @BeforeEach
    void setUp() {
        server.getStore().clear();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8", resp.headers().firstValue("Content-Type").orElse(""));
        assertEquals("[]", resp.body());
    }

    @Test
    void getMovies_returnsAddedMovies() throws Exception {
        String movieJson = "{\"title\":\"Inception\",\"year\":2010}";
        HttpRequest post = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        client.send(post, HttpResponse.BodyHandlers.discarding());

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Inception"));
        assertTrue(resp.body().contains("2010"));
    }

    @Test
    void postMovie_valid_returns201() throws Exception {
        String movieJson = "{\"title\":\"The Matrix\",\"year\":1999}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, resp.statusCode());
        assertTrue(resp.body().contains("\"id\""));
        assertTrue(resp.body().contains("The Matrix"));
        assertTrue(resp.body().contains("1999"));
        assertEquals("application/json; charset=UTF-8", resp.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void postMovie_emptyTitle_returns422() throws Exception {
        String movieJson = "{\"title\":\"\",\"year\":2000}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("название не должно быть пустым"));
    }

    @Test
    void postMovie_titleTooLong_returns422() throws Exception {
        String title = "a".repeat(101);
        String movieJson = "{\"title\":\"" + title + "\",\"year\":2000}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("название должно быть не длиннее 100 символов"));
    }

    @Test
    void postMovie_invalidYear_returns422() throws Exception {
        String movieJson = "{\"title\":\"Old\",\"year\":1800}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("год должен быть между"));
    }

    @Test
    void postMovie_wrongContentType_returns415() throws Exception {
        String movieJson = "{\"title\":\"Test\",\"year\":2020}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(415, resp.statusCode());
        assertTrue(resp.body().contains("Неподдерживаемый Content-Type"));
    }

    @Test
    void postMovie_invalidJson_returns400() throws Exception {
        String invalidJson = "{\"title\":\"Test\", \"year\":}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный JSON"));
    }

    @Test
    void getMovieById_existing_returnsMovie() throws Exception {
        String movieJson = "{\"title\":\"Interstellar\",\"year\":2014}";
        HttpRequest post = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        HttpResponse<String> postResp = client.send(post, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int id = extractId(postResp.body());

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Interstellar"));
    }

    @Test
    void getMovieById_notFound_returns404() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void getMovieById_invalidId_returns400() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void deleteMovie_existing_returns204() throws Exception {
        String movieJson = "{\"title\":\"DeleteMe\",\"year\":2021}";
        HttpRequest post = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movieJson))
                .build();
        HttpResponse<String> postResp = client.send(post, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int id = extractId(postResp.body());

        HttpRequest delete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + id))
                .DELETE()
                .build();
        HttpResponse<Void> resp = client.send(delete, HttpResponse.BodyHandlers.discarding());
        assertEquals(204, resp.statusCode());
        assertTrue(resp.headers().firstValue("Content-Type").isPresent());
        assertEquals("application/json; charset=UTF-8", resp.headers().firstValue("Content-Type").get());
    }

    @Test
    void deleteMovie_notFound_returns404() throws Exception {
        HttpRequest delete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(delete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void deleteMovie_invalidId_returns400() throws Exception {
        HttpRequest delete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(delete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void getMoviesByYear_returnsFiltered() throws Exception {
        String movie1 = "{\"title\":\"A\",\"year\":2000}";
        String movie2 = "{\"title\":\"B\",\"year\":2001}";
        HttpRequest post1 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movie1))
                .build();
        client.send(post1, HttpResponse.BodyHandlers.discarding());
        HttpRequest post2 = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(movie2))
                .build();
        client.send(post2, HttpResponse.BodyHandlers.discarding());

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2000"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("A"));
        assertFalse(resp.body().contains("B"));
    }

    @Test
    void getMoviesByYear_emptyResult_returnsEmptyArray() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=1900"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body());
    }

    private int extractId(String json) {
        int idIndex = json.indexOf("\"id\":");
        if (idIndex == -1) throw new AssertionError("No id field in response");
        int start = json.indexOf(":", idIndex) + 1;
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return Integer.parseInt(json.substring(start, end).trim());
    }
}