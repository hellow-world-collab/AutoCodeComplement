package com.system.demo.LLM;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.system.demo.utils.EditorContextUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 选中代码发送到 LLM → 弹窗显示差异 → 用户确认应用
 */
public class EditSelectionAction extends AnAction {
    private static String lastSuggestion;
    private static String lastOriginal;
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

            // 获取 actionid 来区分 加注释 和 改代码 两个功能
            String actionId = ActionManager.getInstance().getId(this);

            String prompt;
            if ("CommentSelectionWithAI".equalsIgnoreCase(actionId)) {
                // Shift + Alt + 3 → 给代码加注释
                prompt = EditorContextUtils.buildContextPromptForComment(file, selectedText);
            } else {
                // 默认：Shift + Alt + 1 → 改进代码
                prompt = EditorContextUtils.buildContextPrompt(file, selectedText);
            }
//            String prompt = EditorContextUtils.buildContextPrompt(file, selectedText);

            String context = EditorContextUtils.getFullFileText(file) + "\n// Selected:\n" + selectedText;
            String suggestion = LLMClient.queryLLM(prompt, context);
            
            if (suggestion == null || suggestion.isEmpty()) {
                Messages.showWarningDialog(project, "模型未返回结果。", "提示");
                return;
            }

            // 清理建议内容
            String cleanedSuggestion = cleanSuggestion(suggestion);

            lastOriginal = selectedText;
            lastSuggestion = cleanedSuggestion;

            // 在 UI 线程显示差异对比窗口
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
     * 显示差异对比对话框
     */
    private void showDiffDialog(Project project, String original, String modified) {
        DiffContentFactory contentFactory = DiffContentFactory.getInstance();

        DocumentContent content1 = contentFactory.create(project, original);
        DocumentContent content2 = contentFactory.create(project, modified);

        SimpleDiffRequest request = new SimpleDiffRequest(
                "AI 代码修改建议对比",
                content1,
                content2,
                "原始代码",
                "AI 修改建议"
        );

        DiffManager.getInstance().showDiff(project, request);
    }

    /**
     * 快捷键确认替换或弹窗确认应用
     */
    public static void applyEdit(Project project) {
        if (lastEditor == null || lastSuggestion == null) {
            if (project != null) {
                Messages.showWarningDialog(project, "没有可应用的 AI 修改建议。\n请先使用 Shift+Alt+1 分析代码。", "提示");
            }
            return;
        }

        Document doc = lastEditor.getDocument();
        WriteCommandAction.runWriteCommandAction(project, () -> {
            doc.replaceString(lastSelectionStart, lastSelectionEnd, lastSuggestion);
        });

        // 清空状态
        String appliedSuggestion = lastSuggestion;
        lastSuggestion = null;
        lastOriginal = null;
        lastEditor = null;

        Messages.showInfoMessage(project, "已成功应用 AI 修改建议！", "完成");
    }

    /**
     * 绑定 Shift+Alt+R 快捷键应用修改
     */
    public static class ApplyEditAction extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            applyEdit(project);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            // 只在有待应用的修改时启用此操作
            e.getPresentation().setEnabled(lastSuggestion != null && lastEditor != null);
        }
    }
}
