package org.example.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class GeminiAI {

    private static final String API_KEY_FILE = "apikey.txt";
    private static String apiKey;

    public static void loadApiKey() {
        File file = new File(API_KEY_FILE);
        if (file.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                apiKey = br.readLine();
            } catch (IOException e) {
                System.out.println("Failed to read API key: " + e.getMessage());
            }
        }
    }

    public static void saveApiKey(String key) {
        apiKey = key;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(API_KEY_FILE))) {
            bw.write(apiKey);
        } catch (IOException e) {
            System.out.println("Failed to save API key: " + e.getMessage());
        }
    }

    public static boolean hasApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public static String askAI(String prompt) throws Exception {
        if (!hasApiKey()) throw new IllegalStateException("API key is missing.");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        String jsonBody = "{"
                + "  \"contents\": ["
                + "    {"
                + "      \"parts\": ["
                + "        {"
                + "          \"text\": \"" + prompt.replace("\"", "\\\"") + "\""
                + "        }"
                + "      ]"
                + "    }"
                + "  ]"
                + "}";

        Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey)
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Request failed: " + response);
            }

            String responseBody = response.body().string();

            Gson gson = new Gson();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            JsonArray candidates = jsonResponse.getAsJsonArray("candidates");
            if (candidates != null && candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                if (parts != null && parts.size() > 0) {
                    JsonObject firstPart = parts.get(0).getAsJsonObject();
                    JsonElement textElement = firstPart.get("text");
                    if (textElement != null) {
                        return textElement.getAsString();
                    }
                }
            }

            throw new IOException("Failed to parse AI response text from JSON: " + responseBody);
        }
    }

    public static void promptForApiKey() {
        System.out.print("Enter your Gemini API key: ");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String key = reader.readLine().trim();
            if (!key.isEmpty()) {
                saveApiKey(key);
                System.out.println("API key saved successfully.");
            } else {
                System.out.println("API key cannot be empty.");
            }
        } catch (IOException e) {
            System.out.println("Error reading input: " + e.getMessage());
        }
    }

    public static void changeApiKey() {
        System.out.print("Enter your new Gemini API key: ");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String newKey = reader.readLine().trim();
            if (!newKey.isEmpty()) {
                saveApiKey(newKey);
                System.out.println("API key updated successfully.");
            } else {
                System.out.println("API key cannot be empty.");
            }
        } catch (IOException e) {
            System.out.println("Error reading input: " + e.getMessage());
        }
    }
}