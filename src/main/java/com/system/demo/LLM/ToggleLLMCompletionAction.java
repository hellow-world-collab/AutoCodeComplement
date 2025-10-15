package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * 切换 LLM 代码补全功能的开关
 * 使用复选标记显示当前状态
 */
public class ToggleLLMCompletionAction extends ToggleAction implements DumbAware {

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return LLMCompletionSettings.getInstance().isEnabled();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        LLMCompletionSettings.getInstance().setEnabled(state);
        
        // 显示通知
        String message = state ? "LLM 代码补全已启用" : "LLM 代码补全已禁用";
        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("LLM Completion")
            .createNotification(message, com.intellij.notification.NotificationType.INFORMATION)
            .notify(e.getProject());
    }
}
