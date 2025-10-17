package com.system.demo.LLM;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.TypedAction;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 插件组件，用于初始化 Tab 键处理器和输入监听
 */
public class LLMPluginComponent implements ProjectComponent {
    private final Project project;
    private EditorActionHandler originalTabHandler;
    private TypedActionHandler originalTypedHandler;
    private LLMDocumentListener documentListener;

    public LLMPluginComponent(Project project) {
        this.project = project;
    }

    @Override
    public void projectOpened() {
        // 注册 Tab 键处理器
        EditorActionManager actionManager = EditorActionManager.getInstance();
        originalTabHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_TAB);
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, new TabAcceptHandler(originalTabHandler));

        // 注册输入监听器
        TypedAction typedAction = TypedAction.getInstance();
        originalTypedHandler = typedAction.getHandler();
        typedAction.setupHandler(new LLMTypedActionHandler(originalTypedHandler));

        // 注册文档监听器
        documentListener = new LLMDocumentListener();
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(documentListener, project);
    }

    @Override
    public void projectClosed() {
        // 恢复原始处理器
        EditorActionManager actionManager = EditorActionManager.getInstance();
        if (originalTabHandler != null) {
            actionManager.setActionHandler(IdeActions.ACTION_EDITOR_TAB, originalTabHandler);
        }

        TypedAction typedAction = TypedAction.getInstance();
        if (originalTypedHandler != null) {
            typedAction.setupHandler(originalTypedHandler);
        }

        // 移除文档监听器
        if (documentListener != null) {
            EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(documentListener);
        }
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "LLMPluginComponent";
    }
}
