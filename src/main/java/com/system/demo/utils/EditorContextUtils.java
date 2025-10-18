package com.system.demo.utils;

// 优化的上下文提取工具，更接近IDEA官方实现
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;
import java.lang.reflect.*;

/**
 * 增强的编辑器上下文工具类
 * 采用PSI树分析，提供更精准的代码上下文信息
 */
public class EditorContextUtils {
    
    // 缓存PSI元素解析结果，避免重复解析
    private static final Map<String, ContextCache> contextCache = new LinkedHashMap<String, ContextCache>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ContextCache> eldest) {
            return size() > 50; // 限制缓存大小
        }
    };

    /**
     * 上下文缓存项
     */
    private static class ContextCache {
        final CodeContext context;
        final long timestamp;
        final int documentVersion;

        ContextCache(CodeContext context, int documentVersion) {
            this.context = context;
            this.timestamp = System.currentTimeMillis();
            this.documentVersion = documentVersion;
        }

        boolean isValid(int currentVersion) {
            return System.currentTimeMillis() - timestamp < 5000 && documentVersion == currentVersion;
        }
    }

    /**
     * 代码上下文信息
     */
    public static class CodeContext {
        public String fileName;
        public String fileType;
        public String packageName;
        public List<String> imports;
        public String currentClassName;
        public String currentMethodName;
        public String currentMethodSignature;
        public String localVariables;
        public String precedingCode;
        public String followingCode;
        public String currentLine;
        public int cursorPositionInLine;
        public int indentLevel;
        public String scopeContext; // 当前作用域的代码

        public CodeContext() {
            this.imports = new ArrayList<>();
        }

        /**
         * 生成缓存键
         */
        public String getCacheKey() {
            return String.format("%s_%s_%d_%s", 
                fileName, currentMethodName, cursorPositionInLine, 
                precedingCode != null ? precedingCode.hashCode() : 0);
        }

        @Override
        public String toString() {
            return String.format("File: %s, Class: %s, Method: %s, Line: %s", 
                fileName, currentClassName, currentMethodName, currentLine);
        }
    }

    /**
     * 获取完整的代码上下文（带缓存）
     */
    public static CodeContext getCodeContext(Editor editor, PsiFile psiFile) {
        if (psiFile == null) return new CodeContext();

        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        
        // 生成缓存键
        String cacheKey = psiFile.getName() + "_" + offset + "_" + document.getModificationStamp();
        
        // 检查缓存
        ContextCache cached = contextCache.get(cacheKey);
        if (cached != null && cached.isValid((int) document.getModificationStamp())) {
            return cached.context;
        }

        // 提取新的上下文
        CodeContext context = new CodeContext();
        
        // 基本文件信息
        context.fileName = psiFile.getName();
        context.fileType = psiFile.getFileType().getName();
        
        // 使用PSI树提取结构化信息
        PsiElement element = psiFile.findElementAt(offset);
        if (element != null) {
            extractStructuredContext(element, context, document, offset);
        }
        
        // 提取文本级上下文
        extractTextContext(document, offset, context);
        
        // 缓存结果
        contextCache.put(cacheKey, new ContextCache(context, (int) document.getModificationStamp()));
        
        return context;
    }

    /**
     * 使用PSI提取结构化上下文（通用版本，兼容Python和Java）
     */
    private static void extractStructuredContext(PsiElement element, CodeContext context, 
                                                 Document document, int offset) {
        try {
            // 尝试使用反射查找Java相关的类（如果有的话）
            extractJavaContext(element, context, document, offset);
        } catch (Exception e) {
            // 如果不是Java文件或者没有Java支持，使用文本级提取
            extractTextBasedContext(element, context, document, offset);
        }
    }

    /**
     * 提取Java特定的上下文信息（使用反射避免编译错误）
     */
    private static void extractJavaContext(PsiElement element, CodeContext context,
                                          Document document, int offset) {
        try {
            // 使用反射调用 PsiTreeUtil.getParentOfType
            Class<?> psiTreeUtilClass = Class.forName("com.intellij.psi.util.PsiTreeUtil");
            
            // 尝试查找类信息
            Class<?> psiClassType = Class.forName("com.intellij.psi.PsiClass");
            Method getParentMethod = psiTreeUtilClass.getMethod("getParentOfType", PsiElement.class, Class.class);
            Object psiClass = getParentMethod.invoke(null, element, psiClassType);
            if (psiClass != null) {
                Method getName = psiClassType.getMethod("getName");
                context.currentClassName = (String) getName.invoke(psiClass);
            }

            // 尝试查找方法信息
            Class<?> psiMethodType = Class.forName("com.intellij.psi.PsiMethod");
            Object psiMethod = getParentMethod.invoke(null, element, psiMethodType);
            if (psiMethod != null) {
                Method getName = psiMethodType.getMethod("getName");
                context.currentMethodName = (String) getName.invoke(psiMethod);
                context.currentMethodSignature = buildMethodSignatureReflective(psiMethod, psiMethodType);
                
                // 提取作用域上下文
                Method getTextRange = psiMethodType.getMethod("getTextRange");
                Object textRange = getTextRange.invoke(psiMethod);
                Method getStartOffset = textRange.getClass().getMethod("getStartOffset");
                int methodStart = (Integer) getStartOffset.invoke(textRange);
                int contextStart = Math.max(methodStart, offset - 500);
                context.scopeContext = document.getText().substring(contextStart, offset);
            }

            // 尝试查找包信息
            Class<?> psiJavaFileType = Class.forName("com.intellij.psi.PsiJavaFile");
            PsiElement parent = element;
            while (parent != null) {
                if (psiJavaFileType.isInstance(parent)) {
                    Method getPackageName = psiJavaFileType.getMethod("getPackageName");
                    context.packageName = (String) getPackageName.invoke(parent);
                    break;
                }
                parent = parent.getParent();
            }
        } catch (Exception e) {
            // 静默失败，使用文本级提取
            extractTextBasedContext(element, context, document, offset);
        }
    }

    /**
     * 使用反射构建方法签名
     */
    private static String buildMethodSignatureReflective(Object method, Class<?> methodType) {
        try {
            Method getName = methodType.getMethod("getName");
            String name = (String) getName.invoke(method);
            return name + "(...)"; // 简化版本
        } catch (Exception e) {
            return "unknown()";
        }
    }

    /**
     * 基于文本的上下文提取（通用版本）
     */
    private static void extractTextBasedContext(PsiElement element, CodeContext context,
                                               Document document, int offset) {
        // 使用通用的PSI导航
        PsiElement parent = element;
        int depth = 0;
        
        // 向上查找可能的类或函数定义
        while (parent != null && depth < 10) {
            String text = parent.getText();
            if (text != null && text.length() < 1000) {
                // 尝试识别类名
                if (context.currentClassName == null && 
                    (text.contains("class ") || text.contains("interface "))) {
                    context.currentClassName = extractNameFromDefinition(text, "class|interface");
                }
                
                // 尝试识别方法/函数名
                if (context.currentMethodName == null && 
                    (text.contains("def ") || text.contains("function ") || 
                     text.matches(".*\\w+\\s*\\(.*\\).*\\{.*"))) {
                    context.currentMethodName = extractNameFromDefinition(text, "def|function|method");
                }
            }
            parent = parent.getParent();
            depth++;
        }
        
        // 提取作用域上下文
        int contextStart = Math.max(0, offset - 500);
        context.scopeContext = document.getText().substring(contextStart, offset);
    }

    /**
     * 从定义中提取名称
     */
    private static String extractNameFromDefinition(String text, String keywords) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches(".*\\b(" + keywords + ")\\b.*")) {
                // 简单的名称提取
                String[] parts = trimmed.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].matches(keywords)) {
                        String name = parts[i + 1].replaceAll("[^\\w].*", "");
                        if (!name.isEmpty()) {
                            return name;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 提取文本级上下文
     */
    private static void extractTextContext(Document document, int offset, CodeContext context) {
        String text = document.getText();
        
        // 获取当前行
        int lineNumber = document.getLineNumber(offset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        
        context.currentLine = text.substring(lineStart, lineEnd);
        context.cursorPositionInLine = offset - lineStart;
        
        // 计算缩进级别
        String linePrefix = text.substring(lineStart, offset);
        context.indentLevel = countLeadingSpaces(linePrefix) / 4;
        
        // 获取前置代码（前3-5行）
        int precedingStart = Math.max(0, lineStart - 300);
        context.precedingCode = text.substring(precedingStart, offset).trim();
        
        // 获取后续代码（后2-3行）
        int followingEnd = Math.min(text.length(), lineEnd + 150);
        context.followingCode = text.substring(offset, followingEnd).trim();
    }

    /**
     * 计算前导空格数
     */
    private static int countLeadingSpaces(String str) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ' ') count++;
            else if (c == '\t') count += 4;
            else break;
        }
        return count;
    }

    //获取整个文件内容（保留兼容性）
    public static String getFullFileText(PsiFile file) {
        return file != null ? file.getText() : "";
    }

    // 获取选中内容（保留兼容性）
    public static String getSelectedText(Editor editor) {
        SelectionModel selectionModel = editor.getSelectionModel();
        return selectionModel.getSelectedText();
    }

    // 构建带上下文的 prompt（保留兼容性）
    public static String buildContextPrompt(PsiFile file, String selectedText) {
        String fileContent = getFullFileText(file);
        return "这是完整文件内容：\n"
                + fileContent
                + "\n\n请参考整个上下文，改进下面选中的代码片段：\n"
                + selectedText
                + "\n\n要求：保持逻辑一致，但提高可读性和性能。（只返回代码，不要解释）";
    }

    // 获取文件名（保留兼容性）
    public static String getFileName(PsiFile file) {
        return file != null ? file.getName() : "UnknownFile";
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        contextCache.clear();
    }

    /**
     * 获取缓存统计
     */
    public static String getCacheStats() {
        return String.format("上下文缓存: %d 项", contextCache.size());
    }
}

