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
import java.util.*;
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
        boolean processed = false;
        
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
     * 渲染差异
     */
    private void renderDifferences(Patch<String> patch) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            Document doc = editor.getDocument();
            int baseLineNumber = doc.getLineNumber(selectionStart);
            
            // 替换为修改后的代码
            doc.replaceString(selectionStart, selectionEnd, modifiedText);
            
            // 处理每个 delta
            List<AbstractDelta<String>> deltas = new ArrayList<>(patch.getDeltas());
            for (int i = deltas.size() - 1; i >= 0; i--) {
                AbstractDelta<String> delta = deltas.get(i);
                int deltaLine = baseLineNumber + delta.getSource().getPosition();
                
                DiffChunk chunk = new DiffChunk(delta);
                chunks.add(0, chunk);
                
                if (delta.getType() == DeltaType.DELETE || delta.getType() == DeltaType.CHANGE) {
                    String originalCode = buildOriginalCode(delta);
                    insertOriginalCode(deltaLine, originalCode, chunk);
                }
                
                if (delta.getType() == DeltaType.INSERT || delta.getType() == DeltaType.CHANGE) {
                    chunk.modifiedStartLine = deltaLine + (delta.getType() == DeltaType.CHANGE ? delta.getSource().size() : 0);
                }
            }
        });
        
        ApplicationManager.getApplication().invokeLater(this::applyHighlights);
    }
    
    /**
     * 插入原代码
     */
    private void insertOriginalCode(int beforeLine, String originalCode, DiffChunk chunk) {
        Document doc = editor.getDocument();
        if (beforeLine >= doc.getLineCount()) return;
        
        int insertOffset = doc.getLineStartOffset(beforeLine);
        doc.insertString(insertOffset, originalCode);
        chunk.originalStartLine = beforeLine;
    }
    
    /**
     * 应用高亮
     */
    private void applyHighlights() {
        for (DiffChunk chunk : chunks) {
            Document doc = editor.getDocument();
            
            // 高亮原代码（红色）
            if (chunk.originalStartLine >= 0 && chunk.originalStartLine < doc.getLineCount()) {
                for (int i = 0; i < chunk.delta.getSource().size(); i++) {
                    int line = chunk.originalStartLine + i;
                    if (line < doc.getLineCount()) {
                        highlightLine(line, COLOR_DELETED, true);
                    }
                }
            }
            
            // 高亮新代码（绿色）
            if (chunk.modifiedStartLine >= 0 && chunk.modifiedStartLine < doc.getLineCount()) {
                for (int i = 0; i < chunk.delta.getTarget().size(); i++) {
                    int line = chunk.modifiedStartLine + i;
                    if (line < doc.getLineCount()) {
                        highlightLine(line, COLOR_ADDED, false);
                    }
                }
                // 添加悬浮工具栏（鼠标悬停时显示Keep/Undo按钮）
                addHoverableToolbar(chunk);
            }
        }
    }
    
    /**
     * 添加可悬浮的工具栏（在差异块上显示）
     */
    private void addHoverableToolbar(DiffChunk chunk) {
        Document doc = editor.getDocument();
        
        // 在差异块的第一行添加Gutter图标
        int line = chunk.modifiedStartLine;
        if (line < 0 || line >= doc.getLineCount()) return;
        
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
     * 处理局部 Keep（只针对指定chunk，不影响全局导航）
     */
    private void handleLocalKeep(DiffChunk chunk) {
        if (chunk.processed) {
            Messages.showInfoMessage(project, "此差异已处理", "提示");
            return;
        }
        
        WriteCommandAction.runWriteCommandAction(project, () -> {
            Document doc = editor.getDocument();
            
            // 删除原代码显示
            if (chunk.originalStartLine >= 0 && chunk.originalStartLine < doc.getLineCount()) {
                int start = doc.getLineStartOffset(chunk.originalStartLine);
                int end = chunk.originalStartLine + chunk.delta.getSource().size() - 1;
                if (end >= doc.getLineCount()) end = doc.getLineCount() - 1;
                int endOffset = doc.getLineEndOffset(end) + 1;
                doc.deleteString(start, Math.min(endOffset, doc.getTextLength()));
                
                // 更新其他chunk的行号
                updateChunkLines(chunk, -chunk.delta.getSource().size());
            }
            
            chunk.processed = true;
            clearChunkHighlights(chunk);
            updateStatusLabel();
            
            Messages.showInfoMessage(project, "已接受该建议", "局部操作");
        });
    }
    
    /**
     * 处理局部 Undo（只针对指定chunk，不影响全局导航）
     */
    private void handleLocalUndo(DiffChunk chunk) {
        if (chunk.processed) {
            Messages.showInfoMessage(project, "此差异已处理", "提示");
            return;
        }
        
        WriteCommandAction.runWriteCommandAction(project, () -> {
            Document doc = editor.getDocument();
            
            // 删除新代码
            if (chunk.modifiedStartLine >= 0 && chunk.modifiedStartLine < doc.getLineCount()) {
                int start = doc.getLineStartOffset(chunk.modifiedStartLine);
                int end = chunk.modifiedStartLine + chunk.delta.getTarget().size() - 1;
                if (end >= doc.getLineCount()) end = doc.getLineCount() - 1;
                int endOffset = doc.getLineEndOffset(end) + 1;
                doc.deleteString(start, Math.min(endOffset, doc.getTextLength()));
                
                // 更新其他chunk的行号
                updateChunkLines(chunk, -chunk.delta.getTarget().size());
            }
            
            chunk.processed = true;
            clearChunkHighlights(chunk);
            updateStatusLabel();
            
            Messages.showInfoMessage(project, "已保留原代码", "局部操作");
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
    private void highlightLine(int line, Color bgColor, boolean strikeout) {
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
            // 从后向前处理，避免行号混乱
            for (int i = chunks.size() - 1; i >= 0; i--) {
                DiffChunk chunk = chunks.get(i);
                if (chunk.processed) continue;
                
                Document doc = editor.getDocument();
                
                // 删除原代码显示
                if (chunk.originalStartLine >= 0 && chunk.originalStartLine < doc.getLineCount()) {
                    int start = doc.getLineStartOffset(chunk.originalStartLine);
                    int end = chunk.originalStartLine + chunk.delta.getSource().size() - 1;
                    if (end >= doc.getLineCount()) end = doc.getLineCount() - 1;
                    int endOffset = doc.getLineEndOffset(end) + 1;
                    doc.deleteString(start, Math.min(endOffset, doc.getTextLength()));
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
     * 处理 Undo（全局操作，撤销所有未处理的差异，保留原代码）
     */
    private void handleUndo() {
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
            String.format("确认撤销全部 %d 个未处理的 AI 建议，保留原代码吗？", unprocessedCount),
            "全局 Undo",
            Messages.getQuestionIcon()
        );
        
        if (result != Messages.YES) {
            return;
        }
        
        WriteCommandAction.runWriteCommandAction(project, () -> {
            // 从后向前处理，避免行号混乱
            for (int i = chunks.size() - 1; i >= 0; i--) {
                DiffChunk chunk = chunks.get(i);
                if (chunk.processed) continue;
                
                Document doc = editor.getDocument();
                
                // 删除新代码
                if (chunk.modifiedStartLine >= 0 && chunk.modifiedStartLine < doc.getLineCount()) {
                    int start = doc.getLineStartOffset(chunk.modifiedStartLine);
                    int end = chunk.modifiedStartLine + chunk.delta.getTarget().size() - 1;
                    if (end >= doc.getLineCount()) end = doc.getLineCount() - 1;
                    int endOffset = doc.getLineEndOffset(end) + 1;
                    doc.deleteString(start, Math.min(endOffset, doc.getTextLength()));
                }
                
                chunk.processed = true;
                clearChunkHighlights(chunk);
            }
            
            updateStatusLabel();
            Messages.showInfoMessage(project, "已撤销全部 AI 建议，保留原代码", "完成");
            closeToolbar();
        });
    }
    
    /**
     * 清除块的高亮
     */
    private void clearChunkHighlights(DiffChunk chunk) {
        highlighters.removeIf(h -> {
            try {
                Document doc = editor.getDocument();
                int line = doc.getLineNumber(h.getStartOffset());
                boolean shouldRemove = (chunk.originalStartLine >= 0 && 
                    line >= chunk.originalStartLine && 
                    line < chunk.originalStartLine + chunk.delta.getSource().size()) ||
                    (chunk.modifiedStartLine >= 0 && 
                    line >= chunk.modifiedStartLine && 
                    line < chunk.modifiedStartLine + chunk.delta.getTarget().size());
                
                if (shouldRemove) {
                    editor.getMarkupModel().removeHighlighter(h);
                    return true;
                }
            } catch (Exception e) {
                // Ignore
            }
            return false;
        });
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
        
        // 清除所有高亮
        for (RangeHighlighter h : highlighters) {
            editor.getMarkupModel().removeHighlighter(h);
        }
        highlighters.clear();
    }
    
    /**
     * 构建原代码
     */
    private String buildOriginalCode(AbstractDelta<String> delta) {
        StringBuilder sb = new StringBuilder();
        for (String line : delta.getSource().getLines()) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}

