package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.system.demo.utils.EditorContextUtils;
import com.system.demo.utils.EditorContextUtils.CodeContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 优化版本：采用PSI分析和智能缓存，更接近IDEA官方实现
 */
public class LLMTypedActionHandler implements TypedActionHandler {
    private final TypedActionHandler originalHandler;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile ScheduledFuture<?> pendingTask = null;
    private volatile String lastContextKey = "";
    private final CompletionCacheManager cacheManager = CompletionCacheManager.getInstance();

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
                // 使用增强的上下文提取
                final CodeContext[] contextHolder = new CodeContext[1];
                final PsiFile[] psiFileHolder = new PsiFile[1];
                final boolean[] shouldTriggerHolder = new boolean[1];

                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiFile psiFile = PsiDocumentManager.getInstance(editor.getProject())
                            .getPsiFile(editor.getDocument());
                    if (psiFile == null) return;
                    
                    psiFileHolder[0] = psiFile;
                    shouldTriggerHolder[0] = shouldTriggerCompletion(editor, charTyped, psiFile);
                    
                    if (shouldTriggerHolder[0]) {
                        // 使用新的上下文提取工具
                        contextHolder[0] = EditorContextUtils.getCodeContext(editor, psiFile);
                    }
                });

                if (!shouldTriggerHolder[0] || contextHolder[0] == null) return;

                CodeContext context = contextHolder[0];
                
                // 检查上下文是否变化，避免重复请求
                String currentContextKey = context.getCacheKey();
                if (currentContextKey.equals(lastContextKey)) {
                    return;
                }
                lastContextKey = currentContextKey;
                
                // 首先尝试从智能缓存获取
                String cachedSuggestion = cacheManager.getCachedSuggestion(context);
                if (cachedSuggestion != null && !cachedSuggestion.isEmpty()) {
                    String finalSuggestion = cachedSuggestion;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        LLMInlineCompletionManager.showInlineSuggestion(editor, finalSuggestion);
                    });
                    return;
                }

                // 构建优化的Prompt
                String prompt = buildOptimizedPrompt(context, charTyped);

                // 查询LLM
                String suggestion = LLMClient.queryLLM(prompt, context.getCacheKey());
                if (suggestion != null && !suggestion.isEmpty()) {
                    suggestion = cleanSuggestion(suggestion, context);
                    if (!suggestion.isEmpty()) {
                        // 缓存到智能缓存管理器
                        cacheManager.cacheSuggestion(context, suggestion);
                        
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
     * 构建优化的Prompt（使用新的CodeContext）
     */
    private String buildOptimizedPrompt(CodeContext context, char lastChar) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("你是一个").append(context.fileType).append("代码专家。\n\n");
        
        // 添加结构化上下文
        if (context.currentClassName != null) {
            prompt.append("当前类: ").append(context.currentClassName).append("\n");
        }
        if (context.currentMethodName != null) {
            prompt.append("当前方法: ").append(context.currentMethodSignature).append("\n");
        }
        if (context.localVariables != null && !context.localVariables.isEmpty()) {
            prompt.append("局部变量: ").append(context.localVariables).append("\n");
        }
        
        prompt.append("\n=== 前置代码 ===\n");
        if (context.precedingCode != null && !context.precedingCode.isEmpty()) {
            // 只取最后部分，避免prompt过长
            String preceding = context.precedingCode;
            if (preceding.length() > 300) {
                preceding = "..." + preceding.substring(preceding.length() - 300);
            }
            prompt.append(preceding);
        }
        
        prompt.append("\n\n=== 当前行 ===\n");
        prompt.append(context.currentLine.substring(0, context.cursorPositionInLine));
        prompt.append("|"); // 光标位置
        if (context.cursorPositionInLine < context.currentLine.length()) {
            prompt.append(context.currentLine.substring(context.cursorPositionInLine));
        }
        
        prompt.append("\n\n最后输入: '").append(lastChar).append("'\n\n");
        
        prompt.append("要求：\n");
        prompt.append("1. 只返回需要在光标(|)处插入的代码\n");
        prompt.append("2. 保持代码风格和缩进一致\n");
        prompt.append("3. 考虑上下文逻辑连贯性\n");
        prompt.append("4. 通常1-2行即可，简洁为主\n");
        prompt.append("5. 不要重复已有代码\n\n");
        prompt.append("补全内容：");
        
        return prompt.toString();
    }

    /**
     * 清理LLM返回的建议
     */
    private String cleanSuggestion(String suggestion, CodeContext context) {
        if (suggestion == null) return "";

        // 去除 markdown 代码块
        suggestion = suggestion.replaceAll("```[a-zA-Z]*\\n?", "");
        suggestion = suggestion.replaceAll("```", "");
        suggestion = suggestion.trim();

        // 去除与当前行重复的内容
        String beforeCursor = context.currentLine.substring(0, context.cursorPositionInLine).trim();
        if (suggestion.startsWith(beforeCursor)) {
            suggestion = suggestion.substring(beforeCursor.length()).trim();
        }

        // 限制长度
        int maxLength = LLMSettings.getInstance().maxSuggestionLength;
        if (suggestion.length() > maxLength) {
            // 在合适的位置截断（避免截断到行中间）
            int cutPos = suggestion.lastIndexOf('\n', maxLength);
            if (cutPos > 0 && cutPos > maxLength / 2) {
                suggestion = suggestion.substring(0, cutPos);
            } else {
                suggestion = suggestion.substring(0, maxLength);
            }
        }

        return suggestion;
    }
}