package org.example.liteworkspace.listener;

import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.example.liteworkspace.bean.core.context.LiteProjectContext;
import org.jetbrains.annotations.NotNull;

public class LiteFileWatcher implements VirtualFileListener {

    private final LiteProjectContext context;

    public LiteFileWatcher(LiteProjectContext context) {
        this.context = context;
        VirtualFileManager.getInstance().addVirtualFileListener(this);
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        String path = event.getFile().getPath();
        if (path.endsWith(".xml") || path.endsWith(".java")) {
            // TODO: 简化逻辑，仅触发增量更新（可通过 hash 判断变更）
//            new ConfigurationScanner(context).scan();
//            new MyBatisXmlFinder(context).scan();
        }
    }
}

