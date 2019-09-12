/*
 * Remapper
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.remapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;

public class SourcesSupplier implements InputSupplier, OutputSupplier {
    List<File> roots;
    final Map<String, String> files = new HashMap<>();

    public SourcesSupplier(List<File> roots) {
        this.roots = roots;

        for (File src : roots) {
            String root = src.getAbsolutePath();
            Path path = src.toPath();
            try {
                Files.walk(path)
                    .filter(Files::isRegularFile)
                    .map(path::relativize)
                    .forEach(rel -> files.put(rel.toString(), root));
            } catch (IOException e) {
                e.printStackTrace();
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
            if (!out.getParentFile().exists())
                out.getParentFile().mkdirs();
            return new FileOutputStream(out);
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
        List<String> ret = new ArrayList<>();
        for (String path : files.keySet())
            if (path.endsWith(endFilter))
                ret.add(path);
        return ret;
    }

}
