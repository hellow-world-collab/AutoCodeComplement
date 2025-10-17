package com.system.demo.LLM;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 设置界面
 */
public class LLMSettingsConfigurable implements Configurable {
    private JTextField apiUrlField;
    private JTextField apiKeyField;
    private JTextField modelField;
    private JTextField triggerDelayField;
    private JTextField maxLengthField;
    private JTextField maxContextLengthField;
    private JCheckBox enableCacheCheckBox;
    private JTextField cacheTimeoutField;
    private JPanel mainPanel;

    @Nls
    @Override
    public String getDisplayName() {
        return "AI Code Completion Settings";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);

        LLMSettings settings = LLMSettings.getInstance();

        // API URL
        mainPanel.add(new JLabel("API URL:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        apiUrlField = new JTextField(settings.apiUrl, 40);
        mainPanel.add(apiUrlField, gbc);

        // API Key
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        apiKeyField = new JTextField(settings.apiKey, 40);
        mainPanel.add(apiKeyField, gbc);

        // Model
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        modelField = new JTextField(settings.model, 40);
        mainPanel.add(modelField, gbc);

        // Trigger Delay
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("触发延迟 (ms):"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        triggerDelayField = new JTextField(String.valueOf(settings.triggerDelayMs), 40);
        mainPanel.add(triggerDelayField, gbc);

        // Max Length
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("最大建议长度:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        maxLengthField = new JTextField(String.valueOf(settings.maxSuggestionLength), 40);
        mainPanel.add(maxLengthField, gbc);

        // Max Context Length
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("最大上下文长度:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        maxContextLengthField = new JTextField(String.valueOf(settings.maxContextLength), 40);
        mainPanel.add(maxContextLengthField, gbc);

        // Enable Cache
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("启用缓存:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        enableCacheCheckBox = new JCheckBox("启用响应缓存", settings.enableCache);
        mainPanel.add(enableCacheCheckBox, gbc);

        // Cache Timeout
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("缓存超时 (分钟):"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        cacheTimeoutField = new JTextField(String.valueOf(settings.cacheTimeoutMinutes), 40);
        mainPanel.add(cacheTimeoutField, gbc);

        // 说明
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("<html><i>提示：修改设置后需要重启 IDE 才能生效</i></html>");
        mainPanel.add(infoLabel, gbc);

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        LLMSettings settings = LLMSettings.getInstance();
        return !apiUrlField.getText().equals(settings.apiUrl) ||
                !apiKeyField.getText().equals(settings.apiKey) ||
                !modelField.getText().equals(settings.model) ||
                !triggerDelayField.getText().equals(String.valueOf(settings.triggerDelayMs)) ||
                !maxLengthField.getText().equals(String.valueOf(settings.maxSuggestionLength)) ||
                !maxContextLengthField.getText().equals(String.valueOf(settings.maxContextLength)) ||
                enableCacheCheckBox.isSelected() != settings.enableCache ||
                !cacheTimeoutField.getText().equals(String.valueOf(settings.cacheTimeoutMinutes));
    }

    @Override
    public void apply() throws ConfigurationException {
        LLMSettings settings = LLMSettings.getInstance();
        settings.apiUrl = apiUrlField.getText();
        settings.apiKey = apiKeyField.getText();
        settings.model = modelField.getText();
        
        try {
            settings.triggerDelayMs = Integer.parseInt(triggerDelayField.getText());
            settings.maxSuggestionLength = Integer.parseInt(maxLengthField.getText());
            settings.maxContextLength = Integer.parseInt(maxContextLengthField.getText());
            settings.enableCache = enableCacheCheckBox.isSelected();
            settings.cacheTimeoutMinutes = Integer.parseInt(cacheTimeoutField.getText());
        } catch (NumberFormatException e) {
            throw new ConfigurationException("请输入有效的数字");
        }
    }

    @Override
    public void reset() {
        LLMSettings settings = LLMSettings.getInstance();
        apiUrlField.setText(settings.apiUrl);
        apiKeyField.setText(settings.apiKey);
        modelField.setText(settings.model);
        triggerDelayField.setText(String.valueOf(settings.triggerDelayMs));
        maxLengthField.setText(String.valueOf(settings.maxSuggestionLength));
        maxContextLengthField.setText(String.valueOf(settings.maxContextLength));
        enableCacheCheckBox.setSelected(settings.enableCache);
        cacheTimeoutField.setText(String.valueOf(settings.cacheTimeoutMinutes));
    }
}
