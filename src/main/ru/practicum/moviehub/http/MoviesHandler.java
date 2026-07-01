package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

public class MoviesHandler extends BaseHttpHandler {
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MIN_YEAR = 1888;
    private static final int YEAR_OFFSET = 1;

    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        try {
            switch (method.toUpperCase()) {
                case "GET":
                    handleGet(ex, path, query);
                    break;
                case "POST":
                    handlePost(ex, path);
                    break;
                case "DELETE":
                    handleDelete(ex, path);
                    break;
                default:
                    sendError(ex, 405, "Метод не поддерживается");
            }
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (Exception e) {
            sendError(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleGet(HttpExchange ex, String path, String query) throws IOException {
        if (path.equals("/movies")) {
            if (query == null) {
                handleGetAll(ex);
            } else {
                Map<String, String> params = parseQuery(query);
                if (params.containsKey("year")) {
                    handleGetByYear(ex, params.get("year"));
                } else {
                    sendError(ex, 400, "Некорректный параметр запроса");
                }
            }
        } else if (path.matches("/movies/\\d+")) {
            handleGetById(ex, path);
        } else if (path.startsWith("/movies/")) {
            sendError(ex, 400, "Некорректный ID");
        } else {
            sendError(ex, 405, "Метод не поддерживается для данного пути");
        }
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        List<Movie> movies = store.getAll();
        String json = JsonUtils.toJson(movies);
        sendJson(ex, 200, json);
    }

    private void handleGetByYear(HttpExchange ex, String yearStr) throws IOException {
        try {
            int year = Integer.parseInt(yearStr);
            List<Movie> movies = store.getByYear(year);
            String json = JsonUtils.toJson(movies);
            sendJson(ex, 200, json);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный параметр запроса - 'year'");
        }
    }

    private void handleGetById(HttpExchange ex, String path) throws IOException {
        int id = extractId(path);
        Optional<Movie> movie = store.getById(id);
        if (movie.isPresent()) {
            String json = JsonUtils.toJson(movie.get());
            sendJson(ex, 200, json);
        } else {
            sendError(ex, 404, "Фильм не найден");
        }
    }

    private void handlePost(HttpExchange ex, String path) throws IOException {
        if (!path.equals("/movies")) {
            sendError(ex, 405, "POST разрешён только для /movies");
            return;
        }

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendError(ex, 415, "Неподдерживаемый Content-Type");
            return;
        }

        Movie movie;
        try (InputStreamReader reader = new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)) {
            movie = JsonUtils.fromJson(reader, Movie.class);
        } catch (Exception e) {
            sendError(ex, 400, "Ошибка валидации", Collections.singletonList("Некорректный JSON"));
            return;
        }

        List<String> errors = validateMovie(movie);
        if (!errors.isEmpty()) {
            sendError(ex, 422, "Ошибка валидации", errors);
            return;
        }

        Movie saved = store.add(movie);
        String json = JsonUtils.toJson(saved);
        ex.getResponseHeaders().set("Location", "/movies/" + saved.getId());
        sendJson(ex, 201, json);
    }

    private void handleDelete(HttpExchange ex, String path) throws IOException {
        if (path.matches("/movies/\\d+")) {
            int id = extractId(path);
            boolean deleted = store.delete(id);
            if (deleted) {
                sendNoContent(ex);
            } else {
                sendError(ex, 404, "Фильм не найден");
            }
        } else if (path.startsWith("/movies/")) {
            sendError(ex, 400, "Некорректный ID");
        } else {
            sendError(ex, 405, "DELETE разрешён только для /movies/{id}");
        }
    }

    private int extractId(String path) {
        String[] segments = path.split("/");
        String idStr = segments[segments.length - 1];
        return Integer.parseInt(idStr);
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("="))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> parts[1],
                        (a, b) -> a
                ));
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();
        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            errors.add("название должно быть не длиннее " + MAX_TITLE_LENGTH + " символов");
        }
        int currentYear = Year.now().getValue();
        if (movie.getYear() < MIN_YEAR || movie.getYear() > currentYear + YEAR_OFFSET) {
            errors.add("год должен быть между " + MIN_YEAR + " и " + (currentYear + YEAR_OFFSET));
        }
        return errors;
    }
}