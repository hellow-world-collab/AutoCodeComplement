package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 处理 Tab 键接受补全
 */
public class TabAcceptHandler extends EditorActionHandler {

    private final EditorActionHandler originalHandler;

    public TabAcceptHandler(EditorActionHandler originalHandler) {
        this.originalHandler = originalHandler;
    }

    @Override
    public void execute(@NotNull Editor editor, DataContext dataContext) {

        if (LLMInlineCompletionManager.hasSuggestion()) {
            LLMInlineCompletionManager.accept(editor);
        } else if (originalHandler != null) {
            originalHandler.execute(editor, dataContext);
        }
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
        return true;
    }
}
