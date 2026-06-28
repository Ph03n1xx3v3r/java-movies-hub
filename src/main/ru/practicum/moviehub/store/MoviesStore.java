package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;
import java.util.*;

public class MoviesStore {
    private final Map<Integer, Movie> storage = new HashMap<>();
    private int nextId = 1;

    public List<Movie> getAll() {
        return new ArrayList<>(storage.values());
    }

    public Movie add(Movie movie) {
        movie.setId(nextId++);
        storage.put(movie.getId(), movie);
        return movie;
    }

    public Optional<Movie> getById(int id) {
        return Optional.ofNullable(storage.get(id));
    }

    public boolean delete(int id) {
        return storage.remove(id) != null;
    }

    public List<Movie> getByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie m : storage.values()) {
            if (m.getYear() == year) result.add(m);
        }
        return result;
    }

    public void clear() {
        storage.clear();
        nextId = 1;
    }
}