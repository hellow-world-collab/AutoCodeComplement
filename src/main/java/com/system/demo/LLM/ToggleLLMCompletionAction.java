package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * 切换 AI 补全开关的 Action
 */
public class ToggleLLMCompletionAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        boolean before = LLMState.isEnabled();
        LLMState.toggle();
        boolean after = LLMState.isEnabled();

        Messages.showInfoMessage(
                "AI 自动补全已从 " + (before ? "启用" : "禁用") + " 切换为 " + (after ? "启用" : "禁用"),
                "Toggle AI Completion"
        );
    }
}

