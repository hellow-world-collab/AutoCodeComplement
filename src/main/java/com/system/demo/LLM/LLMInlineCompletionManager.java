package com.system.demo.LLM;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;

public class LLMInlineCompletionManager {
    private static final Logger LOG = Logger.getInstance(LLMInlineCompletionManager.class);
    private static Inlay<?> currentInlay;
    private static String currentSuggestion = "";
    private static Editor currentEditor;

    public static boolean hasSuggestion() {
        return currentInlay != null && currentInlay.isValid() && !currentSuggestion.isEmpty();
    }

    public static void showInlineSuggestion(Editor editor, String suggestion) {
        if (editor == null || suggestion == null || suggestion.trim().isEmpty()) {
            return;
        }

        // 清理建议内容，移除多余的空白和换行
        String cleanSuggestion = suggestion.trim();
        if (cleanSuggestion.isEmpty()) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            // 如果编辑器已经改变，移除之前的建议
            if (currentEditor != null && currentEditor != editor) {
                removeInlineSuggestion();
            }
            
            removeInlineSuggestion();
            currentSuggestion = cleanSuggestion;
            currentEditor = editor;
            
            try {
                int offset = editor.getCaretModel().getOffset();
                InlayModel model = editor.getInlayModel();
                currentInlay = model.addInlineElement(offset, true, new SimpleInlayRenderer(cleanSuggestion));
            } catch (Exception e) {
                LOG.warn("Failed to show inline suggestion", e);
                currentInlay = null;
                currentSuggestion = "";
            }
        });
    }

    public static void removeInlineSuggestion() {
        if (currentInlay != null && currentInlay.isValid()) {
            try {
                currentInlay.dispose();
            } catch (Exception e) {
                LOG.warn("Failed to dispose inlay", e);
            }
        }
        currentInlay = null;
        currentSuggestion = "";
        currentEditor = null;
    }

    public static void accept(Editor editor) {
        if (currentInlay == null || editor == null) return;
        
        try {
            WriteCommandAction.runWriteCommandAction(editor.getProject(), () -> {
                editor.getDocument().insertString(editor.getCaretModel().getOffset(), currentSuggestion);
            });
        } catch (Exception e) {
            LOG.warn("Failed to accept suggestion", e);
        } finally {
            removeInlineSuggestion();
        }
    }
}
