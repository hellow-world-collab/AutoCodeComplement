package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * 监听用户输入，触发 LLM 补全
 */
public class LLMTypedActionHandler implements TypedActionHandler {
    private final TypedActionHandler originalHandler;
    private long lastTriggerTime = 0;

    public LLMTypedActionHandler(TypedActionHandler originalHandler) {
        this.originalHandler = originalHandler;
    }

    @Override
    public void execute(@NotNull Editor editor, char charTyped, @NotNull DataContext dataContext) {
        // 先执行原始处理器
        if (originalHandler != null) {
            originalHandler.execute(editor, charTyped, dataContext);
        }

        // 如果未启用 AI 补全，直接返回
        if (!LLMState.isEnabled()) {
            return;
        }

        // 检查是否需要触发补全（避免过于频繁）
        long currentTime = System.currentTimeMillis();
        long triggerDelay = LLMSettings.getInstance().triggerDelayMs;
        if (currentTime - lastTriggerTime < triggerDelay) {
            return;
        }
        lastTriggerTime = currentTime;

        // 在后台线程触发补全
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            PsiFile psiFile = PsiDocumentManager.getInstance(editor.getProject())
                    .getPsiFile(editor.getDocument());
            if (psiFile == null) return;

            String fileContent = psiFile.getText();
            int offset = editor.getCaretModel().getOffset();
            
            // 获取光标前的上下文
            String context = fileContent.substring(0, Math.min(offset, fileContent.length()));
            
            // 只在合适的位置触发（例如输入字母、数字、空格等）
            if (!shouldTriggerCompletion(charTyped, context)) {
                return;
            }

            // 构建 prompt
            String prompt = "请根据以下代码上下文，预测用户接下来可能输入的代码（只返回补全内容，不要解释）：\n\n" + context + "\n\n请补全：";

            String suggestion = LLMClient.queryLLM(prompt);
            if (suggestion != null && !suggestion.isEmpty()) {
                // 清理返回的内容（去除 markdown 代码块等）
                suggestion = cleanSuggestion(suggestion);
                if (!suggestion.isEmpty()) {
                    String finalSuggestion = suggestion;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        LLMInlineCompletionManager.showInlineSuggestion(editor, finalSuggestion);
                    });
                }
            }
        });
    }

    /**
     * 判断是否应该触发补全
     */
    private boolean shouldTriggerCompletion(char charTyped, String context) {
        // 在以下情况触发：
        // 1. 输入字母、数字
        // 2. 输入空格（在单词后）
        // 3. 输入点号（方法调用）
        // 4. 输入左括号
        
        if (Character.isLetterOrDigit(charTyped)) {
            return true;
        }
        
        if (charTyped == ' ' || charTyped == '.' || charTyped == '(') {
            return true;
        }
        
        return false;
    }

    /**
     * 清理 LLM 返回的建议内容
     */
    private String cleanSuggestion(String suggestion) {
        // 去除 markdown 代码块
        suggestion = suggestion.replaceAll("```[a-zA-Z]*\\n?", "");
        suggestion = suggestion.replaceAll("```", "");
        
        // 去除前后空白
        suggestion = suggestion.trim();
        
        // 只取第一行（避免返回太多内容）
        int newlineIndex = suggestion.indexOf('\n');
        if (newlineIndex > 0 && newlineIndex < 100) {
            suggestion = suggestion.substring(0, newlineIndex);
        }
        
        // 限制长度
        int maxLength = LLMSettings.getInstance().maxSuggestionLength;
        if (suggestion.length() > maxLength) {
            suggestion = suggestion.substring(0, maxLength);
        }
        
        return suggestion;
    }
}
