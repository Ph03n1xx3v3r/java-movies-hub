package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;

public class MovieHubApp {
    public static void main(String[] args) {
        new MoviesServer().start();
    }
}