package com.system.demo.LLM;

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

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null) return;

        String selected = EditorContextUtils.getSelectedText(editor);
        if (selected == null || selected.isEmpty()) {
            Messages.showWarningDialog(project, "请先选中代码。", "提示");
            return;
        }

        String prompt = EditorContextUtils.buildContextPrompt(file, selected);
        String suggestion = LLMClient.queryLLM(prompt);
        if (suggestion == null || suggestion.isEmpty()) {
            Messages.showWarningDialog(project, "模型未返回结果。", "提示");
            return;
        }

        lastOriginal = selected;
        lastSuggestion = suggestion;

        // 弹窗显示差异并让用户确认
        String message = "原始代码:\n" + selected + "\n\nAI 修改建议:\n" + suggestion;
        int result = Messages.showYesNoDialog(project, message, "AI 修改建议", "应用修改", "取消", Messages.getQuestionIcon());

        if (result == Messages.YES) {
            applyEdit(editor);
        }
    }

    /**
     * 快捷键确认替换或弹窗确认应用
     */
    public static void applyEdit(Editor editor) {
        if (editor == null || lastSuggestion == null) return;

        SelectionModel sel = editor.getSelectionModel();
        Document doc = editor.getDocument();
        WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
            doc.replaceString(sel.getSelectionStart(), sel.getSelectionEnd(), lastSuggestion);
        });

        lastSuggestion = null;
        lastOriginal = null;
        Messages.showInfoMessage(editor.getProject(), "已应用 AI 修改。", "完成");
    }

    /**
     * 也可以绑定快捷键调用这个方法
     */
    public static class ApplyEditAction extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            applyEdit(editor);
        }
    }
}
