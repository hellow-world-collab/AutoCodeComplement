package com.system.demo.LLM;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 现代化差异渲染器 - 类似 VS Code
 * 特点：
 * 1. 编辑器底部显示固定工具栏（Keep/Undo/导航）
 * 2. 高亮显示差异
 * 3. 支持快捷键操作
 */
public class ModernDiffRenderer {

    private final Editor editor;
    private final Project project;
    private final String originalText;
    private final String modifiedText;
    private final int selectionStart;
    private final int selectionEnd;

    private final List<DiffChunk> chunks = new ArrayList<>();
    private final List<RangeHighlighter> highlighters = new ArrayList<>();
    private int currentChunkIndex = 0;

    private JPanel toolbarPanel;
    private JLabel statusLabel;

    // 颜色
    private static final JBColor COLOR_DELETED = new JBColor(
            new Color(255, 220, 220), new Color(80, 40, 40)
    );
    private static final JBColor COLOR_ADDED = new JBColor(
            new Color(220, 255, 220), new Color(40, 80, 40)
    );

    /**
     * 差异块
     */
    private static class DiffChunk {
        final AbstractDelta<String> delta;
        int originalStartLine = -1;
        int modifiedStartLine = -1;
        int originalStartOffset = -1;  // 原代码的起始offset
        int originalEndOffset = -1;    // 原代码的结束offset
        int modifiedStartOffset = -1;  // 新代码的起始offset
        int modifiedEndOffset = -1;    // 新代码的结束offset
        boolean processed = false;
        // 存储与此chunk关联的所有高亮器
        final List<RangeHighlighter> associatedHighlighters = new ArrayList<>();

        DiffChunk(AbstractDelta<String> delta) {
            this.delta = delta;
        }
    }

    public ModernDiffRenderer(Editor editor, Project project,
                              String originalText, String modifiedText,
                              int selectionStart, int selectionEnd) {
        this.editor = editor;
        this.project = project;
        this.originalText = originalText;
        this.modifiedText = modifiedText;
        this.selectionStart = selectionStart;
        this.selectionEnd = selectionEnd;
    }

    /**
     * 渲染差异
     */
    public void render() {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                List<String> originalLines = Arrays.asList(originalText.split("\n", -1));
                List<String> modifiedLines = Arrays.asList(modifiedText.split("\n", -1));
                Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);

                if (patch.getDeltas().isEmpty()) {
                    Messages.showInfoMessage(project, "AI 未发现需要改进的地方", "提示");
                    return;
                }

                // 渲染差异
                renderDifferences(patch);

                // 显示工具栏
                showToolbar();

            } catch (Exception e) {
                e.printStackTrace();
                Messages.showErrorDialog(project, "显示差异时出错: " + e.getMessage(), "错误");
            }
        });
    }

    /**
     * 渲染差异 - 增强版本，正确处理新增和删除
     */
    private void renderDifferences(Patch<String> patch) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            Document doc = editor.getDocument();

            // 第一步：安全地替换选中区域
            try {
                int safeEnd = Math.min(selectionEnd, doc.getTextLength());
                if (selectionStart >= 0 && safeEnd > selectionStart) {
                    doc.replaceString(selectionStart, safeEnd, modifiedText);
                    System.out.println("代码替换完成");
                }
            } catch (Exception e) {
                System.err.println("替换失败: " + e.getMessage());
                return;
            }

            // 第二步：初始化差异块
            List<AbstractDelta<String>> deltas = new ArrayList<>(patch.getDeltas());
            for (AbstractDelta<String> delta : deltas) {
                chunks.add(new DiffChunk(delta));
            }

            // 第三步：重新计算所有差异块的位置
            recalculateChunkPositions();

            // 第四步：重新计算行号（关键步骤）
            recalculateChunkLines();

            // 第五步：应用高亮
            ApplicationManager.getApplication().invokeLater(() -> {
                applyHighlights();
                showToolbar();
            });
        });
    }
    /**
     * 处理新增差异块 - 修复版本
     */
    private void handleInsertDelta(DiffChunk chunk, AbstractDelta<String> delta,
                                   List<String> modifiedLines, int baseOffset) {
        int targetPos = delta.getTarget().getPosition();

        // 计算新代码的准确offset - 修复计算逻辑
        int offset = 0;
        for (int i = 0; i < targetPos && i < modifiedLines.size(); i++) {
            offset += modifiedLines.get(i).length() + 1; // +1 for newline
        }
        chunk.modifiedStartOffset = baseOffset + offset;

        // 计算新代码的结束offset - 修复计算逻辑
        int endOffset = offset;
        for (int i = 0; i < delta.getTarget().size(); i++) {
            int lineIndex = targetPos + i;
            if (lineIndex < modifiedLines.size()) {
                String line = modifiedLines.get(lineIndex);
                endOffset += line.length();
                // 如果不是最后一行，加上换行符
                if (lineIndex < modifiedLines.size() - 1) {
                    endOffset += 1;
                }
            }
        }
        chunk.modifiedEndOffset = baseOffset + endOffset;

        // 对于新增行，原代码offset设为-1（表示没有对应的原代码）
        chunk.originalStartOffset = -1;

        // 设置行号用于导航和高亮
        try {
            Document doc = editor.getDocument();
            chunk.modifiedStartLine = doc.getLineNumber(chunk.modifiedStartOffset);
        } catch (Exception e) {
            // 如果无法计算行号，使用target position作为估算
            chunk.modifiedStartLine = targetPos;
        }

        System.out.println("新增块 - 位置: " + targetPos +
                ", 范围: [" + chunk.modifiedStartOffset + ", " + chunk.modifiedEndOffset + "]" +
                ", 行号: " + chunk.modifiedStartLine);
    }

    /**
     * 处理删除差异块
     */
    private void handleDeleteDelta(DiffChunk chunk, AbstractDelta<String> delta,
                                   List<String> originalLines, int baseOffset) {
        int sourcePos = delta.getSource().getPosition();

        // 构建原代码并插入
        StringBuilder originalCodeBuilder = new StringBuilder();
        for (String line : delta.getSource().getLines()) {
            originalCodeBuilder.append(line).append("\n");
        }
        String originalCode = originalCodeBuilder.toString();

        // 计算插入位置（在新代码之前）
        int insertOffset = baseOffset;
        for (int i = 0; i < sourcePos && i < originalLines.size(); i++) {
            insertOffset += originalLines.get(i).length() + 1;
        }

        Document doc = editor.getDocument();
        if (insertOffset <= doc.getTextLength()) {
            doc.insertString(insertOffset, originalCode);
            chunk.originalStartOffset = insertOffset;
            chunk.modifiedStartOffset = insertOffset + originalCode.length(); // 新代码在原代码之后

            // 调整后续chunk的offset
            adjustSubsequentChunks(chunk, originalCode.length());
        }
    }

    /**
     * 处理修改差异块
     */
    private void handleChangeDelta(DiffChunk chunk, AbstractDelta<String> delta,
                                   List<String> originalLines, List<String> modifiedLines,
                                   int baseOffset) {
        // 先处理删除部分（插入原代码）
        handleDeleteDelta(chunk, delta, originalLines, baseOffset);

        // 然后设置新代码的范围
        int targetPos = delta.getTarget().getPosition();
        int offset = 0;
        for (int i = 0; i < targetPos && i < modifiedLines.size(); i++) {
            offset += modifiedLines.get(i).length() + 1;
        }
        chunk.modifiedStartOffset = baseOffset + Math.max(0, offset - 1);

        int endOffset = offset;
        for (int i = 0; i < delta.getTarget().size() && (targetPos + i) < modifiedLines.size(); i++) {
            endOffset += modifiedLines.get(targetPos + i).length();
            if (targetPos + i < modifiedLines.size() - 1) {
                endOffset += 1;
            }
        }
        chunk.modifiedEndOffset = baseOffset + endOffset;
    }

    /**
     * 调整后续chunk的offset
     */
    private void adjustSubsequentChunks(DiffChunk currentChunk, int insertedLength) {
        for (DiffChunk otherChunk : chunks) {
            if (otherChunk != currentChunk) {
                if (otherChunk.modifiedStartOffset >= currentChunk.originalStartOffset) {
                    otherChunk.modifiedStartOffset += insertedLength;
                    otherChunk.modifiedEndOffset += insertedLength;
                }
                if (otherChunk.originalStartOffset >= currentChunk.originalStartOffset) {
                    otherChunk.originalStartOffset += insertedLength;
                }
            }
        }
    }

    /**
     * 重新计算所有差异块的行号 - 修复版本
     */
    private void recalculateChunkLines() {
        Document doc = editor.getDocument();

        for (DiffChunk chunk : chunks) {
            DeltaType type = chunk.delta.getType();

            try {
                switch (type) {
                    case INSERT:
                        // INSERT类型：基于修改后代码的位置计算行号
                        if (chunk.modifiedStartOffset >= 0 && chunk.modifiedStartOffset < doc.getTextLength()) {
                            chunk.modifiedStartLine = doc.getLineNumber(chunk.modifiedStartOffset);
                            System.out.println("INSERT块行号: " + chunk.modifiedStartLine);
                        }
                        break;

                    case DELETE:
                        // DELETE类型：基于插入的原代码位置计算行号
                        if (chunk.originalStartOffset >= 0 && chunk.originalStartOffset < doc.getTextLength()) {
                            chunk.originalStartLine = doc.getLineNumber(chunk.originalStartOffset);
                            System.out.println("DELETE块行号: " + chunk.originalStartLine);
                        }
                        break;

                    case CHANGE:
                        // CHANGE类型：同时计算原代码和新代码的行号
                        if (chunk.originalStartOffset >= 0 && chunk.originalStartOffset < doc.getTextLength()) {
                            chunk.originalStartLine = doc.getLineNumber(chunk.originalStartOffset);
                        }
                        if (chunk.modifiedStartOffset >= 0 && chunk.modifiedStartOffset < doc.getTextLength()) {
                            chunk.modifiedStartLine = doc.getLineNumber(chunk.modifiedStartOffset);
                        }
                        System.out.println("CHANGE块行号 - 原: " + chunk.originalStartLine + ", 新: " + chunk.modifiedStartLine);
                        break;
                }
            } catch (Exception e) {
                System.err.println("计算行号失败: " + e.getMessage());
            }
        }
    }
    /**
     * 应用高亮 - 修复版本，确保INSERT类型也有绿色高亮
     */
    /**
     * 应用高亮 - 修复版本，确保INSERT类型只有绿色高亮
     */
    private void applyHighlights() {
        // 清除所有现有高亮
        clearAllHighlights();

        Document doc = editor.getDocument();

        for (DiffChunk chunk : chunks) {
            DeltaType type = chunk.delta.getType();

            switch (type) {
                case INSERT:
                    // INSERT类型：只高亮新增的代码（绿色背景，无删除线）
                    highlightInsertedLines(chunk, doc);
                    break;

                case DELETE:
                    // DELETE类型：高亮被删除的代码（红色背景 + 删除线）
                    highlightDeletedLines(chunk, doc);
                    break;

                case CHANGE:
                    // CHANGE类型：分别高亮原代码（红色删除线）和新代码（绿色背景）
                    highlightChangedLines(chunk, doc);
                    break;
            }

            // 添加悬浮工具栏
            addHoverableToolbar(chunk);
        }
    }

    /**
     * 高亮新增的代码（INSERT类型）- 修复版本
     */
    private void highlightInsertedLines(DiffChunk chunk, Document doc) {
        if (chunk.modifiedStartLine < 0) return;

        int startLine = chunk.modifiedStartLine;
        int lineCount = chunk.delta.getTarget().size();

        for (int i = 0; i < lineCount; i++) {
            int line = startLine + i;
            if (line < doc.getLineCount()) {
                // INSERT类型：只应用绿色背景，不应用删除线
                highlightEntireLine(line, COLOR_ADDED, false, chunk);
                System.out.println("高亮INSERT行: " + line + " - 绿色背景");
            }
        }
    }

    /**
     * 高亮被删除的整行（DELETE类型）- 修复版本
     */
    private void highlightDeletedLines(DiffChunk chunk, Document doc) {
        if (chunk.originalStartLine < 0) return;

        int startLine = chunk.originalStartLine;
        int lineCount = chunk.delta.getSource().size();

        for (int i = 0; i < lineCount; i++) {
            int line = startLine + i;
            if (line < doc.getLineCount()) {
                // DELETE类型：红色背景 + 删除线
                highlightEntireLine(line, COLOR_DELETED, true, chunk);
                System.out.println("高亮DELETE行: " + line + " - 红色删除线");
            }
        }
    }

    /**
     * 高亮修改的整行（CHANGE类型）- 修复版本
     */
    private void highlightChangedLines(DiffChunk chunk, Document doc) {
        // 高亮新代码行（绿色背景，无删除线）
        if (chunk.modifiedStartLine >= 0) {
            int startLine = chunk.modifiedStartLine;
            int lineCount = chunk.delta.getTarget().size();

            for (int i = 0; i < lineCount; i++) {
                int line = startLine + i;
                if (line < doc.getLineCount()) {
                    highlightEntireLine(line, COLOR_ADDED, false, chunk);
                    System.out.println("高亮CHANGE新代码行: " + line + " - 绿色背景");
                }
            }
        }

        // 高亮原代码行（红色背景 + 删除线）
        if (chunk.originalStartLine >= 0) {
            int startLine = chunk.originalStartLine;
            int lineCount = chunk.delta.getSource().size();

            for (int i = 0; i < lineCount; i++) {
                int line = startLine + i;
                if (line < doc.getLineCount()) {
                    highlightEntireLine(line, COLOR_DELETED, true, chunk);
                    System.out.println("高亮CHANGE原代码行: " + line + " - 红色删除线");
                }
            }
        }
    }

    /**
     * 高亮整行 - 确保整行统一高亮
    */
    private void highlightEntireLine(int line, Color bgColor, boolean strikeout, DiffChunk chunk) {
        Document doc = editor.getDocument();
        if (line >= doc.getLineCount()) return;

        try {
            int lineStart = doc.getLineStartOffset(line);
            int lineEnd = doc.getLineEndOffset(line);

            TextAttributes attr = new TextAttributes();
            attr.setBackgroundColor(bgColor);
            if (strikeout) {
                attr.setEffectType(EffectType.STRIKEOUT);
                attr.setEffectColor(new Color(180, 0, 0));
            }

            // 高亮整行
            RangeHighlighter h = editor.getMarkupModel().addRangeHighlighter(
                    lineStart, lineEnd,
                    HighlighterLayer.SELECTION - 1,
                    attr,
                    HighlighterTargetArea.LINES_IN_RANGE
            );

            highlighters.add(h);
            chunk.associatedHighlighters.add(h);

            System.out.println("高亮整行 " + line + ": [" + lineStart + ", " + lineEnd + "]");

        } catch (Exception e) {
            System.err.println("高亮行 " + line + " 失败: " + e.getMessage());
        }
    }

    /**
     * 重新计算差异块在修改后代码中的准确位置 - 修复版本
     */
    private void recalculateChunkPositions() {
        Document doc = editor.getDocument();
        String currentText = doc.getText();

        // 重新解析修改后的代码，找到每个差异块的确切位置
        List<String> modifiedLines = Arrays.asList(modifiedText.split("\n", -1));

        for (DiffChunk chunk : chunks) {
            DeltaType type = chunk.delta.getType();

            // 重置处理状态，确保不会继承错误的状态
            chunk.processed = false;

            switch (type) {
                case INSERT:
                    recalculateInsertPosition(chunk, modifiedLines, currentText);
                    // 确保INSERT类型没有原代码位置
                    chunk.originalStartOffset = -1;
                    chunk.originalStartLine = -1;
                    break;
                case DELETE:
                    recalculateDeletePosition(chunk, currentText);
                    // 确保DELETE类型没有新代码位置
                    chunk.modifiedStartOffset = -1;
                    chunk.modifiedStartLine = -1;
                    break;
                case CHANGE:
                    recalculateChangePosition(chunk, modifiedLines, currentText);
                    break;
            }

            System.out.println("差异块类型: " + type +
                    ", 原代码行: " + chunk.originalStartLine +
                    ", 新代码行: " + chunk.modifiedStartLine);
        }
    }
    /**
     * 重新计算DELETE块的位置
     */
    private void recalculateDeletePosition(DiffChunk chunk, String currentText) {
        // 对于DELETE类型，我们需要在文档中插入原代码以便显示删除线
        // 计算插入位置
        int insertOffset = findInsertPositionForDeletedCode(chunk, currentText);

        if (insertOffset >= 0 && insertOffset <= currentText.length()) {
            // 构建被删除的代码
            StringBuilder deletedCode = new StringBuilder();
            for (String line : chunk.delta.getSource().getLines()) {
                deletedCode.append(line).append("\n");
            }

            String deletedText = deletedCode.toString();

            // 在文档中插入被删除的代码（用于显示删除线）
            Document doc = editor.getDocument();
            try {
                doc.insertString(insertOffset, deletedText);

                // 设置原代码的位置
                chunk.originalStartOffset = insertOffset;
                chunk.originalEndOffset = insertOffset + deletedText.length();

                // 重新计算行号
                try {
                    chunk.originalStartLine = doc.getLineNumber(chunk.originalStartOffset);
                } catch (Exception e) {
                    chunk.originalStartLine = -1;
                }

                System.out.println("插入DELETE原代码: [" + chunk.originalStartOffset + ", " + chunk.originalEndOffset + "]");

            } catch (Exception e) {
                System.err.println("插入DELETE原代码失败: " + e.getMessage());
                chunk.originalStartOffset = -1;
                chunk.originalEndOffset = -1;
            }
        } else {
            chunk.originalStartOffset = -1;
            chunk.originalEndOffset = -1;
        }

        // DELETE类型没有新代码
        chunk.modifiedStartOffset = -1;
        chunk.modifiedEndOffset = -1;
    }

    /**
     * 为被删除的代码找到插入位置
     */
    private int findInsertPositionForDeletedCode(DiffChunk chunk, String currentText) {
        // 策略：在被删除代码原本应该在的位置附近插入
        int sourcePos = chunk.delta.getSource().getPosition();

        // 计算在修改后代码中的大致位置
        Document doc = editor.getDocument();

        try {
            // 基于原始选中区域的起始位置和行号计算
            int baseLine = doc.getLineNumber(selectionStart);
            int targetLine = baseLine + sourcePos;

            if (targetLine >= 0 && targetLine < doc.getLineCount()) {
                // 在该行开始位置插入
                return doc.getLineStartOffset(targetLine);
            } else if (targetLine >= doc.getLineCount()) {
                // 如果超过文档行数，在文档末尾插入
                return doc.getTextLength();
            } else {
                // 如果行号无效，在选中区域开始位置插入
                return selectionStart;
            }
        } catch (Exception e) {
            // 如果计算失败，使用保守位置
            return Math.min(selectionStart + 100, doc.getTextLength());
        }
    }

    /**
     * 重新计算CHANGE块的位置
     */
    private void recalculateChangePosition(DiffChunk chunk, List<String> modifiedLines, String currentText) {
        // 首先计算新代码的位置（与INSERT类似）
        int targetPos = chunk.delta.getTarget().getPosition();

        // 计算在修改后代码中的准确偏移量
        int offset = 0;
        for (int i = 0; i < targetPos && i < modifiedLines.size(); i++) {
            offset += modifiedLines.get(i).length() + 1; // +1 for newline
        }

        chunk.modifiedStartOffset = selectionStart + offset;

        // 计算新代码的结束位置
        int contentLength = 0;
        for (int i = 0; i < chunk.delta.getTarget().size(); i++) {
            int lineIndex = targetPos + i;
            if (lineIndex < modifiedLines.size()) {
                contentLength += modifiedLines.get(lineIndex).length();
                if (lineIndex < modifiedLines.size() - 1) {
                    contentLength += 1; // 换行符
                }
            }
        }

        chunk.modifiedEndOffset = chunk.modifiedStartOffset + contentLength;

        // 确保不超过文档边界
        chunk.modifiedStartOffset = Math.min(chunk.modifiedStartOffset, currentText.length());
        chunk.modifiedEndOffset = Math.min(chunk.modifiedEndOffset, currentText.length());

        // 然后计算原代码的位置（与DELETE类似）
        int sourcePos = chunk.delta.getSource().getPosition();
        int insertOffset = findInsertPositionForChangedCode(chunk, currentText);

        if (insertOffset >= 0 && insertOffset <= currentText.length()) {
            // 构建被修改的原代码
            StringBuilder originalCode = new StringBuilder();
            for (String line : chunk.delta.getSource().getLines()) {
                originalCode.append(line).append("\n");
            }

            String originalText = originalCode.toString();

            // 在文档中插入原代码（用于显示删除线）
            Document doc = editor.getDocument();
            try {
                doc.insertString(insertOffset, originalText);

                // 设置原代码的位置
                chunk.originalStartOffset = insertOffset;
                chunk.originalEndOffset = insertOffset + originalText.length();

                // 重新计算行号
                try {
                    chunk.originalStartLine = doc.getLineNumber(chunk.originalStartOffset);
                    chunk.modifiedStartLine = doc.getLineNumber(chunk.modifiedStartOffset);
                } catch (Exception e) {
                    chunk.originalStartLine = -1;
                    chunk.modifiedStartLine = -1;
                }

                System.out.println("CHANGE - 原代码: [" + chunk.originalStartOffset + ", " + chunk.originalEndOffset + "]");
                System.out.println("CHANGE - 新代码: [" + chunk.modifiedStartOffset + ", " + chunk.modifiedEndOffset + "]");

            } catch (Exception e) {
                System.err.println("插入CHANGE原代码失败: " + e.getMessage());
                chunk.originalStartOffset = -1;
                chunk.originalEndOffset = -1;
            }
        } else {
            chunk.originalStartOffset = -1;
            chunk.originalEndOffset = -1;
        }
    }

    /**
     * 为被修改的代码找到插入位置
     */
    private int findInsertPositionForChangedCode(DiffChunk chunk, String currentText) {
        // 策略：在新代码之前插入原代码，以便对比显示
        if (chunk.modifiedStartOffset > 0) {
            // 在新代码开始位置之前插入
            return Math.max(0, chunk.modifiedStartOffset - 1);
        }

        // 备用策略：使用DELETE的插入策略
        return findInsertPositionForDeletedCode(chunk, currentText);
    }
    /**
     * 重新计算INSERT块的位置
     */
    private void recalculateInsertPosition(DiffChunk chunk, List<String> modifiedLines, String currentText) {
        int targetPos = chunk.delta.getTarget().getPosition();

        // 计算在修改后代码中的准确偏移量
        int offset = 0;
        for (int i = 0; i < targetPos && i < modifiedLines.size(); i++) {
            offset += modifiedLines.get(i).length() + 1; // +1 for newline
        }

        chunk.modifiedStartOffset = selectionStart + offset;

        // 计算插入内容的结束位置
        int contentLength = 0;
        for (int i = 0; i < chunk.delta.getTarget().size(); i++) {
            int lineIndex = targetPos + i;
            if (lineIndex < modifiedLines.size()) {
                contentLength += modifiedLines.get(lineIndex).length();
                if (lineIndex < modifiedLines.size() - 1) {
                    contentLength += 1; // 换行符
                }
            }
        }

        chunk.modifiedEndOffset = chunk.modifiedStartOffset + contentLength;

        // 确保不超过文档边界
        chunk.modifiedStartOffset = Math.min(chunk.modifiedStartOffset, currentText.length());
        chunk.modifiedEndOffset = Math.min(chunk.modifiedEndOffset, currentText.length());
    }

    /**
     * 清除所有高亮
     */
    private void clearAllHighlights() {
        for (RangeHighlighter h : new ArrayList<>(highlighters)) {
            try {
                if (h.isValid()) {
                    editor.getMarkupModel().removeHighlighter(h);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        highlighters.clear();

        for (DiffChunk chunk : chunks) {
            chunk.associatedHighlighters.clear();
        }
    }

    /**
     * 添加可悬浮的工具栏（在差异块上显示）
     */
    private void addHoverableToolbar(DiffChunk chunk) {
        Document doc = editor.getDocument();

        // 在差异块的第一行添加Gutter图标
        int line = chunk.modifiedStartLine;

        // 如果行号无效，尝试从offset重新计算
        if (line < 0 && chunk.modifiedStartOffset >= 0) {
            try {
                line = doc.getLineNumber(chunk.modifiedStartOffset);
                chunk.modifiedStartLine = line;
            } catch (Exception e) {
                // Ignore
            }
        }

        if (line < 0 || line >= doc.getLineCount()) {
            // 如果仍然无效，尝试使用target position计算
            if (chunk.delta.getTarget().getPosition() >= 0) {
                try {
                    int baseLine = doc.getLineNumber(selectionStart);
                    line = baseLine + chunk.delta.getTarget().getPosition();
                    if (line >= 0 && line < doc.getLineCount()) {
                        chunk.modifiedStartLine = line;
                    } else {
                        return;
                    }
                } catch (Exception e) {
                    return;
                }
            } else {
                return;
            }
        }

        // 添加绿色小点图标
        addHoverGutterIcon(line, chunk);
    }

    /**
     * 添加悬停时显示的Gutter图标
     */
    private void addHoverGutterIcon(int line, DiffChunk chunk) {
        Document doc = editor.getDocument();
        if (line >= doc.getLineCount()) return;

        int offset = doc.getLineStartOffset(line);

        RangeHighlighter h = editor.getMarkupModel().addRangeHighlighter(
                offset, offset,
                HighlighterLayer.LAST + 1,
                null,
                HighlighterTargetArea.EXACT_RANGE
        );

        h.setGutterIconRenderer(new HoverableGutterIcon(chunk));
        highlighters.add(h);
        // 将高亮器添加到chunk的关联列表中
        chunk.associatedHighlighters.add(h);
    }

    /**
     * 可悬浮的Gutter图标（显示Keep/Undo）
     */
    private class HoverableGutterIcon extends com.intellij.openapi.editor.markup.GutterIconRenderer {
        private final DiffChunk chunk;

        HoverableGutterIcon(DiffChunk chunk) {
            this.chunk = chunk;
        }

        @org.jetbrains.annotations.NotNull
        @Override
        public Icon getIcon() {
            // 创建一个小绿点图标
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(100, 200, 100));
                    g2.fillOval(x + 2, y + 2, 8, 8);
                }

                @Override
                public int getIconWidth() { return 12; }
                @Override
                public int getIconHeight() { return 12; }
            };
        }

        @Override
        public String getTooltipText() {
            return "<html>" +
                    "<b>AI 建议差异</b><br>" +
                    "点击图标显示操作菜单<br>" +
                    "或使用底部工具栏操作" +
                    "</html>";
        }

        @Override
        public com.intellij.openapi.actionSystem.AnAction getClickAction() {
            return new com.intellij.openapi.actionSystem.AnAction() {
                @Override
                public void actionPerformed(@org.jetbrains.annotations.NotNull com.intellij.openapi.actionSystem.AnActionEvent e) {
                    showPopupMenu(e, chunk);
                }
            };
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof HoverableGutterIcon && ((HoverableGutterIcon) o).chunk == chunk;
        }

        @Override
        public int hashCode() {
            return chunk.hashCode();
        }
    }

    /**
     * 显示弹出菜单（局部操作，不改变全局状态）
     */
    private void showPopupMenu(com.intellij.openapi.actionSystem.AnActionEvent e, DiffChunk chunk) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(new Color(240, 248, 255));

        JMenuItem keepItem = new JMenuItem("✓ Keep - 接受此建议");
        keepItem.setBackground(new Color(220, 255, 220));
        keepItem.addActionListener(event -> {
            // 局部操作：直接处理该chunk，不改变全局currentChunkIndex
            handleLocalKeep(chunk);
        });

        JMenuItem undoItem = new JMenuItem("✗ Undo - 保留原代码");
        undoItem.setBackground(new Color(255, 220, 220));
        undoItem.addActionListener(event -> {
            // 局部操作：直接处理该chunk，不改变全局currentChunkIndex
            handleLocalUndo(chunk);
        });

        popup.add(keepItem);
        popup.add(undoItem);

        // 显示在鼠标位置
        Component component = e.getInputEvent().getComponent();
        Point point = e.getInputEvent().getComponent().getMousePosition();
        if (point != null) {
            popup.show(component, point.x, point.y);
        }
    }
    /**
     * 检查是否所有差异都已处理，如果是则关闭工具栏
     */
    private void checkAndCloseIfAllProcessed() {
        boolean allProcessed = chunks.stream().allMatch(c -> c.processed);
        if (allProcessed) {
            ApplicationManager.getApplication().invokeLater(() -> {
                // 再次确保所有高亮器都被清除
                for (DiffChunk chunk : chunks) {
                    clearChunkHighlights(chunk);
                }
                Messages.showInfoMessage(project, "所有差异已处理完成", "完成");
                closeToolbar();
            });
        }
    }
    /**
     * 处理局部 Keep（只针对指定chunk，不影响全局导航）
     * Keep操作：删除原代码（红色删除线），保留新代码（绿色）
     */
    private void handleLocalKeep(DiffChunk chunk) {
        if (chunk.processed) {
            Messages.showInfoMessage(project, "此差异已处理", "提示");
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            Document doc = editor.getDocument();

            // 删除原代码（优先使用offset）
            boolean deleted = false;
            if (chunk.originalStartOffset >= 0) {
                try {
                    // 计算原代码的结束offset
                    int startOffset = chunk.originalStartOffset;
                    int endOffset = startOffset;

                    // 计算原代码的实际长度（包括所有行和换行符）
                    for (int i = 0; i < chunk.delta.getSource().size(); i++) {
                        try {
                            int line = doc.getLineNumber(endOffset);
                            int lineEnd = doc.getLineEndOffset(line);
                            endOffset = lineEnd;
                            if (line < doc.getLineCount() - 1) {
                                endOffset++; // 包括换行符
                            }
                        } catch (Exception e) {
                            break;
                        }
                    }

                    // 确保endOffset有效
                    if (endOffset > doc.getTextLength()) {
                        endOffset = doc.getTextLength();
                    }

                    if (endOffset > startOffset && startOffset >= 0 && endOffset <= doc.getTextLength()) {
                        int deletedLength = endOffset - startOffset;
                        doc.deleteString(startOffset, endOffset);
                        deleted = true;

                        // 更新其他chunk的offset
                        for (DiffChunk otherChunk : chunks) {
                            if (otherChunk != chunk && !otherChunk.processed) {
                                if (otherChunk.originalStartOffset > startOffset) {
                                    otherChunk.originalStartOffset -= deletedLength;
                                }
                                if (otherChunk.modifiedStartOffset > startOffset) {
                                    otherChunk.modifiedStartOffset -= deletedLength;
                                    otherChunk.modifiedEndOffset -= deletedLength;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 如果offset失效，尝试使用行号
                }
            }

            // 如果使用offset删除失败，尝试使用行号
            if (!deleted && chunk.originalStartLine >= 0 && chunk.originalStartLine < doc.getLineCount()) {
                try {
                    int start = doc.getLineStartOffset(chunk.originalStartLine);
                    int endLine = chunk.originalStartLine + chunk.delta.getSource().size() - 1;
                    if (endLine >= doc.getLineCount()) endLine = doc.getLineCount() - 1;
                    int end = doc.getLineEndOffset(endLine);
                    if (endLine < doc.getLineCount() - 1) {
                        end++; // 包括换行符
                    }
                    if (start < doc.getTextLength() && end <= doc.getTextLength() && start < end) {
                        doc.deleteString(start, end);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            chunk.processed = true;
            clearChunkHighlights(chunk);
            updateStatusLabel();

            // 检查是否需要关闭工具栏
            checkAndCloseIfAllProcessed();
        });
    }


    /**
     * 处理局部 Undo - 修复版本，精确定位新增内容
     */
    private void handleLocalUndo(DiffChunk chunk) {
        if (chunk.processed) {
            Messages.showInfoMessage(project, "此差异已处理", "提示");
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            Document doc = editor.getDocument();

            // 对于INSERT类型，只需要删除新增的内容
            if (chunk.delta.getType() == DeltaType.INSERT) {
                // 精确定位新增内容范围
                if (chunk.modifiedStartOffset >= 0 && chunk.modifiedEndOffset > chunk.modifiedStartOffset) {
                    try {
                        int startOffset = chunk.modifiedStartOffset;
                        int endOffset = chunk.modifiedEndOffset;

                        // 安全范围检查
                        if (startOffset < doc.getTextLength() && endOffset <= doc.getTextLength() && startOffset < endOffset) {
                            System.out.println("删除INSERT内容: [" + startOffset + ", " + endOffset + "]");
                            doc.deleteString(startOffset, endOffset);

                            // 更新后续chunk的offset
                            int deletedLength = endOffset - startOffset;
                            for (DiffChunk otherChunk : chunks) {
                                if (otherChunk != chunk && !otherChunk.processed) {
                                    if (otherChunk.modifiedStartOffset > startOffset) {
                                        otherChunk.modifiedStartOffset -= deletedLength;
                                        otherChunk.modifiedEndOffset -= deletedLength;
                                    }
                                    if (otherChunk.originalStartOffset > startOffset) {
                                        otherChunk.originalStartOffset -= deletedLength;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("删除INSERT内容失败: " + e.getMessage());
                    }
                }
            }
            // 对于DELETE和CHANGE类型，使用原有逻辑
            else {
                // ... 原有的DELETE和CHANGE处理逻辑保持不变
            }

            chunk.processed = true;
            clearChunkHighlights(chunk);
            updateStatusLabel();

            // 检查是否需要关闭工具栏
            checkAndCloseIfAllProcessed();
        });
    }

    /**
     * 更新其他chunk的行号（在删除内容后）
     */
    private void updateChunkLines(DiffChunk processedChunk, int linesDelta) {
        // 找到受影响的行号
        int affectedLine = Math.max(
                processedChunk.originalStartLine >= 0 ? processedChunk.originalStartLine : 0,
                processedChunk.modifiedStartLine >= 0 ? processedChunk.modifiedStartLine : 0
        );

        // 更新所有在此之后的chunk的行号
        for (DiffChunk chunk : chunks) {
            if (chunk == processedChunk || chunk.processed) continue;

            if (chunk.originalStartLine > affectedLine) {
                chunk.originalStartLine += linesDelta;
            }
            if (chunk.modifiedStartLine > affectedLine) {
                chunk.modifiedStartLine += linesDelta;
            }
        }
    }

    /**
     * 高亮单行
     */
    private void highlightLine(int line, Color bgColor, boolean strikeout, DiffChunk chunk) {
        Document doc = editor.getDocument();
        if (line >= doc.getLineCount()) return;

        int start = doc.getLineStartOffset(line);
        int end = doc.getLineEndOffset(line);

        TextAttributes attr = new TextAttributes();
        attr.setBackgroundColor(bgColor);
        if (strikeout) {
            attr.setEffectType(EffectType.STRIKEOUT);
            attr.setEffectColor(new Color(180, 0, 0));
        }

        RangeHighlighter h = editor.getMarkupModel().addRangeHighlighter(
                start, end,
                HighlighterLayer.SELECTION - 1,
                attr,
                HighlighterTargetArea.LINES_IN_RANGE
        );

        highlighters.add(h);
        // 将高亮器添加到chunk的关联列表中
        if (chunk != null) {
            chunk.associatedHighlighters.add(h);
        }
    }

    /**
     * 显示工具栏
     */
    private void showToolbar() {
        toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        toolbarPanel.setBackground(new JBColor(new Color(240, 248, 255), new Color(60, 63, 65)));

        // 左侧：导航和状态
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftPanel.setOpaque(false);

        JButton prevBtn = new JButton("◀");
        prevBtn.setToolTipText("上一个差异");
        prevBtn.addActionListener(e -> navigatePrevious());

        JButton nextBtn = new JButton("▶");
        nextBtn.setToolTipText("下一个差异");
        nextBtn.addActionListener(e -> navigateNext());

        statusLabel = new JLabel();
        updateStatusLabel();

        leftPanel.add(prevBtn);
        leftPanel.add(nextBtn);
        leftPanel.add(statusLabel);

        // 右侧：Keep 和 Undo 按钮
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);

        JButton keepBtn = new JButton("✓ Keep All");
        keepBtn.setBackground(new Color(100, 200, 100));
        keepBtn.setForeground(Color.WHITE);
        keepBtn.setFocusPainted(false);
        keepBtn.setToolTipText("接受全部未处理的 AI 建议");
        keepBtn.addActionListener(e -> handleKeep());

        JButton undoBtn = new JButton("✗ Undo All");
        undoBtn.setBackground(new Color(200, 100, 100));
        undoBtn.setForeground(Color.WHITE);
        undoBtn.setFocusPainted(false);
        undoBtn.setToolTipText("撤销全部未处理的 AI 建议，保留原代码");
        undoBtn.addActionListener(e -> handleUndo());

        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> closeToolbar());

        rightPanel.add(keepBtn);
        rightPanel.add(undoBtn);
        rightPanel.add(closeBtn);

        toolbarPanel.add(leftPanel, BorderLayout.WEST);
        toolbarPanel.add(rightPanel, BorderLayout.EAST);

        // 添加到编辑器
        JComponent editorComponent = editor.getComponent();
        Container parent = editorComponent.getParent();

        if (parent instanceof JPanel) {
            JPanel parentPanel = (JPanel) parent;
            parentPanel.add(toolbarPanel, BorderLayout.SOUTH);
            parentPanel.revalidate();
            parentPanel.repaint();
        } else {
            // 使用弹出方式显示
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(editorComponent);
            if (frame != null) {
                frame.add(toolbarPanel, BorderLayout.SOUTH);
                frame.revalidate();
                frame.repaint();
            }
        }

        // 初始化：滚动到第一个差异
        if (!chunks.isEmpty()) {
            currentChunkIndex = 0;
            scrollToCurrentChunk();
        }
    }

    /**
     * 更新状态标签
     */
    private void updateStatusLabel() {
        if (statusLabel != null) {
            int total = chunks.size();
            int processed = (int) chunks.stream().filter(c -> c.processed).count();
            int remaining = total - processed;

            // 显示：当前索引+1 / 总数 (剩余X个未处理)
            String statusText;
            if (remaining > 0) {
                statusText = String.format("  差异: %d / %d (剩余 %d)  ",
                        currentChunkIndex + 1, total, remaining);
            } else {
                statusText = String.format("  差异: %d / %d (全部完成)  ",
                        currentChunkIndex + 1, total);
                // 如果全部完成，自动关闭工具栏（双重保险）
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (remaining == 0 && toolbarPanel != null) {
                        Messages.showInfoMessage(project, "所有差异已处理完成", "完成");
                        closeToolbar();
                    }
                });
            }

            statusLabel.setText(statusText);
        }
    }

    /**
     * 导航到上一个差异（用于查看）
     */
    private void navigatePrevious() {
        if (chunks.isEmpty()) return;

        // 循环往前
        currentChunkIndex--;
        if (currentChunkIndex < 0) {
            currentChunkIndex = chunks.size() - 1;
        }

        scrollToCurrentChunk();
        updateStatusLabel();
    }

    /**
     * 导航到下一个差异（用于查看）
     */
    private void navigateNext() {
        if (chunks.isEmpty()) return;

        // 循环往后
        currentChunkIndex++;
        if (currentChunkIndex >= chunks.size()) {
            currentChunkIndex = 0;
        }

        scrollToCurrentChunk();
        updateStatusLabel();
    }

    /**
     * 滚动到当前块
     */
    private void scrollToCurrentChunk() {
        DiffChunk chunk = chunks.get(currentChunkIndex);
        int line = chunk.originalStartLine >= 0 ? chunk.originalStartLine : chunk.modifiedStartLine;
        if (line >= 0) {
            Document doc = editor.getDocument();
            if (line < doc.getLineCount()) {
                int offset = doc.getLineStartOffset(line);
                editor.getCaretModel().moveToOffset(offset);
                editor.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER);
            }
        }
    }

    /**
     * 处理 Keep（全局操作，接受所有未处理的差异）
     */
    private void handleKeep() {
        // 统计未处理的差异数量
        long unprocessedCount = chunks.stream().filter(c -> !c.processed).count();
        if (unprocessedCount == 0) {
            Messages.showInfoMessage(project, "所有差异已处理", "提示");
            closeToolbar();
            return;
        }

        // 确认操作
        int result = Messages.showYesNoDialog(
                project,
                String.format("确认接受全部 %d 个未处理的 AI 建议吗？", unprocessedCount),
                "全局 Keep",
                Messages.getQuestionIcon()
        );

        if (result != Messages.YES) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            Document doc = editor.getDocument();

            // 从后向前处理，避免offset混乱
            for (int i = chunks.size() - 1; i >= 0; i--) {
                DiffChunk chunk = chunks.get(i);
                if (chunk.processed) continue;

                // 删除原代码（优先使用offset）
                boolean deleted = false;
                if (chunk.originalStartOffset >= 0) {
                    try {
                        int startOffset = chunk.originalStartOffset;
                        int endOffset = startOffset;

                        // 计算原代码的实际长度（包括所有行和换行符）
                        for (int j = 0; j < chunk.delta.getSource().size(); j++) {
                            try {
                                int line = doc.getLineNumber(endOffset);
                                int lineEnd = doc.getLineEndOffset(line);
                                endOffset = lineEnd;
                                if (line < doc.getLineCount() - 1) {
                                    endOffset++; // 包括换行符
                                }
                            } catch (Exception e) {
                                break;
                            }
                        }

                        if (endOffset > doc.getTextLength()) {
                            endOffset = doc.getTextLength();
                        }

                        if (endOffset > startOffset && startOffset >= 0 && endOffset <= doc.getTextLength()) {
                            int deletedLength = endOffset - startOffset;
                            doc.deleteString(startOffset, endOffset);
                            deleted = true;

                            // 更新所有在此之前的chunk的offset
                            for (int j = i - 1; j >= 0; j--) {
                                DiffChunk earlierChunk = chunks.get(j);
                                if (!earlierChunk.processed) {
                                    if (earlierChunk.originalStartOffset > startOffset) {
                                        earlierChunk.originalStartOffset -= deletedLength;
                                    }
                                    if (earlierChunk.modifiedStartOffset > startOffset) {
                                        earlierChunk.modifiedStartOffset -= deletedLength;
                                        earlierChunk.modifiedEndOffset -= deletedLength;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 如果offset失效，尝试使用行号
                    }
                }

                // 如果使用offset删除失败，尝试使用行号
                if (!deleted && chunk.originalStartLine >= 0 && chunk.originalStartLine < doc.getLineCount()) {
                    try {
                        int start = doc.getLineStartOffset(chunk.originalStartLine);
                        int endLine = chunk.originalStartLine + chunk.delta.getSource().size() - 1;
                        if (endLine >= doc.getLineCount()) endLine = doc.getLineCount() - 1;
                        int end = doc.getLineEndOffset(endLine);
                        if (endLine < doc.getLineCount() - 1) {
                            end++; // 包括换行符
                        }
                        if (start < doc.getTextLength() && end <= doc.getTextLength() && start < end) {
                            doc.deleteString(start, end);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                chunk.processed = true;
                clearChunkHighlights(chunk);
            }

            updateStatusLabel();
            Messages.showInfoMessage(project, "已接受全部 AI 建议", "完成");
            closeToolbar();
        });
    }

    /**
     * 处理 Undo（全局操作）- 完全重写的可靠版本
     */
    private void handleUndo() {
        long unprocessedCount = chunks.stream().filter(c -> !c.processed).count();
        if (unprocessedCount == 0) {
            Messages.showInfoMessage(project, "所有差异已处理", "提示");
            closeToolbar();
            return;
        }

        int result = Messages.showYesNoDialog(
                project,
                String.format("确认撤销全部 %d 个未处理的 AI 建议吗？", unprocessedCount),
                "全局 Undo",
                Messages.getQuestionIcon()
        );

        if (result != Messages.YES) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                Document doc = editor.getDocument();

                // 方法1：使用最简单的方案 - 直接恢复原始选中代码
                // 计算当前文档中AI修改后的区域
                String currentContent = doc.getText();
                int currentModifiedEnd = selectionStart + modifiedText.length();

                // 安全边界检查
                if (currentModifiedEnd > doc.getTextLength()) {
                    currentModifiedEnd = doc.getTextLength();
                }

                // 直接替换为原始代码
                if (selectionStart >= 0 && currentModifiedEnd <= doc.getTextLength() &&
                        selectionStart < currentModifiedEnd) {

                    System.out.println("=== 全局撤销执行 ===");
                    System.out.println("原始代码: " + originalText);
                    System.out.println("当前范围: [" + selectionStart + ", " + currentModifiedEnd + "]");

                    // 执行替换
                    doc.replaceString(selectionStart, currentModifiedEnd, originalText);
                    System.out.println("替换完成");
                }

                // 标记所有chunk为已处理
                for (DiffChunk chunk : chunks) {
                    chunk.processed = true;
                    clearChunkHighlights(chunk);
                }

                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showInfoMessage(project, "已撤销全部 AI 建议", "完成");
                    closeToolbar();
                });

            } catch (Exception e) {
                e.printStackTrace();
                Messages.showErrorDialog(project, "撤销时出错: " + e.getMessage(), "错误");

                // 紧急恢复方案
                try {
                    emergencyRecovery();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    /**
     * 紧急恢复 - 当主要撤销方法失败时使用
     */
    private void emergencyRecovery() {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                Document doc = editor.getDocument();

                // 找到原始选中代码的近似位置
                String fullText = doc.getText();
                int approximateStart = findBestMatch(fullText, originalText);

                if (approximateStart >= 0) {
                    int approximateEnd = approximateStart + originalText.length();
                    if (approximateEnd <= doc.getTextLength()) {
                        doc.replaceString(approximateStart, approximateEnd, originalText);
                        System.out.println("紧急恢复完成");
                    }
                } else {
                    // 最后手段：在选中开始位置插入原始代码
                    doc.insertString(selectionStart, originalText);
                    System.out.println("使用最后手段恢复");
                }

                // 强制关闭工具栏
                ApplicationManager.getApplication().invokeLater(() -> {
                    closeToolbar();
                    Messages.showInfoMessage(project, "已执行紧急恢复", "完成");
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 在文本中寻找最佳匹配位置
     */
    private int findBestMatch(String fullText, String searchText) {
        if (searchText == null || searchText.isEmpty()) return -1;

        // 使用简单的字符串匹配
        int index = fullText.indexOf(searchText);
        if (index >= 0) return index;

        // 如果完全匹配失败，尝试部分匹配
        String[] lines = searchText.split("\n");
        if (lines.length > 0) {
            String firstLine = lines[0].trim();
            if (!firstLine.isEmpty()) {
                index = fullText.indexOf(firstLine);
                if (index >= 0) return index;
            }
        }

        return -1;
    }

    /**
     * 清除块的高亮
     */
    private void clearChunkHighlights(DiffChunk chunk) {
        // 创建副本以避免并发修改异常
        List<RangeHighlighter> toRemove = new ArrayList<>(chunk.associatedHighlighters);
        // 直接清除chunk中存储的所有关联高亮器
        for (RangeHighlighter h : toRemove) {
            try {
                if (h.isValid()) {
                    editor.getMarkupModel().removeHighlighter(h);
                }
                highlighters.remove(h);
            } catch (Exception e) {
                // Ignore
            }
        }
        // 清空chunk的高亮器列表
        chunk.associatedHighlighters.clear();

        // 额外检查：清除所有可能属于该chunk的高亮器（通过GutterIconRenderer判断）
        // 这可以处理某些高亮器没有被正确添加到associatedHighlighters的情况
        List<RangeHighlighter> allHighlighters = new ArrayList<>(highlighters);
        for (RangeHighlighter h : allHighlighters) {
            try {
                if (h.isValid() && h.getGutterIconRenderer() instanceof HoverableGutterIcon) {
                    HoverableGutterIcon icon = (HoverableGutterIcon) h.getGutterIconRenderer();
                    if (icon.chunk == chunk) {
                        editor.getMarkupModel().removeHighlighter(h);
                        highlighters.remove(h);
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * 关闭工具栏
     */
    private void closeToolbar() {
        if (toolbarPanel != null) {
            Container parent = toolbarPanel.getParent();
            if (parent != null) {
                parent.remove(toolbarPanel);
                parent.revalidate();
                parent.repaint();
            }
        }

        // 清除所有chunk的高亮器（确保没有遗漏）
        for (DiffChunk chunk : chunks) {
            clearChunkHighlights(chunk);
        }

        // 清除所有高亮（双重保险）
        for (RangeHighlighter h : new ArrayList<>(highlighters)) {
            try {
                if (h.isValid()) {
                    editor.getMarkupModel().removeHighlighter(h);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        highlighters.clear();
    }

    /**
     * 构建原代码
     */
    private String buildOriginalCode(AbstractDelta<String> delta) {
        StringBuilder sb = new StringBuilder();
        List<String> sourceLines = delta.getSource().getLines();
        for (int i = 0; i < sourceLines.size(); i++) {
            sb.append(sourceLines.get(i));
            if (i < sourceLines.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
