package com.system.demo.LLM;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 文档监听器，用于触发LLM补全
 */
public class LLMDocumentListener implements DocumentListener {
    private static final Logger LOG = Logger.getInstance(LLMDocumentListener.class);
    private static final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private static volatile long lastRequestTime = 0;
    private static final long DEBOUNCE_DELAY_MS = 500; // 防抖延迟

    @Override
    public void documentChanged(DocumentEvent event) {
        if (!LLMState.isEnabled()) return;

        Document document = event.getDocument();
        Project project = null;

        // 获取项目
        for (Project p : ProjectManager.getInstance().getOpenProjects()) {
            if (PsiDocumentManager.getInstance(p).getPsiFile(document) != null) {
                project = p;
                break;
            }
        }

        if (project == null) return;

        // 防抖机制
        LLMSettings settings = LLMSettings.getInstance();
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime < settings.triggerDelayMs) {
            return;
        }
        lastRequestTime = currentTime;

        // 获取编辑器
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null || editor.getDocument() != document) return;

        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (psiFile == null) return;

        String fileContent = document.getText();
        int offset = editor.getCaretModel().getOffset();
        String context = fileContent.substring(0, Math.min(offset, fileContent.length()));

        // 限制上下文长度
        if (context.length() > settings.maxContextLength) {
            context = context.substring(context.length() - settings.maxContextLength);
        }

        // 检查是否在合适的位置触发补全
        if (!shouldTriggerCompletion(context, offset)) {
            return;
        }

        // 检查缓存
        String cacheKey = context.hashCode() + "_" + offset;
        if (settings.enableCache) {
            String cachedResponse = LLMCache.get(cacheKey);
            if (cachedResponse != null) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    LLMInlineCompletionManager.showInlineSuggestion(editor, cachedResponse);
                });
                return;
            }
        }

        CompletableFuture<String> cachedRequest = pendingRequests.get(cacheKey);
        if (cachedRequest != null && !cachedRequest.isDone()) {
            return;
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
        future.orTimeout(8, TimeUnit.SECONDS)
                .whenComplete((suggestion, throwable) -> {
                    pendingRequests.remove(cacheKey);
                    
                    if (throwable != null) {
                        LOG.warn("LLM request timeout or failed", throwable);
                        return;
                    }

                    if (suggestion != null && !suggestion.trim().isEmpty()) {
                        String cleanSuggestion = suggestion.trim();
                        // 限制建议长度
                        if (cleanSuggestion.length() > settings.maxSuggestionLength) {
                            cleanSuggestion = cleanSuggestion.substring(0, settings.maxSuggestionLength);
                        }
                        
                        // 缓存响应
                        if (settings.enableCache) {
                            LLMCache.put(cacheKey, cleanSuggestion);
                        }
                        
                        ApplicationManager.getApplication().invokeLater(() -> {
                            LLMInlineCompletionManager.showInlineSuggestion(editor, cleanSuggestion);
                        });
                    }
                });
    }

    private boolean shouldTriggerCompletion(String context, int offset) {
        // 检查是否在合适的位置触发补全
        if (offset == 0) return false;
        
        char lastChar = context.charAt(offset - 1);
        // 在特定字符后触发补全
        return lastChar == ' ' || lastChar == '\n' || lastChar == '{' || 
               lastChar == '(' || lastChar == '=' || lastChar == ';' ||
               lastChar == '.' || lastChar == '>' || lastChar == '<';
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