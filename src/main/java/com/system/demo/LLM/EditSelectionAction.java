package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.system.demo.utils.EditorContextUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * 选中代码发送到 LLM → 弹窗显示差异（模态对话框） → 用户确认应用
 */
public class EditSelectionAction extends AnAction {
    private static String lastSuggestion;
    private static String lastOriginal;
    private static int lastSelectionStart;
    private static int lastSelectionEnd;
    private static Editor lastEditor;
    private static boolean editorWasReadOnly = false;
    private static boolean diffDialogOpen = false;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null || project == null) return;

        String selected = EditorContextUtils.getSelectedText(editor);
        if (selected == null || selected.isEmpty()) {
            Messages.showWarningDialog(project, "请先选中代码。", "提示");
            return;
        }

        // 保存选中位置
        SelectionModel selectionModel = editor.getSelectionModel();
        lastSelectionStart = selectionModel.getSelectionStart();
        lastSelectionEnd = selectionModel.getSelectionEnd();
        lastEditor = editor;

        // 显示进度提示
        Messages.showInfoMessage(project, "正在分析代码，请稍候...", "AI 分析");

        // 在后台线程调用 LLM
        final String selectedText = selected;
        new Thread(() -> {

            // 获取 actionId 来区分不同功能
            String actionId = ActionManager.getInstance().getId(this);

            String prompt;
            if ("CommentSelectionWithAI".equalsIgnoreCase(actionId)) {
                // Shift + Alt + 3 → 给代码加注释
                prompt = EditorContextUtils.buildContextPromptForComment(file, selectedText);
            } else {
                // 默认：Shift + Alt + 1 → 改进代码
                prompt = EditorContextUtils.buildContextPrompt(file, selectedText);
            }

            String context = EditorContextUtils.getFullFileText(file) + "\n// Selected:\n" + selectedText;
            String suggestion = LLMClient.queryLLM(prompt, context);

            if (suggestion == null || suggestion.isEmpty()) {
                Messages.showWarningDialog(project, "模型未返回结果。", "提示");
                return;
            }

            // 清理建议内容
            String cleanedSuggestion = cleanSuggestion(suggestion);

            lastOriginal = selectedText;
            lastSuggestion = cleanedSuggestion;

            // 在 UI 线程显示模态对话框
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                showDiffDialog(project, selectedText, cleanedSuggestion);
            });
        }).start();
    }

    /**
     * 清理 LLM 返回的建议内容
     */
    private String cleanSuggestion(String suggestion) {
        // 去除 markdown 代码块标记
        suggestion = suggestion.replaceAll("```[a-zA-Z]*\\n?", "");
        suggestion = suggestion.replaceAll("```", "");

        // 去除前后空白
        suggestion = suggestion.trim();

        return suggestion;
    }

    /**
     * 显示差异对比对话框（模态对话框，阻止编辑）
     */
    private void showDiffDialog(Project project, String original, String modified) {
        // 保存编辑器原始只读状态
        if (lastEditor != null) {
            editorWasReadOnly = !lastEditor.getDocument().isWritable();

            // 设置编辑器为只读模式
            lastEditor.getDocument().setReadOnly(true);
            diffDialogOpen = true;
        }

        new DiffDialog(project, original, modified).show();
    }

    /**
     * 自定义模态对话框（阻塞主窗口）
     */
    private static class DiffDialog extends DialogWrapper {
        private final Project project;
        private final String original;
        private final String modified;

        protected DiffDialog(Project project, String original, String modified) {
            super(true); // true = 模态对话框
            this.project = project;
            this.original = original;
            this.modified = modified;
            setTitle("AI 修改建议对比 - 模态窗口");
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            // 创建一个水平分割面板：左侧原代码，右侧 AI 建议
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            splitPane.setResizeWeight(0.5);

            JTextArea leftArea = new JTextArea(original);
            leftArea.setEditable(false);
            leftArea.setFont(new Font("Monospaced", Font.PLAIN, 13));

            JTextArea rightArea = new JTextArea(modified);
            rightArea.setEditable(false);
            rightArea.setFont(new Font("Monospaced", Font.PLAIN, 13));

            splitPane.setLeftComponent(new JScrollPane(leftArea));
            splitPane.setRightComponent(new JScrollPane(rightArea));

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(splitPane, BorderLayout.CENTER);

            JLabel tip = new JLabel("← 原代码 | AI 修改建议 →");
            tip.setHorizontalAlignment(SwingConstants.CENTER);
            tip.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
            panel.add(tip, BorderLayout.NORTH);

            return panel;
        }

        @Override
        protected Action @NotNull [] createActions() {
            // 自定义按钮
            return new Action[]{
                    new ApplyAction(),
                    new CancelAction()
            };
        }

        private class ApplyAction extends DialogWrapperAction {
            protected ApplyAction() {
                super("应用修改");
            }

            @Override
            protected void doAction(ActionEvent e) {
                applyChanges();
                close(OK_EXIT_CODE);
            }
        }

        private class CancelAction extends DialogWrapperAction {
            protected CancelAction() {
                super("取消");
            }

            @Override
            protected void doAction(ActionEvent e) {
                restoreEditorEditability();
                close(CANCEL_EXIT_CODE);
            }
        }

        private void applyChanges() {
            if (lastEditor != null && lastSuggestion != null) {
                restoreEditorEditability();
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    lastEditor.getDocument().replaceString(lastSelectionStart, lastSelectionEnd, lastSuggestion);
                });
                Messages.showInfoMessage(project, "已成功应用 AI 修改建议！", "完成");
            }
        }

        @Override
        protected void dispose() {
            super.dispose();
            restoreEditorEditability();
        }
    }

    /**
     * 恢复编辑器的可编辑状态
     */
    private static void restoreEditorEditability() {
        if (lastEditor != null && diffDialogOpen) {
            // 只有当编辑器原本不是只读时才恢复为可编辑
            if (!editorWasReadOnly) {
                lastEditor.getDocument().setReadOnly(false);
            }
            diffDialogOpen = false;
        }
    }

    /**
     * 快捷键确认替换或弹窗确认应用
     */
    public static void applyEdit(Project project) {
        if (lastEditor == null || lastSuggestion == null) {
            if (project != null) {
                Messages.showWarningDialog(project, "没有可应用的 AI 修改建议。\n请先使用 Shift+Alt+1 分析代码。", "提示");
            }
            return;
        }

        Document doc = lastEditor.getDocument();

        // 应用修改前先恢复编辑权限
        restoreEditorEditability();

        WriteCommandAction.runWriteCommandAction(project, () -> {
            doc.replaceString(lastSelectionStart, lastSelectionEnd, lastSuggestion);
        });

        // 清空状态
        lastSuggestion = null;
        lastOriginal = null;
        lastEditor = null;
        editorWasReadOnly = false;

        Messages.showInfoMessage(project, "已成功应用 AI 修改建议！", "完成");
    }

    /**
     * 绑定 Shift+Alt+2 快捷键应用修改
     */
    public static class ApplyEditAction extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            applyEdit(project);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            // 只在有待应用的修改时启用此操作
            e.getPresentation().setEnabled(lastSuggestion != null && lastEditor != null);
//            e.getPresentation().setEnabled(true);
        }
    }
}
