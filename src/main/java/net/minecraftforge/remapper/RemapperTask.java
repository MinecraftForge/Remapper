package net.minecraftforge.remapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import joptsimple.internal.Strings;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.rangeapplier.ExceptorFile;
import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;

public class RemapperTask {
    public static interface IProgressListener {
        void writeLine(String line);

    }

    public static void runRemapMod(final List<File> deps, final List<File> srcs, final String mcVersion,
            final String oldMapping, final String newMapping, final File cacheDir, final IProgressListener listener) {
        new Thread(new Runnable(){
            @Override
            public void run() {
                runRemapMod_Thread(deps, srcs, mcVersion, oldMapping, newMapping, cacheDir, listener);
            }
        }).start();
    }

    public static void runRemapMod_Thread(final List<File> deps, final List<File> srcs, final String mcVersion,
            final String oldMapping, final String newMapping, final File cacheDir, final IProgressListener listener) {
        if (extractRange(deps, srcs, listener)) {
            final Map<String, String> excs = Maps.newHashMap();
            SrgContainer srg = createSrg(mcVersion, oldMapping, newMapping, cacheDir, excs, listener);

            if (listener != null)
                listener.writeLine("Loading EXC file");
            try {
                for (String line : Files.readLines(MappingDownloader.getMcp(mcVersion, cacheDir, "joined.exc"), Charsets.UTF_8)) {
                    if (line.indexOf('=') != -1) {
                        String[] pts = line.split("=");
                        excs.put(pts[0], pts[1]);
                    }
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            ExceptorFile exc = new ExceptorFile(){
                {
                    for (Map.Entry<String, String> e : excs.entrySet()) {
                        ExcLine line = super.parseLine(e.getKey() + "=" + e.getValue());
                        if (line != null)
                            this.add(line);
                    }
                }
            };

            applyRemap(srg, exc, deps, srcs, listener);
        }
    }

    private static boolean extractRange(final List<File> deps, final List<File> srcs, final IProgressListener listener) {
        RangeExtractor extractor = new RangeExtractor();

        for (File dep : deps)
            extractor.addLibs(dep);


        final Map<String, String> files = Maps.newHashMap();
        for (File src : srcs) {
            String root = src.getAbsolutePath();
            for (File f : Files.fileTreeTraverser().preOrderTraversal(src)) {
                if (f.isDirectory())
                    continue;
                String relative = f.getAbsolutePath().substring(root.length() + 1);
                files.put(relative, root);
            }
        }

        extractor.setSrc(new SourcesSupplier(srcs));

        PrintStream log = null;
        PrintWriter map = null;
        try {
            log = new PrintStream("./ExtractRange.log"){
                private int total = 1;
                private int current = 0;

                @Override
                public void println(String x) {
                    if (listener != null) {
                        if (x.startsWith("Processing ") && x.endsWith(" files")) {
                            listener.writeLine(x);
                            total = Integer.parseInt(x.split(" ")[1]);
                        }
                        else if (x.startsWith("startProcessing \"")) {
                            String file = x.substring(17, x.indexOf('"', 18));
                            listener.writeLine("Extracting " + ++current + "/" + total + ": " + file);
                        }
                    }
                    super.println(x);
                }
            };
            map = new PrintWriter(new BufferedWriter(new FileWriter("./ExtractRange.txt")));
            extractor.setOutLogger(log);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        boolean worked = extractor.generateRangeMap(map);

        if (log != null)
            log.close();
        if (map != null)
            map.close();

        if (listener != null)
            listener.writeLine("Extracting Range Complete!");

        return worked;
    }

    private static SrgContainer createSrg(String mcVersion, String oldMapping, String newMapping, File cache, Map<String, String> excs, IProgressListener listener) {
        Map<String, String> mold = Maps.newHashMap();
        Map<String, String> mnew = Maps.newHashMap();

        if (listener != null)
            listener.writeLine("Loading Old Mapping");
        for (File f : MappingDownloader.getCsvs(oldMapping, cache))
            loadCSV(f, mold);
        if (listener != null)
            listener.writeLine("Loading New Mapping");
        for (File f : MappingDownloader.getCsvs(newMapping, cache))
            loadCSV(f, mnew);

        SrgContainer ret = new SrgContainer();
        try {
            if (listener != null)
                listener.writeLine("Loading Statics File");
            Set<String> statics = Sets.newHashSet();
            statics.addAll(Files.readLines(MappingDownloader.getMcp(mcVersion, cache, "static_methods.txt"), Charsets.UTF_8));

            Joiner COMMA = Joiner.on(',');

            if (listener != null)
                listener.writeLine("Loading SRG File");
            for (String line : Files.readLines(MappingDownloader.getMcp(mcVersion, cache, "joined.srg"), Charsets.UTF_8)) {
                if (Strings.isNullOrEmpty(line) || line.startsWith("#"))
                    continue;

                String[] pts = line.split(" ");
                if ("PK:".equals(pts[0])) {
                    ret.packageMap.put(pts[1], pts[2]);
                }
                else if ("CL:".equals(pts[0])) {
                    ret.classMap.put(pts[1], pts[2]);
                }
                else if ("FD:".equals(pts[0])) {
                    String cls = pts[2].substring(0, pts[2].lastIndexOf('/') + 1);
                    String fd = pts[2].substring(cls.length());
                    String o = mold.get(fd);
                    String n = mnew.get(fd);
                    if (o == null) o = fd;
                    if (n == null) n = fd;
                    ret.fieldMap.put(cls + o, cls + n);
                }
                else if ("MD:".equals(pts[0])) {
                    String cls = pts[3].substring(0, pts[3].lastIndexOf('/') + 1);
                    String md = pts[3].substring(cls.length());
                    String o = mold.get(md);
                    String n = mnew.get(md);
                    if (o == null) o = md;
                    if (n == null) n = md;
                    ret.methodMap.put(new MethodData(cls + o, pts[4]), new MethodData(cls + n, pts[4]));


                    if (md.startsWith("func_") && !pts[4].startsWith("()")) {
                        int idx = 0;
                        if (statics.contains(md)) {
                            idx++;
                        }
                        List<String> params = Lists.newArrayList();
                        String id = md.split("_")[1];
                        int pos = 1;
                        int end = pts[3].lastIndexOf(')');
                        while (pos < end) {
                            params.add("p_" + id + "_" + idx++ + "_");
                            char i = pts[3].charAt(pos);
                            switch (i) {
                                case 'L':
                                    pos = pts[3].indexOf(';', pos) + 1;
                                    break;
                                case 'J':
                                case 'D':
                                    idx++;
                                    pos++;
                                    break;
                                default:
                                    pos++;
                                    break;
                            }
                        }
                        excs.put(cls + o, "|" + COMMA.join(params));
                    }
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return ret;
    }

    static Splitter COMMA = Splitter.on(",");
    private static void loadCSV(File file, final Map<String, String> map){
        try {
            boolean header = true;
            for (String line : Files.readLines(file, Charsets.UTF_8)) {
                if (header) {
                    header = false;
                    continue;
                }
                List<String> pts = COMMA.splitToList(line);
                map.put(pts.get(0), pts.get(1));
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private static boolean applyRemap(SrgContainer srg, ExceptorFile exc, final List<File> deps, final List<File> srcs, final IProgressListener listener) {
        RangeApplier applier = new RangeApplier();
        applier.setKeepImports(true);
        applier.readSrg(srg);
        if (exc != null && exc.size() > 0)
            applier.readParamMap(exc);

        PrintStream log = null;
        try {
            log = new PrintStream("./ApplyRange.log"){
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
            };
            applier.setOutLogger(log);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        SourcesSupplier sup = new SourcesSupplier(srcs);
        try {
            applier.remapSources(sup, sup, new File("./ExtractRange.txt"), false);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (log != null)
            log.close();
        if (listener != null)
            listener.writeLine("Remapping finished!");

        return true;
    }
}
