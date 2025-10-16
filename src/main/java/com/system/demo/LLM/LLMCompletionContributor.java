package com.system.demo.LLM;

import com.intellij.codeInsight.completion.*;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * 捕获输入并触发 LLM 灰色补全
 */
public class LLMCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        if (!LLMState.isEnabled()) return;

        PsiFile file = parameters.getOriginalFile();
        String fileContent = file.getText();
        int offset = parameters.getOffset();
        String context = fileContent.substring(0, Math.min(offset, fileContent.length()));

        // 构建 prompt
        String prompt = "根据以下代码内容预测用户在光标处可能输入的内容：\n\n" + context;

        String suggestion = LLMClient.queryLLM(prompt);
        if (suggestion != null && !suggestion.isEmpty()) {
            LLMInlineCompletionManager.showInlineSuggestion(parameters.getEditor(), suggestion);
        }
    }
}
