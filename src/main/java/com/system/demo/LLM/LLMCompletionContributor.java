package com.system.demo.LLM;

import com.intellij.codeInsight.completion.*;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class LLMCompletionContributor extends CompletionContributor {

    public LLMCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet resultSet) {

                String fileText = parameters.getOriginalFile().getText();
                int offset = parameters.getOffset();
                String beforeCaret = fileText.substring(0, Math.min(offset, fileText.length()));

                // 提示内容
                String prompt = "基于以下代码片段提供智能补全建议：\n" + beforeCaret;

                String suggestion = LLMClient.queryLLM(prompt);
                if (suggestion != null && !suggestion.isEmpty()) {
                    resultSet.addElement(LookupElementBuilder.create(suggestion));
                }
            }
        });
    }
}

