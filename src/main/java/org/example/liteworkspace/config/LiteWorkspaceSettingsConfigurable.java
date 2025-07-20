package org.example.liteworkspace.config;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LiteWorkspaceSettingsConfigurable implements Configurable {

    private JTextField apiKeyField;
    private JTextField apiUrlField;
    private JPanel mainPanel;

    @Nls
    @Override
    public String getDisplayName() {
        return "Lite Workspace Settings";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        apiKeyField = new JTextField(30); // 设置宽度
        apiUrlField = new JTextField(30);

        mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createTitledBorder("LiteWorkspaceConfig"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 1: API Key Label
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(new JLabel("🔐 Dify API Key:"), gbc);

        // Row 1: API Key Field
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(apiKeyField, gbc);

        // Row 2: API URL Label
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel("🌐 Dify API URL:"), gbc);

        // Row 2: API URL Field
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(apiUrlField, gbc);

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        return !apiKeyField.getText().equals(settings.getApiKey()) ||
                !apiUrlField.getText().equals(settings.getApiUrl());
    }

    @Override
    public void apply() {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        settings.setApiKey(apiKeyField.getText());
        settings.setApiUrl(apiUrlField.getText());
    }

    @Override
    public void reset() {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        apiKeyField.setText(settings.getApiKey());
        apiUrlField.setText(settings.getApiUrl());
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
    }
}
