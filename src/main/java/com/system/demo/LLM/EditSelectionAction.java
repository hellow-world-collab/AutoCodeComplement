package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;

public class EditSelectionAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null || project == null) return;

        SelectionModel selection = editor.getSelectionModel();
        String selectedText = selection.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) return;

        String prompt = "请改进以下Python代码，优化结构并保持语义一致：\n\n" + selectedText;
        String modified = LLMClient.queryLLM(prompt);

        WriteCommandAction.runWriteCommandAction(project, () -> {
            editor.getDocument().replaceString(selection.getSelectionStart(), selection.getSelectionEnd(), modified);
        });
    }
}

