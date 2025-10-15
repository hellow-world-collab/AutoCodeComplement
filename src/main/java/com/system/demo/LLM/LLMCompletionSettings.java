package com.system.demo.LLM;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 持久化保存 LLM 代码补全的开关状态
 */
@State(
    name = "LLMCompletionSettings",
    storages = @Storage("LLMCompletionSettings.xml")
)
public class LLMCompletionSettings implements PersistentStateComponent<LLMCompletionSettings.State> {

    public static class State {
        public boolean enabled = false; // 默认关闭
    }

    private State state = new State();

    public static LLMCompletionSettings getInstance() {
        return ApplicationManager.getApplication().getService(LLMCompletionSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    public void setEnabled(boolean enabled) {
        state.enabled = enabled;
    }

    public void toggle() {
        state.enabled = !state.enabled;
    }
}
