package com.example.clipbot_backend.util;

public class AssetUrlBuilder {
    public static String out(String objectKey) {
        return "/v1/files/out/" + objectKey; // frontend baseURL plakt de host erbij
    }
}
