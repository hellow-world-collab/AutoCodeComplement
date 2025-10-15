package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.system.demo.utils.EditorContextUtils;
import org.jetbrains.annotations.NotNull;

public class EditSelectionAction extends AnAction {

    private static String lastSuggestion = null;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        if (editor == null || file == null) return;

        String selectedText = EditorContextUtils.getSelectedText(editor);
        if (selectedText == null || selectedText.isEmpty()) {
            Messages.showWarningDialog(project, "请先选中需要修改的代码。", "提示");
            return;
        }

        // 构建 prompt
        String prompt = EditorContextUtils.buildContextPrompt(file, selectedText);
        String suggestion = LLMClient.queryLLM(prompt);

        if (suggestion == null || suggestion.isEmpty()) {
            Messages.showWarningDialog(project, "模型未返回结果。", "提示");
            return;
        }

        lastSuggestion = suggestion;
        Messages.showInfoMessage(project, suggestion, "AI 修改建议");
    }

    /**
     * 确认替换操作
     */
    public static class ApplyEditAction extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor == null || lastSuggestion == null) return;

            SelectionModel selection = editor.getSelectionModel();
            Document document = editor.getDocument();

            document.replaceString(
                    selection.getSelectionStart(),
                    selection.getSelectionEnd(),
                    lastSuggestion
            );

            lastSuggestion = null;
            Messages.showInfoMessage("已应用 AI 修改。", "完成");
        }
    }
}
