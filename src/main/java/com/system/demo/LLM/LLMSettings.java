package com.system.demo.LLM;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 插件设置存储
 */
@State(
        name = "LLMSettings",
        storages = @Storage("LLMSettings.xml")
)
public class LLMSettings implements PersistentStateComponent<LLMSettings> {
    public String apiUrl = "https://api.openai.com/v1/chat/completions";
    public String apiKey = "";
    public String model = "gpt-4o-mini";
    public int triggerDelayMs = 500;
    public int maxSuggestionLength = 150;

    public static LLMSettings getInstance() {
        return ServiceManager.getService(LLMSettings.class);
    }

    @Nullable
    @Override
    public LLMSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull LLMSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
