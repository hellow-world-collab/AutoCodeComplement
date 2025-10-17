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
 * 监听用户输入，触发 LLM 补全
 * 优化版本：增加防抖机制和请求取消
 */
public class LLMTypedActionHandler implements TypedActionHandler {
    private final TypedActionHandler originalHandler;
    
    // 使用 ScheduledExecutorService 实现防抖
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile ScheduledFuture<?> pendingTask = null;
    private volatile String lastContext = "";

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
        
        // 取消正在进行的 LLM 请求
        LLMClient.cancelCurrentRequest();

        // 使用防抖机制：延迟执行补全请求
        long triggerDelay = LLMSettings.getInstance().triggerDelayMs;
        pendingTask = scheduler.schedule(() -> {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                // 先在 readAction 内安全读取 PSI 和光标
                final String[] fileContentHolder = new String[1];
                final int[] offsetHolder = new int[1];

                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiFile psiFile = PsiDocumentManager.getInstance(editor.getProject())
                            .getPsiFile(editor.getDocument());
                    if (psiFile == null) return;

                    fileContentHolder[0] = psiFile.getText();
                    offsetHolder[0] = editor.getCaretModel().getOffset();
                });

                String fileContent = fileContentHolder[0];
                if (fileContent == null) return;

                int offset = offsetHolder[0];
                String context = fileContent.substring(0, Math.min(offset, fileContent.length()));

                // 检查上下文是否变化，避免重复请求
                if (context.equals(lastContext)) {
                    return;
                }
                lastContext = context;

                if (!shouldTriggerCompletion(charTyped, context)) {
                    return;
                }

                // 优化 prompt，只发送最后 500 个字符作为上下文
                String contextToSend = context.length() > 500 
                    ? context.substring(context.length() - 500) 
                    : context;
                String prompt = "请根据以下代码上下文，预测用户接下来可能输入的代码（只返回补全内容，不要解释）：\n\n"
                        + contextToSend + "\n\n请补全：";

                String suggestion = LLMClient.queryLLM(prompt);
                if (suggestion != null && !suggestion.isEmpty()) {
                    suggestion = cleanSuggestion(suggestion);
                    if (!suggestion.isEmpty()) {
                        String finalSuggestion = suggestion;
                        // 回到 UI 主线程显示
                        ApplicationManager.getApplication().invokeLater(() -> {
                            LLMInlineCompletionManager.showInlineSuggestion(editor, finalSuggestion);
                        });
                    }
                }
            });
        }, triggerDelay, TimeUnit.MILLISECONDS);

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
