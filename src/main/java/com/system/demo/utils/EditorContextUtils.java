package com.system.demo.utils;

// 用来获取需要补全或者需要更改的代码片段的上下文（文件内容、选中内容）
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.psi.PsiFile;

public class EditorContextUtils {
    //获取整个文件内容
    public static String getFullFileText(PsiFile file) {
        return file != null ? file.getText() : "";
    }

     // 获取选中内容
    public static String getSelectedText(Editor editor) {
        SelectionModel selectionModel = editor.getSelectionModel();
        return selectionModel.getSelectedText();
    }

     // 构建带上下文的 prompt （改进代码功能）
    public static String buildContextPrompt(PsiFile file, String selectedText) {
        String fileContent = getFullFileText(file);
        return "这是完整文件内容：\n"
                + fileContent
                + "\n\n请参考整个上下文，改进下面选中的代码片段：\n"
                + selectedText
                + "\n\n要求：保持逻辑一致，但提高可读性和性能。（只返回代码，不要解释）";
    }
    // 给代码加注释功能
    public static String buildContextPromptForComment(PsiFile file, String selectedText) {
        String fileContent = getFullFileText(file);
        return "这是完整文件内容：\n"
                + fileContent
                + "\n\n请参考整个上下文，给下面代码片段加注释：\n"
                + selectedText
                + "\n\n要求：保持逻辑一致，但提高可读性和性能。（只返回代码，不要解释）";
    }

    // 获取文件名
    public static String getFileName(PsiFile file) {
        return file != null ? file.getName() : "UnknownFile";
    }
}

