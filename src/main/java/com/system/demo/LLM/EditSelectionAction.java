package com.system.demo.LLM;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * 使用 LLM 改进选中的代码
 * 会显示差异预览并让用户确认是否应用
 */
public class EditSelectionAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        
        if (editor == null || project == null || psiFile == null) {
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        
        if (selectedText == null || selectedText.isEmpty()) {
            showNotification(project, "请先选中要改进的代码", NotificationType.WARNING);
            return;
        }

        // 在后台任务中调用 LLM
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在使用 LLM 改进代码...", true) {
            private String modifiedCode;
            
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                
                try {
                    // 获取完整文件内容作为上下文
                    Document document = editor.getDocument();
                    String fullFileText = document.getText();
                    String fileName = psiFile.getName();
                    
                    // 获取选中文本在文件中的位置
                    int selectionStart = selectionModel.getSelectionStart();
                    int selectionEnd = selectionModel.getSelectionEnd();
                    String beforeSelection = fullFileText.substring(0, selectionStart);
                    String afterSelection = fullFileText.substring(selectionEnd);
                    
                    // 构建包含完整上下文的提示
                    String prompt = buildEditPrompt(fileName, beforeSelection, selectedText, afterSelection);
                    
                    // 调用 LLM
                    modifiedCode = LLMClient.queryLLM(prompt);
                    
                    if (modifiedCode != null && !modifiedCode.isEmpty()) {
                        // 清理返回的代码
                        modifiedCode = cleanCode(modifiedCode);
                    }
                    
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ApplicationManager.getApplication().invokeLater(() -> {
                        showNotification(project, "LLM 调用失败: " + ex.getMessage(), NotificationType.ERROR);
                    });
                    return;
                }
                
                // 在 UI 线程中显示差异预览
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (modifiedCode == null || modifiedCode.isEmpty()) {
                        showNotification(project, "LLM 未返回有效的修改建议", NotificationType.WARNING);
                        return;
                    }
                    
                    // 显示差异预览对话框
                    FileType fileType = psiFile.getFileType();
                    DiffPreviewDialog dialog = new DiffPreviewDialog(
                        project, 
                        selectedText, 
                        modifiedCode,
                        fileType
                    );
                    
                    // 如果用户点击确认，应用修改
                    if (dialog.showAndGet()) {
                        applyModification(project, editor, selectionModel, modifiedCode);
                        showNotification(project, "代码已成功更新", NotificationType.INFORMATION);
                    }
                });
            }
        });
    }

    /**
     * 构建代码编辑的提示词，包含完整文件上下文
     */
    private String buildEditPrompt(String fileName, String beforeSelection, 
                                   String selectedCode, String afterSelection) {
        return String.format(
            "你是一个专业的 Python 代码优化助手。请改进下面选中的代码片段。\n\n" +
            "文件名：%s\n\n" +
            "选中代码之前的上下文：\n```python\n%s\n```\n\n" +
            "选中的需要改进的代码：\n```python\n%s\n```\n\n" +
            "选中代码之后的上下文：\n```python\n%s\n```\n\n" +
            "请基于完整的文件上下文，改进选中的代码。要求：\n" +
            "1. 保持代码的原有功能和语义\n" +
            "2. 优化代码结构和可读性\n" +
            "3. 遵循 Python 最佳实践和 PEP 8 规范\n" +
            "4. 如果有明显的 bug 或性能问题，请修复\n" +
            "5. 只返回改进后的代码，不要包含任何解释或 markdown 标记\n\n" +
            "改进后的代码：",
            fileName,
            beforeSelection.length() > 500 ? "..." + beforeSelection.substring(beforeSelection.length() - 500) : beforeSelection,
            selectedCode,
            afterSelection.length() > 500 ? afterSelection.substring(0, 500) + "..." : afterSelection
        );
    }

    /**
     * 清理 LLM 返回的代码
     */
    private String cleanCode(String code) {
        // 移除可能的 markdown 代码块标记
        code = code.replaceAll("^```[a-z]*\\n", "");
        code = code.replaceAll("\\n```$", "");
        code = code.trim();
        return code;
    }

    /**
     * 应用代码修改
     */
    private void applyModification(Project project, Editor editor, 
                                  SelectionModel selectionModel, String newCode) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            editor.getDocument().replaceString(
                selectionModel.getSelectionStart(),
                selectionModel.getSelectionEnd(),
                newCode
            );
        });
    }

    /**
     * 显示通知
     */
    private void showNotification(Project project, String message, NotificationType type) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LLM Completion")
            .createNotification(message, type)
            .notify(project);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只在有选中文本时启用此操作
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean hasSelection = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabledAndVisible(hasSelection);
    }
}

