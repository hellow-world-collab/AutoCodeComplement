package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 优化版本：更积极的补全策略，模仿IDEA行为
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
                // 安全读取 PSI 和光标
                final String[] fileContentHolder = new String[1];
                final int[] offsetHolder = new int[1];
                final String[] fileTypeHolder = new String[1];
                final boolean[] shouldTriggerHolder = new boolean[1];

                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiFile psiFile = PsiDocumentManager.getInstance(editor.getProject())
                            .getPsiFile(editor.getDocument());
                    if (psiFile == null) return;

                    fileContentHolder[0] = psiFile.getText();
                    offsetHolder[0] = editor.getCaretModel().getOffset();
                    fileTypeHolder[0] = psiFile.getFileType().getName().toLowerCase();

                    // 在新的读操作中判断是否应该触发
                    shouldTriggerHolder[0] = shouldTriggerCompletion(editor, charTyped, psiFile);
                });

                if (!shouldTriggerHolder[0]) return;

                String fileContent = fileContentHolder[0];
                if (fileContent == null) return;

                int offset = offsetHolder[0];
                String fileType = fileTypeHolder[0] != null ? fileTypeHolder[0] : "java";

                // 使用优化的上下文获取策略
                EnhancedContextInfo contextInfo = getEnhancedContext(fileContent, offset, editor);

                // 检查上下文是否变化，避免重复请求
                String currentContextKey = generateContextKey(contextInfo, charTyped);
                if (currentContextKey.equals(lastContextKey)) {
                    return;
                }
                lastContextKey = currentContextKey;

                // 构建优化的Prompt
                String prompt = buildEnhancedPrompt(contextInfo, charTyped, fileType);

                // 不取消之前的请求，让它们自然完成（减少中断）
                String suggestion = LLMClient.queryLLM(prompt, contextInfo.getCacheKey());
                if (suggestion != null && !suggestion.isEmpty()) {
                    suggestion = cleanSuggestion(suggestion, contextInfo);
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
     * 增强的上下文获取
     */
    private EnhancedContextInfo getEnhancedContext(String fileContent, int offset, Editor editor) {
        int totalLength = fileContent.length();
        int windowSize = 500; // 上下文窗口（字符数）
        int start = Math.max(0, offset - windowSize);
        int end = Math.min(totalLength, offset + 200);

        String windowText = fileContent.substring(start, end);

        // 精准方法上下文
        String methodContext = extractMethodContext(fileContent, offset);
        String classContext = extractClassContext(fileContent, offset);

        // 当前行信息
        int lineStart = fileContent.lastIndexOf('\n', offset - 1) + 1;
        int lineEnd = fileContent.indexOf('\n', offset);
        if (lineEnd == -1) lineEnd = fileContent.length();
        String currentLine = fileContent.substring(lineStart, lineEnd);
        String beforeCursor = fileContent.substring(lineStart, offset);
        String afterCursor = fileContent.substring(offset, lineEnd);

        return new EnhancedContextInfo(
                windowText,
                beforeCursor,
                afterCursor,
                currentLine,
                methodContext + "\n" + classContext
        );
    }

    // 识别当前方法上下文
    private String extractMethodContext(String content, int offset) {
        int methodStart = -1;
        int braceBalance = 0;
        for (int i = offset; i > 0; i--) {
            char c = content.charAt(i - 1);
            if (c == '{') braceBalance--;
            else if (c == '}') braceBalance++;
            if (braceBalance < 0) {
                methodStart = i - 1;
                break;
            }
        }
        if (methodStart < 0) return "";
        int sigStart = Math.max(0, content.lastIndexOf('\n', methodStart - 1));
        return content.substring(sigStart, Math.min(offset, content.length()));
    }

    // 识别类级上下文
    private String extractClassContext(String content, int offset) {
        int classStart = content.lastIndexOf("class ", offset);
        if (classStart == -1) return "";
        int classHeaderEnd = content.indexOf("{", classStart);
        if (classHeaderEnd == -1) classHeaderEnd = Math.min(offset, content.length());
        return content.substring(classStart, classHeaderEnd);
    }


    /**
     * 构建增强的Prompt
     */
    private String buildEnhancedPrompt(EnhancedContextInfo context, char lastChar, String fileType) {
        return String.format(
                "你是一个专业的 %s 代码补全助手。\n" +
                        "当前文件类型: %s\n" +
                        "用户最后输入字符: '%s'\n\n" +
                        "==== 当前上下文（光标附近） ====\n%s\n\n" +
                        "==== 方法上下文 ====\n%s\n\n" +
                        "==== 当前行 ====\n%s|\n\n" +
                        "请仅输出 **应在光标处插入的补全内容**，不要重复上下文或添加解释。",
                fileType.toUpperCase(), fileType, lastChar,
                context.previousLines, context.methodContext, context.beforeCursor
        );
    }


    private String generateContextKey(EnhancedContextInfo context, char lastChar) {
        // 使用更精细的上下文键
        return context.beforeCursor.hashCode() + "_" +
                context.previousLines.hashCode() + "_" +
                lastChar;
    }

    private String cleanSuggestion(String suggestion, EnhancedContextInfo context) {
        if (suggestion == null) return "";

        // 去除 markdown 代码块
        suggestion = suggestion.replaceAll("```[a-zA-Z]*\\n?", "");
        suggestion = suggestion.replaceAll("```", "");
        suggestion = suggestion.trim();

        // 去除与当前行重复的内容
        if (suggestion.startsWith(context.beforeCursor)) {
            suggestion = suggestion.substring(context.beforeCursor.length()).trim();
        }

        // 限制长度
        int maxLength = LLMSettings.getInstance().maxSuggestionLength;
        if (suggestion.length() > maxLength) {
            suggestion = suggestion.substring(0, maxLength);
        }

        return suggestion;
    }

    /**
     * 增强的上下文信息
     */
    private static class EnhancedContextInfo {
        final String previousLines;    // 前几行代码
        final String beforeCursor;     // 当前行光标前内容
        final String afterCursor;      // 当前行光标后内容
        final String currentLine;      // 整行内容
        final String methodContext;    // 方法级上下文

        EnhancedContextInfo(String previousLines, String beforeCursor,
                            String afterCursor, String currentLine, String methodContext) {
            this.previousLines = previousLines;
            this.beforeCursor = beforeCursor;
            this.afterCursor = afterCursor;
            this.currentLine = currentLine;
            this.methodContext = methodContext;
        }

        String getCacheKey() {
            return previousLines + beforeCursor + afterCursor;
        }
    }
}