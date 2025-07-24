package org.example.liteworkspace.action;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CompileAndRunDialog extends DialogWrapper {
    private JTextField mainClassField;
    private JTextArea filePathsArea;

    public CompileAndRunDialog() {
        super(true);
        setTitle("Run Selected Java Files");
        setOKButtonText("Run");
        init();
    }

    @Override
    protected javax.swing.JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        panel.add(new JLabel("主类全限定名 (如 com.example.MyMain):"), BorderLayout.NORTH);

        mainClassField = new JTextField();
        panel.add(mainClassField, BorderLayout.CENTER);

        panel.add(new JLabel("源码文件路径 (每行一个 .java 文件):"), BorderLayout.SOUTH);

        filePathsArea = new JTextArea(10, 50);
        JScrollPane scrollPane = new JScrollPane(filePathsArea);
        panel.add(scrollPane, BorderLayout.SOUTH);

        return panel;
    }

    public String getMainClass() {
        return mainClassField.getText();
    }

    public String[] getSourceFilePaths() {
        String text = filePathsArea.getText();
        if (text == null || text.trim().isEmpty()) {
            return new String[0];
        }
        return text.split("\n")
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toArray(String[]::new);
    }
}
