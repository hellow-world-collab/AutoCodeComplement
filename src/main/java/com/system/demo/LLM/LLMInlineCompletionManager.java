package com.system.demo.LLM;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;

public class LLMInlineCompletionManager {
    private static Inlay<?> currentInlay;
    private static String currentSuggestion = "";

    public static boolean hasSuggestion() {
        return currentInlay != null && currentInlay.isValid() && !currentSuggestion.isEmpty();
    }

    public static void showInlineSuggestion(Editor editor, String suggestion) {
        ApplicationManager.getApplication().invokeLater(() -> {
            removeInlineSuggestion();
            if (suggestion == null || suggestion.isEmpty()) return;
            currentSuggestion = suggestion;
            int offset = editor.getCaretModel().getOffset();
            InlayModel model = editor.getInlayModel();
            currentInlay = model.addInlineElement(offset, true, new SimpleInlayRenderer(suggestion));
        });
    }

    public static void removeInlineSuggestion() {
        if (currentInlay != null && currentInlay.isValid()) {
            currentInlay.dispose();
        }
        currentInlay = null;
        currentSuggestion = "";
    }

    public static void accept(Editor editor) {
        if (currentInlay == null) return;
        WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
            editor.getDocument().insertString(editor.getCaretModel().getOffset(), currentSuggestion);
        });
        removeInlineSuggestion();
    }
}
