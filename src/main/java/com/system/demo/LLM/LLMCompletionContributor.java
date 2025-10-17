package com.system.demo.LLM;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 捕获输入并触发 LLM 灰色补全
 */
public class LLMCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(LLMCompletionContributor.class);
    private static final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private static volatile long lastRequestTime = 0;
    private static final long DEBOUNCE_DELAY_MS = 300; // 防抖延迟

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        if (!LLMState.isEnabled()) return;

        // 防抖机制
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime < DEBOUNCE_DELAY_MS) {
            return;
        }
        lastRequestTime = currentTime;

        PsiFile file = parameters.getOriginalFile();
        String fileContent = file.getText();
        int offset = parameters.getOffset();
        String context = fileContent.substring(0, Math.min(offset, fileContent.length()));

        // 限制上下文长度以提高性能
        if (context.length() > 2000) {
            context = context.substring(context.length() - 2000);
        }

        // 检查缓存
        String cacheKey = context.hashCode() + "_" + offset;
        CompletableFuture<String> cachedRequest = pendingRequests.get(cacheKey);
        if (cachedRequest != null && !cachedRequest.isDone()) {
            return; // 已有相同请求在进行中
        }

        // 取消之前的请求
        cancelPendingRequests();

        // 异步调用LLM
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = "根据以下代码内容预测用户在光标处可能输入的内容（只返回代码，不要解释）：\n\n" + context;
                return LLMClient.queryLLM(prompt);
            } catch (Exception e) {
                LOG.warn("LLM query failed", e);
                return null;
            }
        });

        pendingRequests.put(cacheKey, future);

        // 设置超时
        future.orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((suggestion, throwable) -> {
                    pendingRequests.remove(cacheKey);
                    
                    if (throwable != null) {
                        LOG.warn("LLM request timeout or failed", throwable);
                        return;
                    }

                    if (suggestion != null && !suggestion.trim().isEmpty()) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            LLMInlineCompletionManager.showInlineSuggestion(parameters.getEditor(), suggestion.trim());
                        });
                    }
                });
    }

    private static void cancelPendingRequests() {
        pendingRequests.values().forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        pendingRequests.clear();
    }
}
