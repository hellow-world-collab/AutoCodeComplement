package com.system.demo.LLM;

import com.google.common.net.MediaType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class LLMClient {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String API_KEY = "YOUR_API_KEY";

    public static String queryLLM(String prompt) {
        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        json.put("model", "gpt-4o-mini");
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        json.put("messages", messages);

        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "调用API失败：" + response.code();
            }
            String responseBody = response.body().string();
            JSONObject result = new JSONObject(responseBody);
            return result
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
        } catch (IOException e) {
            e.printStackTrace();
            return "调用LLM出错：" + e.getMessage();
        }
    }
}

