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

    public static String buildContextPrompt(PsiFile file, String selectedText) {
        String fileContent = getFullFileText(file);
        String fileName = getFileName(file);

        return String.format(
                "你是一个专业的代码助手。请分析以下代码并给出改进建议。\n\n" +
                        "文件: %s\n" +
                        "完整文件内容（仅作上下文参考）:\n" +
                        "```\n%s\n```\n\n" +
                        "需要改进的选中代码:\n" +
                        "```\n%s\n```\n\n" +
                        "重要要求:\n" +
                        "1. 只返回改进后的代码片段，不要返回整个文件\n" +
                        "2. 保持代码逻辑不变，主要改进：代码风格、可读性、性能\n" +
                        "3. 不要添加额外的解释或注释（除非必要）\n" +
                        "4. 不要使用 markdown 代码块标记\n" +
                        "5. 确保改进后的代码可以直接替换原选中代码\n" +
                        "6. 如果选中代码是方法的一部分，确保参数和返回值一致\n" +
                        "7. 保持相同的缩进和代码风格",
                fileName, fileContent, selectedText
        );
    }

    public static String buildContextPromptForComment(PsiFile file, String selectedText) {
        String fileContent = getFullFileText(file);
        String fileName = getFileName(file);

        return String.format(
                "你是一个专业的代码助手。请为以下代码添加清晰的注释。\n\n" +
                        "文件: %s\n" +
                        "完整文件内容（仅作上下文参考）:\n" +
                        "```\n%s\n```\n\n" +
                        "需要添加注释的选中代码:\n" +
                        "```\n%s\n```\n\n" +
                        "重要要求:\n" +
                        "1. 只返回添加注释后的代码片段，不要返回整个文件\n" +
                        "2. 保持原代码逻辑完全不变\n" +
                        "3. 为方法/类添加文档注释（JavaDoc风格）\n" +
                        "4. 为关键逻辑添加行内注释\n" +
                        "5. 注释要简洁、有用，避免废话\n" +
                        "6. 不要使用 markdown 代码块标记\n" +
                        "7. 确保注释后的代码可以直接替换原选中代码\n" +
                        "8. 保持相同的缩进和代码风格",
                fileName, fileContent, selectedText
        );
    }

    // 获取文件名
    public static String getFileName(PsiFile file) {
        return file != null ? file.getName() : "UnknownFile";
    }
}

