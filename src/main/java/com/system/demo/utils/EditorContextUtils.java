package com.system.demo.utils;

// 用来获取需要补全或者需要更改的代码片段的上下文（文件内容、选中内容）
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 优化的上下文工具类 - 更接近IDEA官方实现
 * 使用PSI树进行智能上下文提取，提供更精确的代码补全上下文
 */
public class EditorContextUtils {
    private static final int MAX_CONTEXT_LENGTH = 2000; // 最大上下文长度
    private static final int BEFORE_CURSOR_LINES = 20;  // 光标前的行数
    private static final int AFTER_CURSOR_LINES = 5;    // 光标后的行数

    /**
     * 获取智能上下文信息（核心方法）
     */
    @NotNull
    public static SmartContext getSmartContext(@NotNull PsiFile file, @NotNull Editor editor) {
        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        
        // 1. 获取当前PSI元素
        PsiElement element = file.findElementAt(offset);
        
        // 2. 提取周围上下文
        String beforeCursor = getTextBeforeCursor(document, offset, BEFORE_CURSOR_LINES);
        String afterCursor = getTextAfterCursor(document, offset, AFTER_CURSOR_LINES);
        
        // 3. 提取结构化信息（类、方法、imports等）
        String imports = extractImports(file);
        String classContext = extractClassContext(element);
        String methodContext = extractMethodContext(element);
        
        // 4. 提取相关的变量和类型信息
        String localVariables = extractLocalVariables(element);
        String typeContext = extractTypeContext(element);
        
        return new SmartContext(
            beforeCursor,
            afterCursor,
            imports,
            classContext,
            methodContext,
            localVariables,
            typeContext,
            file.getFileType().getName(),
            offset
        );
    }

    /**
     * 获取光标前的文本（多行）
     */
    private static String getTextBeforeCursor(Document document, int offset, int maxLines) {
        String text = document.getText();
        int start = offset;
        int lineCount = 0;
        
        // 向前查找指定行数
        for (int i = offset - 1; i >= 0 && lineCount < maxLines; i--) {
            if (text.charAt(i) == '\n') {
                lineCount++;
            }
            start = i;
        }
        
        String result = text.substring(start, offset);
        return limitLength(result, MAX_CONTEXT_LENGTH);
    }

    /**
     * 获取光标后的文本（多行）
     */
    private static String getTextAfterCursor(Document document, int offset, int maxLines) {
        String text = document.getText();
        int end = offset;
        int lineCount = 0;
        
        // 向后查找指定行数
        for (int i = offset; i < text.length() && lineCount < maxLines; i++) {
            if (text.charAt(i) == '\n') {
                lineCount++;
            }
            end = i + 1;
        }
        
        String result = text.substring(offset, Math.min(end, text.length()));
        return limitLength(result, MAX_CONTEXT_LENGTH / 2);
    }

    /**
     * 提取import语句（关键依赖信息）
     */
    private static String extractImports(@NotNull PsiFile file) {
        StringBuilder imports = new StringBuilder();
        
        // 遍历文件的顶层元素
        PsiElement[] children = file.getChildren();
        for (PsiElement child : children) {
            String className = child.getClass().getSimpleName();
            // 兼容Java和Python的import语句
            if (className.contains("Import")) {
                imports.append(child.getText()).append("\n");
            }
        }
        
        return limitLength(imports.toString(), 500);
    }

    /**
     * 提取类级上下文（类名、继承关系等）- 通用版本
     */
    private static String extractClassContext(@Nullable PsiElement element) {
        if (element == null) return "";
        
        try {
            // 向上查找类定义（通用方法）
            PsiElement current = element;
            int depth = 0;
            while (current != null && depth < 20) {
                String className = current.getClass().getSimpleName();
                String text = current.getText();
                
                // 检查是否是类定义
                if ((className.contains("Class") || className.contains("Def")) &&
                    (text.contains("class ") || text.contains("interface "))) {
                    // 获取类的签名部分
                    int bodyStart = text.indexOf('{');
                    if (bodyStart > 0) {
                        return limitLength(text.substring(0, bodyStart + 1), 300);
                    }
                    // 对Python等语言，查找冒号
                    int colonIndex = text.indexOf(':');
                    if (colonIndex > 0 && colonIndex < 100) {
                        String header = text.substring(0, colonIndex + 1);
                        return limitLength(header, 300);
                    }
                    return limitLength(text, 300);
                }
                
                current = current.getParent();
                depth++;
            }
        } catch (Exception e) {
            // 忽略错误
        }
        
        return "";
    }

    /**
     * 提取方法级上下文（方法签名、参数等）- 通用版本
     */
    private static String extractMethodContext(@Nullable PsiElement element) {
        if (element == null) return "";
        
        try {
            // 向上查找方法定义（通用方法）
            PsiElement current = element;
            int depth = 0;
            while (current != null && depth < 15) {
                String className = current.getClass().getSimpleName();
                String text = current.getText();
                
                // 检查是否是方法/函数定义
                if ((className.contains("Method") || className.contains("Function")) &&
                    text.length() > 10 && text.length() < 5000) {
                    return limitLength(text, 800);
                }
                
                current = current.getParent();
                depth++;
            }
        } catch (Exception e) {
            // 忽略错误
        }
        
        return "";
    }

    /**
     * 提取局部变量信息（通用版本，兼容多种语言）
     */
    private static String extractLocalVariables(@Nullable PsiElement element) {
        if (element == null) return "";
        
        StringBuilder vars = new StringBuilder();
        
        try {
            // 查找当前作用域内的变量声明（使用通用方法）
            PsiElement scope = element;
            int depth = 0;
            // 向上查找到方法或函数级别
            while (scope != null && depth < 10) {
                String className = scope.getClass().getSimpleName();
                if (className.contains("Method") || className.contains("Function") || 
                    className.contains("Block") || className.contains("Statement")) {
                    break;
                }
                scope = scope.getParent();
                depth++;
            }
            
            if (scope != null) {
                // 简单提取包含赋值语句的行（通用方法）
                String scopeText = scope.getText();
                if (scopeText.length() < 1000) { // 避免处理太大的作用域
                    String[] lines = scopeText.split("\\n");
                    for (String line : lines) {
                        if (line.contains("=") && !line.trim().startsWith("//") && 
                            !line.trim().startsWith("#")) {
                            vars.append(line.trim()).append("; ");
                            if (vars.length() > 300) break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误，返回空字符串
        }
        
        return limitLength(vars.toString(), 400);
    }

    /**
     * 提取类型信息（通用版本，兼容多种语言）
     */
    private static String extractTypeContext(@Nullable PsiElement element) {
        if (element == null) return "";
        
        try {
            // 收集附近使用的类型关键字（通用方法）
            Set<String> types = new HashSet<>();
            
            PsiElement parent = element.getParent();
            if (parent != null) {
                String parentText = parent.getText();
                if (parentText.length() < 500) {
                    // 使用正则匹配常见类型声明
                    String[] commonTypes = {"int", "String", "boolean", "double", "float", "long",
                        "List", "Map", "Set", "Array", "Object", "str", "dict", "list", "tuple"};
                    for (String type : commonTypes) {
                        if (parentText.contains(type)) {
                            types.add(type);
                        }
                    }
                }
            }
            
            return String.join(", ", types);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 限制字符串长度
     */
    private static String limitLength(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text != null ? text : "";
        }
        return text.substring(0, maxLength) + "...";
    }

    //获取整个文件内容（保留用于向后兼容）
    public static String getFullFileText(PsiFile file) {
        return file != null ? file.getText() : "";
    }

     // 获取选中内容
    public static String getSelectedText(Editor editor) {
        SelectionModel selectionModel = editor.getSelectionModel();
        return selectionModel.getSelectedText();
    }

     // 构建带上下文的 prompt
    public static String buildContextPrompt(PsiFile file, String selectedText) {
        String fileContent = getFullFileText(file);
        return "这是完整文件内容：\n"
                + fileContent
                + "\n\n请参考整个上下文，改进下面选中的代码片段：\n"
                + selectedText
                + "\n\n要求：保持逻辑一致，但提高可读性和性能。（只返回代码，不要解释）";
    }

    // 获取文件名
    public static String getFileName(PsiFile file) {
        return file != null ? file.getName() : "UnknownFile";
    }

    /**
     * 智能上下文数据类
     */
    public static class SmartContext {
        public final String beforeCursor;
        public final String afterCursor;
        public final String imports;
        public final String classContext;
        public final String methodContext;
        public final String localVariables;
        public final String typeContext;
        public final String fileType;
        public final int cursorOffset;

        public SmartContext(
            String beforeCursor,
            String afterCursor,
            String imports,
            String classContext,
            String methodContext,
            String localVariables,
            String typeContext,
            String fileType,
            int cursorOffset
        ) {
            this.beforeCursor = beforeCursor;
            this.afterCursor = afterCursor;
            this.imports = imports;
            this.classContext = classContext;
            this.methodContext = methodContext;
            this.localVariables = localVariables;
            this.typeContext = typeContext;
            this.fileType = fileType;
            this.cursorOffset = cursorOffset;
        }

        /**
         * 生成缓存键（用于缓存查找）
         */
        public String getCacheKey() {
            // 使用关键信息生成缓存键
            return String.format("%d_%s_%s",
                beforeCursor.hashCode(),
                methodContext.hashCode(),
                fileType
            );
        }

        /**
         * 构建优化的Prompt
         */
        public String buildOptimizedPrompt() {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是专业的代码补全助手。\n\n");
            
            if (!imports.isEmpty()) {
                prompt.append("=== Imports ===\n").append(imports).append("\n\n");
            }
            
            if (!classContext.isEmpty()) {
                prompt.append("=== Class Context ===\n").append(classContext).append("\n\n");
            }
            
            if (!methodContext.isEmpty()) {
                prompt.append("=== Method Context ===\n").append(methodContext).append("\n\n");
            }
            
            if (!localVariables.isEmpty()) {
                prompt.append("=== Local Variables ===\n").append(localVariables).append("\n\n");
            }
            
            prompt.append("=== 光标位置 ===\n");
            prompt.append(beforeCursor).append("█").append(afterCursor);
            prompt.append("\n\n请提供光标处(█)的代码补全，只输出补全内容，不要解释。");
            
            return prompt.toString();
        }
    }
}

