package org.example.liteworkspace.index;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class BeanReturnTypeStubIndexExtension extends FileBasedIndexExtension<String, String> {

    private static final ID<String, String> NAME = ID.create("spring.bean.returnType");

    @Override
    public @NotNull ID<String, String> getName() {
        return NAME;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public @NotNull DataIndexer<String, String, FileContent> getIndexer() {
        return new BeanReturnTypeIndexer();
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return new EnumeratorStringDescriptor();
    }

    @Override
    public @NotNull DataExternalizer<String> getValueExternalizer() {
        return new EnumeratorStringDescriptor();
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE);
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    @Override
    public boolean hasSnapshotMapping() {
        return true;
    }
}
