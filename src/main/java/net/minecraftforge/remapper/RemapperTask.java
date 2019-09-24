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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import net.minecraftforge.srg2source.api.RangeApplierBuilder;
import net.minecraftforge.srg2source.api.RangeExtractorBuilder;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IRenamer;

public class RemapperTask {
    @FunctionalInterface
    public static interface IProgressListener {
        void writeLine(String line);
    }
    private static final IProgressListener NULL = (l) -> {};

    public static void runRemapMod(final List<File> deps, final List<File> srcs, final MinecraftVersion mcVersion,
            final String oldMapping, final String newMapping, final File cacheDir, final IProgressListener listener) {
        new Thread(() -> runRemapMod_Thread(deps, srcs, mcVersion, oldMapping, newMapping, cacheDir, listener)).start();
    }

    public static void runRemapMod_Thread(final List<File> deps, final List<File> srcs, final MinecraftVersion mcVersion,
            final String oldMapping, final String newMapping, final File cache, IProgressListener listener) {

        if (mcVersion == null)
            return;

        if (listener == null)
            listener = NULL;

        if (extractRange(deps, srcs, listener)) {
            File srg = createSrg(mcVersion, oldMapping, newMapping, cache, listener);
            File exc = createExc(mcVersion, oldMapping, newMapping, cache, listener);

            applyRemap(srg, exc, deps, srcs, listener);
        }
    }

    private static boolean extractRange(final List<File> deps, final List<File> srcs, final IProgressListener listener) {
        RangeExtractorBuilder builder = new RangeExtractorBuilder().batch();
        listener.writeLine("Extracting range:");
        deps.forEach(builder::library);
        srcs.forEach(builder::input);

        boolean worked = false;

        try (PrintStream log = new PrintStream("./ExtractRange.log") {
                private int total = 1;
                private int current = 0;

                @Override
                public void println(String x) {
                    if (x.startsWith("Processing ") && x.endsWith(" files")) {
                        listener.writeLine(x);
                        total = Integer.parseInt(x.split(" ")[1]);
                    }
                    else if (x.startsWith("startProcessing \"")) {
                        String file = x.substring(17, x.indexOf('"', 18));
                        listener.writeLine("Extracting " + ++current + "/" + total + ": " + file);
                    }
                    super.println(x);
                }
            }) {

            builder.logger(log);
            builder.output(new File("./ExtractRange.txt"));

            worked = builder.build().run();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        listener.writeLine("Extracting Range Complete!");

        return worked;
    }

    private static File createSrg(MinecraftVersion mcVersion, String oldMapping, String newMapping, File cache, IProgressListener listener) {
        try {
            listener.writeLine("Loading SRG File");

            File mcpMapping = new File(MappingDownloader.getMcpRoot(mcVersion, cache), "obf_to_srg.tsrg");
            if (!mcpMapping.exists()) {
                byte[] data = MappingDownloader.getMcpData(mcVersion, cache, "mappings", false);
                Files.write(mcpMapping.toPath(), data);
            }

            File oldToNewF = "SRG".equals(oldMapping) ? new File(MappingDownloader.getMcpRoot(mcVersion, cache), "srg_to_" + newMapping + ".tsrg") :
                             "SRG".equals(newMapping) ? new File(MappingDownloader.getMappingRoot(oldMapping, mcVersion, cache), oldMapping + "_to_srg.tsrg") :
                                                        new File(MappingDownloader.getMappingRoot(oldMapping, mcVersion, cache), oldMapping + "_to_" + newMapping + ".tsrg");
            if (!oldToNewF.exists()) {
                listener.writeLine("Creating SRG to SRG");
                File srgToSrgF = new File(MappingDownloader.getMcpRoot(mcVersion, cache), "srg_to_srg.tsrg");
                IMappingFile srgToSrg = null;
                if (srgToSrgF.exists()) {
                    srgToSrg = IMappingFile.load(srgToSrgF);
                } else {
                    IMappingFile obfToSrg = IMappingFile.load(mcpMapping);

                    listener.writeLine("Creating SRG to OBF");
                    File srgToObfF = new File(MappingDownloader.getMcpRoot(mcVersion, cache), "srg_to_obf.tsrg");
                    IMappingFile srgToObf = null;
                    if (srgToObfF.exists()) {
                        srgToObf = IMappingFile.load(srgToObfF);
                    } else {
                        srgToObf = obfToSrg.reverse();
                        srgToObf.write(srgToObfF.toPath(), IMappingFile.Format.TSRG, false);
                    }

                    srgToSrg = srgToObf.chain(obfToSrg);
                    srgToSrg.write(srgToSrgF.toPath(), IMappingFile.Format.TSRG, false);
                }

                IMappingFile oldToSrg = null;
                if (!"SRG".equals(oldMapping)) {
                    File oldToSrgF = new File(MappingDownloader.getMappingRoot(oldMapping, mcVersion, cache), oldMapping + "_to_srg.tsrg");
                    if (!oldToSrgF.exists()) {
                        listener.writeLine("Loading Old Mapping");
                        Map<String, String> names = new HashMap<>();
                        loadCSV(MappingDownloader.getCsvs(oldMapping, mcVersion, cache), names);

                        listener.writeLine("Creating Old to SRG");
                        oldToSrg = srgToSrg.rename(new IRenamer() {
                            @Override
                            public String rename(IField value) {
                                return names.getOrDefault(value.getMapped(), value.getMapped());
                            }
                            @Override
                            public String rename(IMethod value) {
                                return names.getOrDefault(value.getMapped(), value.getMapped());
                            }
                        }).reverse();
                        oldToSrg.write(oldToSrgF.toPath(), IMappingFile.Format.TSRG, false);
                    } else {
                        oldToSrg = IMappingFile.load(oldToSrgF);
                    }
                } else {
                    oldToSrg = srgToSrg;
                }

                IMappingFile srgToNew = null;
                if (!"SRG".equals(newMapping)) {

                    File srgToNewF = new File(MappingDownloader.getMappingRoot(newMapping, mcVersion, cache), "srg_to_" + newMapping + ".tsrg");
                    if (!srgToNewF.exists()) {
                        listener.writeLine("Loading New Mapping");
                        Map<String, String> names = new HashMap<>();
                        loadCSV(MappingDownloader.getCsvs(newMapping, mcVersion, cache), names);

                        listener.writeLine("Creating New to SRG");
                        srgToNew = srgToSrg.rename(new IRenamer() {
                            @Override
                            public String rename(IField value) {
                                return names.getOrDefault(value.getMapped(), value.getMapped());
                            }
                            @Override
                            public String rename(IMethod value) {
                                return names.getOrDefault(value.getMapped(), value.getMapped());
                            }
                        });
                        srgToNew.write(srgToNewF.toPath(), IMappingFile.Format.TSRG, false);
                    } else {
                        srgToNew = IMappingFile.load(srgToNewF);
                    }
                } else {
                    srgToNew = srgToSrg;
                }

                listener.writeLine("Creating Old to New");
                IMappingFile oldToNew = oldToSrg.chain(srgToNew);
                oldToNew.write(oldToNewF.toPath(), IMappingFile.Format.TSRG, false);
            }
            return oldToNewF;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static File createExc(MinecraftVersion mcVersion, String oldMapping, String newMapping, File cache, IProgressListener listener) {
        try {
            File oldToNewF = "SRG".equals(oldMapping) ? new File(MappingDownloader.getMcpRoot(mcVersion, cache), "srg_to_" + newMapping + ".exc") :
                             "SRG".equals(newMapping) ? new File(MappingDownloader.getMappingRoot(oldMapping, mcVersion, cache), oldMapping + "_to_srg.exc") :
                                                        new File(MappingDownloader.getMappingRoot(oldMapping, mcVersion, cache), oldMapping + "_to_" + newMapping + ".exc");
            if (!oldToNewF.exists()) {

                listener.writeLine("Loading Statics File");
                File staticsF = new File(MappingDownloader.getMcpRoot(mcVersion, cache), "statics.txt");
                Set<String> statics;
                if (!staticsF.exists()) {
                    byte[] data = MappingDownloader.getMcpData(mcVersion, cache, "statics", false);
                    Files.write(staticsF.toPath(), data);
                    statics = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8)).lines().collect(Collectors.toSet());
                } else {
                    statics = Files.lines(staticsF.toPath(), StandardCharsets.UTF_8).collect(Collectors.toSet());
                }

                listener.writeLine("Loading Constructors File");
                File ctrsF = new File(MappingDownloader.getMcpRoot(mcVersion, cache), "constructors.txt");
                Map<String, String> ctrs;
                if (!ctrsF.exists()) {
                    byte[] data = MappingDownloader.getMcpData(mcVersion, cache, "constructors", false);
                    Files.write(ctrsF.toPath(), data);
                    ctrs = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8)).lines()
                        .map(l -> l.split(" "))
                        .collect(Collectors.toMap(e -> e[1] + ' ' + e[2], e -> 'i' + e[0], (o,n) -> n));
                } else {
                    ctrs = Files.lines(ctrsF.toPath(), StandardCharsets.UTF_8)
                        .map(l -> l.split(" "))
                        .collect(Collectors.toMap(e -> e[1] + ' ' + e[2], e -> 'i' + e[0], (o,n) -> n));
                }

                listener.writeLine("Loading MCP Mappings");
                File mcpMapping = new File(MappingDownloader.getMcpRoot(mcVersion, cache), "obf_to_srg.tsrg");
                IMappingFile obfToSrg = null;
                if (!mcpMapping.exists()) {
                    byte[] data = MappingDownloader.getMcpData(mcVersion, cache, "mappings", false);
                    Files.write(mcpMapping.toPath(), data);
                    obfToSrg = IMappingFile.load(new ByteArrayInputStream(data));
                } else {
                    obfToSrg = IMappingFile.load(mcpMapping);
                }

                Map<String, String> oNames = new HashMap<>();
                if (!"SRG".equals(oldMapping)) {
                    listener.writeLine("Loading Old Mapping");
                    loadCSV(MappingDownloader.getCsvs(oldMapping, mcVersion, cache), oNames);
                }

                Map<String, String> nNames = new HashMap<>();
                if (!"SRG".equals(newMapping)) {
                    listener.writeLine("Loading New Mapping");
                    loadCSV(MappingDownloader.getCsvs(newMapping, mcVersion, cache), nNames);
                }

                Function<String, BiFunction<String, String, List<String>>> getParams = (name) -> {
                    return (id, desc) -> {
                        int idx = statics.contains(name) ? 1 : 0;
                        int pos = 1;
                        int end = desc.lastIndexOf(')');
                        List<String> params = new ArrayList<>();
                        while (pos < end) {
                            String srg = "p_" + id + '_' + idx++ + '_';
                            params.add(nNames.getOrDefault(srg, srg));
                            switch (desc.charAt(pos)) {
                                case 'L':
                                    pos = desc.indexOf(';', pos) + 1;
                                    break;
                                case 'J':
                                case 'D':
                                    idx++;
                                default:
                                    pos++;
                            }
                        }
                        return params;
                    };
                };

                final Map<String, List<String>> lines = new HashMap<>();
                ctrs.forEach((k,v) -> {
                    String[] pts = k.split(" ");
                    lines.put(pts[0] + ".<init>" + pts[1], getParams.apply("<init>").apply(v, pts[1]));
                });

                obfToSrg.getClasses().forEach(cls -> {
                    cls.getMethods().forEach(mtd -> {
                        String name = mtd.getMapped();
                        String desc = mtd.getDescriptor();
                        String id = null;

                        if (name.startsWith("func_") && !desc.startsWith("()"))
                            id = name.split("_")[1];
                        else if (name.equals("<init>"))
                            id = ctrs.getOrDefault(cls.getMapped() + ' ' + desc, null);

                        if (id != null)
                            lines.put(cls.getMapped() + '.' + mtd.getMapped() + mtd.getMappedDescriptor(), getParams.apply(name).apply(id, desc));
                    });
                });

                try (BufferedWriter writer = Files.newBufferedWriter(oldToNewF.toPath())) {
                    List<String> lns = lines.entrySet().stream()
                    .map(e -> e.getKey() + "=|" + e.getValue().stream().collect(Collectors.joining(",")))
                    .collect(Collectors.toList());

                    Collections.sort(lns);
                    for (String line : lns) {
                        writer.write(line);
                        writer.write('\n');
                    }
                }
            }
            return oldToNewF;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    private static boolean loadCSV(File file, final Map<String, String> map){
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> enu = zip.entries();
            while (enu.hasMoreElements()) {
                ZipEntry entry = enu.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".csv"))
                    continue;
                CsvReader reader = new CsvReader();
                reader.setContainsHeader(true);
                CsvContainer csv = reader.read(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
                csv.getRows().forEach(row -> map.put(row.getField(0), row.getField(1)));
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean applyRemap(File srg, File exc, final List<File> deps, final List<File> srcs, final IProgressListener listener) {
        RangeApplierBuilder builder = new RangeApplierBuilder().keepImports();
        builder.srg(srg);
        if (exc != null && exc.exists())
            builder.exc(exc);

        try(PrintStream log  = new PrintStream("./ApplyRange.log"){
            private int total = 1;
            private int current = 0;

            @Override
            public void println(String x) {
                if (listener != null) {
                    if (x.startsWith("Processing ") && x.endsWith(" files")) {
                        listener.writeLine(x);
                        total = Integer.parseInt(x.split(" ")[1]);
                    }
                    else if (x.startsWith("Start Processing: ")) {
                        String file = x.substring(17);
                        listener.writeLine("Remapping " + ++current + "/" + total + ": " + file);
                    }
                }
                super.println(x);
            }
        }) {
            builder.logger(log);
            srcs.forEach(builder::input);
            builder.range(new File("./ExtractRange.txt"));
            builder.output(new File("./output/")); //TODO: Change API to expose Suppliers
            RangeApplier applier = builder.build();
            SourcesSupplier sup = new SourcesSupplier(srcs);
            applier.setOutput(sup);
            applier.run();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }


        if (listener != null)
            listener.writeLine("Remapping finished!");

        return true;
    }
}
