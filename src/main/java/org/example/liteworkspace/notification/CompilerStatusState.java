package org.example.liteworkspace.notification;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompilerStatusState {
    private final Map<VirtualFile, Boolean> state = new ConcurrentHashMap<>();

    private static CompilerStatusState stateService;

    public static CompilerStatusState getInstance(Project project) {
        if (stateService != null) {
            return stateService;
        }
        stateService = project.getService(CompilerStatusState.class);
        return stateService;
    }

    public void setShow(VirtualFile file, boolean show) {
        state.put(file, show);
    }

    public boolean shouldShow(VirtualFile file) {
        return state.getOrDefault(file, false);
    }
}
