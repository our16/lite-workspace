package org.example.liteworkspace.util;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;

import java.util.HashMap;
import java.util.Map;

public class ConsoleService {
    private static final Map<Project, ConsoleView> consoleMap = new HashMap<>();

    public static void print(Project project, String message, ConsoleViewContentType type) {
        ConsoleView console = consoleMap.computeIfAbsent(project, p -> {
            ConsoleView view = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
            view.print("[RunOnDemand Console Ready]\n", ConsoleViewContentType.SYSTEM_OUTPUT);
            return view;
        });
        console.print(message + "\n", type);
    }
}
