package net.minecraftforge.remapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;

public class SourcesSupplier implements InputSupplier, OutputSupplier {
    List<File> roots;
    final Map<String, String> files = Maps.newHashMap();

    public SourcesSupplier(List<File> roots) {
        this.roots = roots;

        for (File src : roots) {
            String root = src.getAbsolutePath();
            for (File f : Files.fileTreeTraverser().preOrderTraversal(src)) {
                if (f.isDirectory())
                    continue;
                String relative = f.getAbsolutePath().substring(root.length() + 1);
                files.put(relative, root);
            }
        }
    }

    @Override
    public void close() throws IOException {
        // Dont need to close
    }

    @Override
    public OutputStream getOutput(String relPath) {
        File out = null;
        for (File root : roots) {
            File file = new File(root, relPath);
            if (file.exists()) {
                out = file;
                break;
            }
        }
        if (out == null)
            out = new File(roots.get(0), relPath);

        try
        {
            if (!out.exists()) {
                out.getParentFile().mkdirs();
                out.createNewFile();
            }
            return Files.asByteSink(out).openStream();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String getRoot(String resource) {
        for (File root : roots) {
            File file = new File(root, resource);
            if (file.exists())
                return root.getAbsolutePath();
        }
        return roots.get(0).getAbsolutePath();
    }

    @Override
    public InputStream getInput(String relPath) {
        for (File root : roots) {
            File f = new File(root, relPath);
            if (f.exists()) {
                try {
                    return new FileInputStream(f);
                } catch (FileNotFoundException e) {
                    //Will never happen..
                }
            }
        }
        return null;
    }

    @Override
    public List<String> gatherAll(String endFilter) {
        List<String> ret = Lists.newArrayList();
        for (String path : files.keySet())
            if (path.endsWith(endFilter))
                ret.add(path);
        return ret;
    }

}
