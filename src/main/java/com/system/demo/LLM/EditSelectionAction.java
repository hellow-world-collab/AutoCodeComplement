package com.system.demo.LLM;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;
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

import java.util.Arrays;
import java.util.List;


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

            // 更细致的空结果处理
            if (suggestion == null) {
                // 网络错误或其他问题
                Messages.showErrorDialog(project, "请求失败，请检查网络连接和API配置", "错误");
                return;
            }

            if (suggestion.isEmpty()) {
                // 模型返回空字符串，可能表示代码无需修改
                int result = Messages.showYesNoDialog(project,
                        "AI 分析后认为当前代码无需改进。\n是否显示原始代码对比？",
                        "无需改进",
                        Messages.getQuestionIcon());

                if (result == Messages.YES) {
                    // 显示原始代码的"无差异"对比
                    showNoChangesDialog(project, selectedText);
                }
                return;
            }

// 检查建议是否与原始代码相同
            if (isSuggestionIdentical(selectedText, suggestion)) {
                Messages.showInfoMessage(project, "AI 建议与当前代码相同，无需修改", "提示");
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
     * 检查建议是否与原始代码相同
     */
    private boolean isSuggestionIdentical(String original, String suggestion) {
        if (original == null || suggestion == null) return false;

        // 标准化比较：去除空白差异
        String normalizedOriginal = original.replaceAll("\\s+", " ").trim();
        String normalizedSuggestion = suggestion.replaceAll("\\s+", " ").trim();

        return normalizedOriginal.equals(normalizedSuggestion);
    }

    /**
     * 显示无差异对话框
     */
    private void showNoChangesDialog(Project project, String originalCode) {
        String message = String.format(
                "<html><body>" +
                        "<b>AI 分析结果：代码无需改进</b><br><br>" +
                        "当前选中的代码已经具有良好的质量。<br><br>" +
                        "<b>选中的代码：</b><br>" +
                        "<pre style='background: #f5f5f5; padding: 10px; border-radius: 3px;'>%s</pre>" +
                        "</body></html>",
                originalCode.replace("<", "&lt;").replace(">", "&gt;")
        );

        Messages.showMessageDialog(project, message, "无需改进", Messages.getInformationIcon());
    }
    /**
     * 清理 LLM 返回的建议内容
     */
    private String cleanSuggestion(String suggestion) {
        if (suggestion == null || suggestion.isEmpty()) {
            return "";
        }

        // 保存原始内容用于调试
        String original = suggestion;

        // 1. 去除 markdown 代码块标记
        suggestion = suggestion.replaceAll("(?i)```[a-zA-Z]*\\s*\\n?", "");
        suggestion = suggestion.replaceAll("```\\s*$", "");
        suggestion = suggestion.replaceAll("```", "");

        // 2. 去除可能的引导性文字
        suggestion = suggestion.replaceAll("(?i)改进后的代码[:：]?\\s*", "");
        suggestion = suggestion.replaceAll("(?i)添加注释后的代码[:：]?\\s*", "");
        suggestion = suggestion.replaceAll("(?i)这里是.*?代码[:：]?\\s*", "");

        // 3. 去除前后空白
        suggestion = suggestion.trim();

        // 4. 检查是否为空
        if (suggestion.isEmpty()) {
            // 如果清理后为空，尝试使用原始内容但去除明显的非代码部分
            String fallback = original
                    .replaceAll("(?i)```[a-zA-Z]*\\s*", "")
                    .replaceAll("```", "")
                    .trim();

            // 如果以代码常见的字符开头，使用fallback
            if (fallback.matches("^[\\s\\w\\[\\]{}()<>;=.,+\\-*/\"'].*")) {
                return fallback;
            }

            System.err.println("警告：清理后内容为空，原始内容: " + original);
            return original.trim(); // 最后手段：返回原始内容
        }

        return suggestion;
    }

    /**
     * 显示差异对比 - 现代化内联显示（类似 VS Code）
     */
    private void showDiffDialog(Project project, String original, String modified) {
        System.out.println("=== 差异分析 ===");
        System.out.println("原始代码:");
        System.out.println(original);
        System.out.println("AI建议代码:");
        System.out.println(modified);
        System.out.println("选中范围: [" + lastSelectionStart + ", " + lastSelectionEnd + "]");

        // 计算差异
        List<String> originalLines = Arrays.asList(original.split("\n"));
        List<String> modifiedLines = Arrays.asList(modified.split("\n"));
        Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);

        System.out.println("发现 " + patch.getDeltas().size() + " 个差异块:");
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            System.out.println("类型: " + delta.getType() +
                    ", 原位置: " + delta.getSource().getPosition() +
                    ", 新位置: " + delta.getTarget().getPosition() +
                    ", 原行数: " + delta.getSource().size() +
                    ", 新行数: " + delta.getTarget().size());
        }

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
