package com.system.demo.LLM;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class LLMCompletionContributor extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters,
                                       @NotNull CompletionResultSet result) {
        if (!LLMState.isEnabled()) {
            return; // 没启用大模型，走默认补全
        }

        PsiFile file = parameters.getOriginalFile();
        Editor editor = parameters.getEditor();

        String fileContent = file.getText();
        int offset = parameters.getOffset();
        String prefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters);

        String prompt = "请根据以下完整文件内容预测用户在光标处可能输入的代码。\n\n"
                + fileContent
                + "\n\n光标上下文：" + prefix;

        String suggestion = LLMClient.queryLLM(prompt);
        if (suggestion != null && !suggestion.isEmpty()) {
            result.addElement(LookupElementBuilder.create(suggestion).withTypeText("AI Suggestion"));
        }
    }
}
