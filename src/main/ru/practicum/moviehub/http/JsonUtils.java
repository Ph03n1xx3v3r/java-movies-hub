package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;

public class JsonUtils {
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) throws IOException {
        return gson.fromJson(json, clazz);
    }

    public static <T> T fromJson(Reader reader, Class<T> clazz) throws IOException {
        return gson.fromJson(reader, clazz);
    }
}