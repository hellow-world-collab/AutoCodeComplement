package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.system.demo.utils.EditorContextUtils;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 优化版本：更积极的补全策略，更接近IDEA官方实现
 * 改进：
 * 1. 使用基于PSI的智能上下文提取
 * 2. 更好的缓存键生成策略
 * 3. 优化的触发机制
 */
public class LLMTypedActionHandler implements TypedActionHandler {
    private final TypedActionHandler originalHandler;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile ScheduledFuture<?> pendingTask = null;
    private volatile String lastContextKey = "";

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

        // 立即清除旧的建议，提高响应性
        LLMInlineCompletionManager.removeInlineSuggestion();

        // 取消之前的延迟任务
        if (pendingTask != null && !pendingTask.isDone()) {
            pendingTask.cancel(false);
        }

        // 使用防抖机制：延迟执行补全请求
        long triggerDelay = LLMSettings.getInstance().triggerDelayMs;
        pendingTask = scheduler.schedule(() -> {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                // 使用智能上下文提取
                final EditorContextUtils.SmartContext[] smartContextHolder = new EditorContextUtils.SmartContext[1];
                final boolean[] shouldTriggerHolder = new boolean[1];
                final PsiFile[] psiFileHolder = new PsiFile[1];

                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiFile psiFile = PsiDocumentManager.getInstance(editor.getProject())
                            .getPsiFile(editor.getDocument());
                    if (psiFile == null) return;
                    
                    psiFileHolder[0] = psiFile;

                    // 判断是否应该触发
                    shouldTriggerHolder[0] = shouldTriggerCompletion(editor, charTyped, psiFile);
                    
                    if (shouldTriggerHolder[0]) {
                        // 使用新的智能上下文提取
                        smartContextHolder[0] = EditorContextUtils.getSmartContext(psiFile, editor);
                    }
                });

                if (!shouldTriggerHolder[0] || smartContextHolder[0] == null) return;

                EditorContextUtils.SmartContext smartContext = smartContextHolder[0];
                
                // 检查上下文是否变化，避免重复请求
                String currentContextKey = smartContext.getCacheKey() + "_" + charTyped;
                if (currentContextKey.equals(lastContextKey)) {
                    return;
                }
                lastContextKey = currentContextKey;

                // 使用SmartContext构建优化的Prompt
                String prompt = smartContext.buildOptimizedPrompt();

                // 查询LLM
                String suggestion = LLMClient.queryLLM(prompt, smartContext.getCacheKey());
                if (suggestion != null && !suggestion.isEmpty()) {
                    // 清理建议
                    suggestion = cleanSuggestion(suggestion, smartContext.beforeCursor);
                    if (!suggestion.isEmpty()) {
                        String finalSuggestion = suggestion;
                        ApplicationManager.getApplication().invokeLater(() -> {
                            LLMInlineCompletionManager.showInlineSuggestion(editor, finalSuggestion);
                        });
                    }
                }
            });
        }, triggerDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * 优化的触发条件 - 更接近IDEA的行为
     */
    private boolean shouldTriggerCompletion(Editor editor, char charTyped, PsiFile psiFile) {
        int offset = editor.getCaretModel().getOffset();
        String content = editor.getDocument().getText();

        if (offset == 0) return false;

        // 获取当前行和上下文
        int lineStart = content.lastIndexOf('\n', offset - 1) + 1;
        int lineEnd = content.indexOf('\n', offset);
        if (lineEnd == -1) lineEnd = content.length();

        String currentLine = content.substring(lineStart, lineEnd).trim();
        String beforeCursor = content.substring(lineStart, offset).trim();

        // 1. 空行或行首触发（IDEA常见行为）
        if (currentLine.isEmpty() || offset == lineStart) {
            return shouldTriggerAtLineStart(content, offset);
        }

        // 2. 刚完成一行（分号、大括号后）
        if (charTyped == ';' || charTyped == '}' || charTyped == '{') {
            return true;
        }

        // 3. 空格触发（更宽松的条件）
        if (charTyped == ' ') {
            return shouldTriggerAfterSpace(beforeCursor);
        }

        // 4. 点号、括号等常规触发
        if (charTyped == '.' || charTyped == '(' || charTyped == '=') {
            return true;
        }

        // 5. 字母数字触发（在已有内容后）
        if (Character.isLetterOrDigit(charTyped) && !beforeCursor.isEmpty()) {
            return true;
        }

        // 6. 换行触发（预测下一行）
        if (charTyped == '\n') {
            return shouldTriggerAfterNewline(content, offset);
        }

        return false;
    }

    /**
     * 在行首是否触发补全
     */
    private boolean shouldTriggerAtLineStart(String content, int offset) {
        if (offset == 0) return false;

        // 查找上一行
        int prevLineEnd = content.lastIndexOf('\n', offset - 1);
        if (prevLineEnd == -1) prevLineEnd = 0;
        else prevLineEnd++; // 跳过换行符

        String prevLine = content.substring(prevLineEnd, offset).trim();

        // 上一行是控制语句或方法调用后，在下一行行首触发
        return prevLine.endsWith("{") ||
                prevLine.endsWith(";") ||
                prevLine.contains("if") ||
                prevLine.contains("for") ||
                prevLine.contains("while") ||
                prevLine.contains("return");
    }

    /**
     * 空格后触发条件
     */
    private boolean shouldTriggerAfterSpace(String beforeCursor) {
        String trimmed = beforeCursor.trim();

        // 在关键字后空格触发
        return trimmed.endsWith("if") ||
                trimmed.endsWith("for") ||
                trimmed.endsWith("while") ||
                trimmed.endsWith("return") ||
                trimmed.endsWith("=") ||
                trimmed.endsWith("new") ||
                trimmed.endsWith("public") ||
                trimmed.endsWith("private") ||
                trimmed.endsWith("protected");
    }

    /**
     * 换行后触发条件（预测下一行）
     */
    private boolean shouldTriggerAfterNewline(String content, int offset) {
        if (offset == 0) return false;

        // 查找上一行
        int prevLineStart = content.lastIndexOf('\n', offset - 1);
        if (prevLineStart == -1) prevLineStart = 0;
        else prevLineStart++;

        String prevLine = content.substring(prevLineStart, offset - 1).trim(); // -1 排除换行符

        // 上一行是方法调用、控制语句等，预测下一行
        return !prevLine.isEmpty() &&
                (prevLine.endsWith("{") ||
                        prevLine.endsWith(";") ||
                        prevLine.contains(".") ||
                        prevLine.contains("if") ||
                        prevLine.contains("for") ||
                        prevLine.contains("while"));
    }

    /**
     * 清理建议内容
     */
    private String cleanSuggestion(String suggestion, String beforeCursor) {
        if (suggestion == null) return "";

        // 去除 markdown 代码块
        suggestion = suggestion.replaceAll("```[a-zA-Z]*\\n?", "");
        suggestion = suggestion.replaceAll("```", "");
        suggestion = suggestion.trim();

        // 去除与当前行重复的内容
        if (suggestion.startsWith(beforeCursor.trim())) {
            suggestion = suggestion.substring(beforeCursor.trim().length()).trim();
        }

        // 限制长度
        int maxLength = LLMSettings.getInstance().maxSuggestionLength;
        if (suggestion.length() > maxLength) {
            suggestion = suggestion.substring(0, maxLength);
        }

        return suggestion;
    }
}