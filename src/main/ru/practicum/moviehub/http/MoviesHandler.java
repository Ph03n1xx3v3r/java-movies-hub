package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoviesHandler extends BaseHttpHandler {
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
            if ("GET".equalsIgnoreCase(method)) {
                if (path.matches("/movies/\\d+")) {
                    handleGetById(ex);
                } else if (path.startsWith("/movies/")) {
                    sendError(ex, 400, "Некорректный ID");
                } else if (path.equals("/movies")) {
                    if (query != null) {
                        if (query.startsWith("year=")) {
                            handleGetByYear(ex, query);
                        } else {
                            sendError(ex, 400, "Некорректный параметр запроса");
                        }
                    } else {
                        handleGetAll(ex);
                    }
                } else {
                    sendError(ex, 405, "Метод не поддерживается для данного пути");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.equals("/movies")) {
                    handlePost(ex);
                } else {
                    sendError(ex, 405, "POST разрешён только для /movies");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                if (path.matches("/movies/\\d+")) {
                    handleDelete(ex);
                } else if (path.startsWith("/movies/")) {
                    sendError(ex, 400, "Некорректный ID");
                } else {
                    sendError(ex, 405, "DELETE разрешён только для /movies/{id}");
                }
            } else {
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

    private void handleGetAll(HttpExchange ex) throws IOException {
        List<Movie> movies = store.getAll();
        String json = JsonUtils.toJson(movies);
        sendJson(ex, 200, json);
    }

    private void handleGetByYear(HttpExchange ex, String query) throws IOException {
        String yearStr = query.substring(5);
        try {
            int year = Integer.parseInt(yearStr);
            List<Movie> movies = store.getByYear(year);
            String json = JsonUtils.toJson(movies);
            sendJson(ex, 200, json);
        } catch (NumberFormatException e) {
            // Используем дефис, чтобы точно совпадать с тестом
            sendError(ex, 400, "Некорректный параметр запроса - 'year'");
        }
    }

    private void handleGetById(HttpExchange ex) throws IOException {
        int id = extractId(ex);
        Optional<Movie> movie = store.getById(id);
        if (movie.isPresent()) {
            String json = JsonUtils.toJson(movie.get());
            sendJson(ex, 200, json);
        } else {
            sendError(ex, 404, "Фильм не найден");
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendError(ex, 415, "Неподдерживаемый Content-Type");
            return;
        }

        String body = readBody(ex);
        if (body == null || body.trim().isEmpty()) {
            sendError(ex, 400, "Тело запроса не должно быть пустым");
            return;
        }

        Movie movie;
        try {
            movie = JsonUtils.fromJson(body, Movie.class);
        } catch (Exception e) {
            List<String> details = new ArrayList<>();
            details.add("Некорректный JSON");
            sendError(ex, 400, "Ошибка валидации", details);
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

    private void handleDelete(HttpExchange ex) throws IOException {
        int id = extractId(ex);
        boolean deleted = store.delete(id);
        if (deleted) {
            sendNoContent(ex);
        } else {
            sendError(ex, 404, "Фильм не найден");
        }
    }

    private int extractId(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        String[] segments = path.split("/");
        String idStr = segments[segments.length - 1];
        return Integer.parseInt(idStr);
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            byte[] bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();
        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > 100) {
            errors.add("название должно быть не длиннее 100 символов");
        }
        int currentYear = Year.now().getValue();
        if (movie.getYear() < 1888 || movie.getYear() > currentYear + 1) {
            errors.add("год должен быть между 1888 и " + (currentYear + 1));
        }
        return errors;
    }
}