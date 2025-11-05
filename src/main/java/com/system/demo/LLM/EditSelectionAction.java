package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.system.demo.utils.EditorContextUtils;
import org.jetbrains.annotations.NotNull;


/**
 * 选中代码发送到 LLM → 弹窗显示差异（模态对话框） → 用户确认应用
 */
public class EditSelectionAction extends AnAction {
    private static int lastSelectionStart;
    private static int lastSelectionEnd;
    private static Editor lastEditor;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null || project == null) return;

        String selected = EditorContextUtils.getSelectedText(editor);
        if (selected == null || selected.isEmpty()) {
            Messages.showWarningDialog(project, "请先选中代码。", "提示");
            return;
        }

        // 保存选中位置
        SelectionModel selectionModel = editor.getSelectionModel();
        lastSelectionStart = selectionModel.getSelectionStart();
        lastSelectionEnd = selectionModel.getSelectionEnd();
        lastEditor = editor;

        // 显示进度提示
        Messages.showInfoMessage(project, "正在分析代码，请稍候...", "AI 分析");

        // 在后台线程调用 LLM
        final String selectedText = selected;
        new Thread(() -> {

            // 获取 actionId 来区分不同功能
            String actionId = ActionManager.getInstance().getId(this);

            String prompt;
            if ("CommentSelectionWithAI".equalsIgnoreCase(actionId)) {
                // Shift + Alt + 3  给代码加注释
                prompt = EditorContextUtils.buildContextPromptForComment(file, selectedText);
            } else {
                // 默认：Shift + Alt + 1  改进代码
                prompt = EditorContextUtils.buildContextPrompt(file, selectedText);
            }

            String context = EditorContextUtils.getFullFileText(file) + "\n// Selected:\n" + selectedText;
            String suggestion = LLMClient.queryLLM(prompt, context);

            if (suggestion == null || suggestion.isEmpty()) {
                Messages.showWarningDialog(project, "模型未返回结果。", "提示");
                return;
            }

            // 清理建议内容
            String cleanedSuggestion = cleanSuggestion(suggestion);

            // 在 UI 线程显示内联差异
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                showDiffDialog(project, selectedText, cleanedSuggestion);
            });
        }).start();
    }

    /**
     * 清理 LLM 返回的建议内容
     */
    private String cleanSuggestion(String suggestion) {

        // 去除 markdown 代码块标记
        suggestion = suggestion.replaceAll("```[a-zA-Z]*\\n?", "");
        suggestion = suggestion.replaceAll("```", "");

        // 去除前后空白
        suggestion = suggestion.trim();

        return suggestion;
    }

    /**
     * 显示差异对比 - 现代化内联显示（类似 VS Code）
     */
    private void showDiffDialog(Project project, String original, String modified) {
        // 使用现代化 diff 渲染器
        // 特点：
        // 1. 底部工具栏显示 Keep/Undo 按钮和导航
        // 2. 高亮显示差异（红色+删除线 vs 绿色）
        // 3. 支持快捷键操作
        ModernDiffRenderer diffRenderer = new ModernDiffRenderer(
            lastEditor, project, original, modified, lastSelectionStart, lastSelectionEnd
        );
        diffRenderer.render();
    }


    /**
     * 绑定 Shift+Alt+2 快捷键应用修改（简化版本）
     */
    public static class ApplyEditAction extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            if (project != null) {
                Messages.showInfoMessage(project, 
                    "请使用内联差异视图中的按钮来应用或取消修改", 
                    "提示");
            }
        }
    }
}
