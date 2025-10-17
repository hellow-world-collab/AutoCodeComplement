package com.system.demo.LLM;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

/**
 * 大模型LLM部分，到时候把queryLLM更改为本地
 */
public class LLMClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static String queryLLM(String prompt) {
        LLMSettings settings = LLMSettings.getInstance();

        String apiKey = settings.apiKey;
        String apiUrl = settings.apiUrl;
        String model = settings.model;

        if (apiKey == null || apiKey.isEmpty()) {
            return "错误：未设置 API Key，请在设置中配置。";
        }

        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = "https://api.openai.com/v1/chat/completions";
        }

        // 替换为你的代理地址和端口，例如 127.0.0.1:7897
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7897));

        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(proxy) // 如果不需要代理，可删除这一行
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(5))
                .build();

        JSONObject json = new JSONObject();
        json.put("model", model != null && !model.isEmpty() ? model : "gpt-4o-mini");
        json.put("max_tokens", 100); // 限制响应长度
        json.put("temperature", 0.1); // 降低随机性，提高一致性

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "你是一个专业的代码助手。请只返回代码补全内容，不要包含任何解释或注释。"));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        json.put("messages", messages);

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "调用API失败：" + response.code() + " - " + response.message();
            }
            if (response.body() == null) {
                return "响应体为空。";
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
//    public static String queryLLM(String prompt) {
//        try {
//            OkHttpClient client = new OkHttpClient();
//
//            JSONObject json = new JSONObject();
//            json.put("model", "llama3"); // 或你的模型名
//            json.put("prompt", prompt);
//            json.put("stream", false);
//
//            RequestBody body = RequestBody.create(json.toString(), JSON);
//            Request request = new Request.Builder()
//                    .url("http://localhost:11434/api/generate") // ✅ 本地模型地址
//                    .post(body)
//                    .build();
//
//            try (Response response = client.newCall(request).execute()) {
//                if (!response.isSuccessful()) {
//                    return "调用本地模型失败：" + response.code();
//                }
//                if (response.body() == null) {
//                    return "本地模型响应为空。";
//                }
//
//                String responseBody = response.body().string();
//                JSONObject result = new JSONObject(responseBody);
//                return result.optString("response", "").trim();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            return "调用本地模型出错：" + e.getMessage();
//        }
//    }

}
