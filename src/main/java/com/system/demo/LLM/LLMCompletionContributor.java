package com.system.demo.LLM;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * LLM 驱动的代码补全贡献者
 * 只在用户启用时生效，并使用整个文件作为上下文
 */
public class LLMCompletionContributor extends CompletionContributor {

    public LLMCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet resultSet) {
                
                // 检查 LLM 补全是否启用
                if (!LLMCompletionSettings.getInstance().isEnabled()) {
                    return;
                }

                // 在后台线程异步执行，避免阻塞 UI
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    try {
                        PsiFile file = parameters.getOriginalFile();
                        Document document = parameters.getEditor().getDocument();
                        int offset = parameters.getOffset();
                        
                        String fileText = document.getText();
                        String fileName = file.getName();
                        
                        // 获取光标前后的内容
                        String beforeCursor = fileText.substring(0, Math.min(offset, fileText.length()));
                        String afterCursor = fileText.substring(Math.min(offset, fileText.length()));
                        
                        // 构建包含完整上下文的提示
                        String prompt = buildCompletionPrompt(fileName, beforeCursor, afterCursor);
                        
                        // 调用 LLM
                        String suggestion = LLMClient.queryLLM(prompt);
                        
                        if (suggestion != null && !suggestion.isEmpty()) {
                            // 清理建议（移除可能的代码块标记）
                            final String cleanedSuggestion = cleanSuggestion(suggestion);
                            
                            // 将结果添加到补全列表
                            ApplicationManager.getApplication().invokeLater(() -> {
                                resultSet.addElement(
                                    LookupElementBuilder.create(cleanedSuggestion)
                                        .withPresentableText(cleanedSuggestion.length() > 50 
                                            ? cleanedSuggestion.substring(0, 50) + "..." 
                                            : cleanedSuggestion)
                                        .withTypeText("LLM", true)
                                        .withIcon(com.intellij.icons.AllIcons.Actions.IntentionBulb)
                                );
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        });
    }

    /**
     * 构建代码补全的提示词
     */
    private String buildCompletionPrompt(String fileName, String beforeCursor, String afterCursor) {
        return String.format(
            "你是一个专业的 Python 代码助手。请基于以下文件的完整上下文，为光标位置提供代码补全建议。\n\n" +
            "文件名：%s\n\n" +
            "光标前的代码：\n```python\n%s\n```\n\n" +
            "光标后的代码：\n```python\n%s\n```\n\n" +
            "请只返回应该在光标位置插入的代码，不要包含任何解释或markdown标记。" +
            "补全应该简洁、符合上下文，并遵循 Python 最佳实践。",
            fileName,
            beforeCursor.length() > 1000 ? "..." + beforeCursor.substring(beforeCursor.length() - 1000) : beforeCursor,
            afterCursor.length() > 500 ? afterCursor.substring(0, 500) + "..." : afterCursor
        );
    }

    /**
     * 清理 LLM 返回的建议
     */
    private String cleanSuggestion(String suggestion) {
        // 移除可能的 markdown 代码块标记
        suggestion = suggestion.replaceAll("^```[a-z]*\\n", "");
        suggestion = suggestion.replaceAll("\\n```$", "");
        suggestion = suggestion.trim();
        return suggestion;
    }
}

