package com.system.demo.LLM;

// 是否开启Al代码补全
public class LLMState {
    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}

