package com.system.demo.LLM;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 显示原始代码和 LLM 修改后代码的差异预览对话框
 */
public class DiffPreviewDialog extends DialogWrapper {
    private final Project project;
    private final String originalText;
    private final String modifiedText;
    private final FileType fileType;
    private JPanel diffPanel;

    public DiffPreviewDialog(@NotNull Project project, 
                            @NotNull String originalText, 
                            @NotNull String modifiedText,
                            @NotNull FileType fileType) {
        super(project);
        this.project = project;
        this.originalText = originalText;
        this.modifiedText = modifiedText;
        this.fileType = fileType;
        
        setTitle("LLM 代码修改预览");
        setOKButtonText("应用修改");
        setCancelButtonText("取消");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        diffPanel = new JPanel(new BorderLayout());
        diffPanel.setPreferredSize(new Dimension(800, 600));
        
        // 添加说明标签
        JBLabel label = new JBLabel("预览 LLM 建议的修改（绿色为新增，红色为删除）：");
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        diffPanel.add(label, BorderLayout.NORTH);
        
        // 创建差异视图
        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        DocumentContent content1 = contentFactory.create(project, originalText, fileType);
        DocumentContent content2 = contentFactory.create(project, modifiedText, fileType);
        
        SimpleDiffRequest request = new SimpleDiffRequest(
            "代码修改对比",
            content1,
            content2,
            "原始代码",
            "LLM 修改后"
        );
        
        // 创建差异面板
        JComponent diffComponent = DiffManager.getInstance().createRequestPanel(
            project, 
            getDisposable(), 
            null
        ).getComponent();
        
        diffPanel.add(diffComponent, BorderLayout.CENTER);
        
        // 显示差异请求
        DiffManager.getInstance().showDiff(project, request);
        
        return diffPanel;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 添加提示信息
        JBLabel hintLabel = new JBLabel("提示：点击「应用修改」将用 LLM 的建议替换选中的代码");
        hintLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        panel.add(hintLabel, BorderLayout.NORTH);
        
        // 添加按钮
        panel.add(super.createSouthPanel(), BorderLayout.SOUTH);
        
        return panel;
    }
}
