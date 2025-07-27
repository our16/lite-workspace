package org.example.liteworkspace.config;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class LiteWorkspaceSettingsConfigurable implements Configurable {

    private static final int FIELD_WIDTH = 30;
    private static final int PADDING = 5;

    private JTextField apiKeyField;
    private JTextField apiUrlField;
    private JTextField modelField;
    private JTextField javaHomeField;
    private JPanel mainPanel;

    @Nls
    @Override
    public String getDisplayName() {
        return "Lite Workspace Settings";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        initializeFields();
        setupMainPanel();
        return mainPanel;
    }

    private void initializeFields() {
        apiKeyField = new JTextField(FIELD_WIDTH);
        apiUrlField = new JTextField(FIELD_WIDTH);
        modelField = new JTextField(FIELD_WIDTH);
        javaHomeField = new JTextField(FIELD_WIDTH);
    }

    private void setupMainPanel() {
        mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createTitledBorder("LiteWorkspace Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(PADDING);
        gbc.anchor = GridBagConstraints.WEST;

        addLabelAndField("🔐 Dify API Key:", apiKeyField, gbc, 0);
        addLabelAndField("🌐 Dify API URL:", apiUrlField, gbc, 1);
        addLabelAndField("🤖 Model Name:", modelField, gbc, 2);
        addLabelAndField("☕ JAVA_HOME:", javaHomeField, gbc, 3);
    }

    private void addLabelAndField(String labelText, JTextField field,
                                  GridBagConstraints gbc, int row) {
        // Label
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        mainPanel.add(new JLabel(labelText), gbc);

        // Field
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(field, gbc);
    }

    @Override
    public boolean isModified() {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        return !apiKeyField.getText().equals(settings.getApiKey()) ||
                !apiUrlField.getText().equals(settings.getApiUrl()) ||
                !modelField.getText().equals(settings.getModelName()) ||
                !javaHomeField.getText().equals(settings.getJavaHome());
    }

    @Override
    public void apply() {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        settings.setApiKey(apiKeyField.getText().trim());
        settings.setApiUrl(apiUrlField.getText().trim());
        settings.setModelName(modelField.getText().trim());
        settings.setJavaHome(javaHomeField.getText().trim());
    }

    @Override
    public void reset() {
        LiteWorkspaceSettings settings = LiteWorkspaceSettings.getInstance();
        apiKeyField.setText(settings.getApiKey());
        apiUrlField.setText(settings.getApiUrl());
        modelField.setText(settings.getModelName());
        javaHomeField.setText(settings.getJavaHome());
    }

    @Override
    public void disposeUIResources() {
        apiKeyField = null;
        apiUrlField = null;
        modelField = null;
        javaHomeField = null;
        mainPanel = null;
    }
}