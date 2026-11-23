package com.example.application;

import java.net.http.HttpClient;

public class App {
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();
        System.out.println("Classpath is working. HTTP client version: " + client.version());
    }
}
