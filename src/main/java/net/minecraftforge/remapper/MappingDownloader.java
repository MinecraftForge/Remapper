package net.minecraftforge.remapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;

public class MappingDownloader implements Runnable {
    private static Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Map<String, List<String>> mappings = Maps.newLinkedHashMap();

    public static void downloadMappingList(Runnable callback) {
        new Thread(new MappingDownloader(callback)).run();
    }

    private Runnable callback;
    private MappingDownloader(Runnable callback) {
        this.callback = callback;
    }

    private void loadCache() {
        File cache = new File("./mappings.json");
        if (!cache.exists())
            return;

        try {
            loadJson(Files.toString(cache, Charsets.UTF_8));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void download() {
        File cache = new File("./mappings.json");
        try {
            URLConnection con = (new URL("http://export.mcpbot.bspk.rs/versions.json")).openConnection();
            String data = CharStreams.toString(new InputStreamReader(con.getInputStream()));
            JsonObject obj = new JsonParser().parse(data).getAsJsonObject();
            Files.write(GSON.toJson(obj), cache, Charsets.UTF_8);
            loadJson(data);
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void loadJson(String data) {
        try {
            Map<String, Map<String, int[]>> json = GSON.fromJson(data, new TypeToken<Map<String, Map<String, int[]>>>() {}.getType());
            mappings.clear();
            for (String mcver : json.keySet()) {
                Map<String, int[]> values = json.get(mcver);
                List<String> tmp = Lists.newArrayList();
                mappings.put(mcver, tmp);
                for (String channel : values.keySet()) {
                    for (int id : values.get(channel)) {
                        tmp.add(channel + "_" + id);
                    }
                }
            }

        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        loadCache();
        download();

        callback.run();
    }

    public static boolean needsDownload(String mcVersion, String mapping, File cacheDir) {
        if ("UNLOADED".equals(mapping))
            return false;
        if (!getMcp(mcVersion, cacheDir, "joined.srg").exists() ||
            !getMcp(mcVersion, cacheDir, "joined.exc").exists() ||
            !getMcp(mcVersion, cacheDir, "static_methods.txt").exists())
            return true;

        for (File f : getCsvs(mapping, cacheDir))
            if (!f.exists())
                return true;
        return false;
    }


    public static URL getMaven(String org, String artifact, String version) {
        return getMaven(org, artifact, version, null, "jar");
    }
    public static URL getMaven(String org, String artifact, String version, String classifier, String ext) {
        try {
            return new URL("http://files.minecraftforge.net/maven/" + org.replace('.', '/') +
                    "/" + artifact +
                    "/" + version +
                    "/" + artifact + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + ext);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static File getMcp(String mcVersion, File cacheDir, String file) {
        return new File(cacheDir, "de/oceanlabs/mcp/mcp/" + mcVersion + "/" + file);
    }
    public static File[] getCsvs(String mapping, File cacheDir) {
        if ("SRG".equals(mapping))
            return new File[0];
        mapping = mapping.replace("_nodoc", "");
        String channel = mapping;
        String version = mapping;
        if (mapping.indexOf('_') != -1) {
            channel = mapping.substring(0, mapping.lastIndexOf('_'));
            version = mapping.substring(mapping.lastIndexOf('_') + 1);
        }
        File base = new File(cacheDir, "de/oceanlabs/mcp/mcp_" + channel + "_nodoc/" + version);
        return new File[]{
            new File(base, "fields.csv"),
            new File(base, "methods.csv"),
            new File(base, "params.csv")
        };
    }

    public static void download(final String mcVersion, final String mapping, final File cacheDir, final Runnable callback) {
        new Thread(new Runnable(){
            @Override
            public void run() {
                if (!getMcp(mcVersion, cacheDir, "joined.srg").exists() ||
                    !getMcp(mcVersion, cacheDir, "joined.exc").exists() ||
                    !getMcp(mcVersion, cacheDir, "static_methods.txt").exists())
                {

                    URL maven = getMaven("de.oceanlabs.mcp", "mcp", mcVersion, "srg", "zip");
                    File base = getMcp(mcVersion, cacheDir, "");
                    downloadZip(base, maven);
                }

                String tmp = mapping.replace("_nodoc", "");
                String channel = tmp.substring(0, tmp.lastIndexOf('_'));
                String version = tmp.substring(tmp.lastIndexOf('_') + 1);

                for (File f : getCsvs(mapping, cacheDir)) {
                    if (!f.exists()) {

                        String mavenVer = mcVersion;
                        for (String key : mappings.keySet()) {
                            if (mappings.get(key).contains(channel + "_" + version)) {
                                mavenVer = key;
                                break;
                            }
                        }

                        URL maven = getMaven("de.oceanlabs.mcp", "mcp_" + channel +"_nodoc", version + "-" + mavenVer, null, "zip");
                        downloadZip(f.getParentFile(), maven);
                        break;
                    }
                }

                if (callback != null)
                    callback.run();
            }
        }).start();
    }

    private static void downloadZip(File cacheDir, URL url) {
        InputStream is = null;
        FileOutputStream fos = null;
        System.out.println("Downloading: " + url);
        System.out.println("To:          " + cacheDir.getAbsolutePath());

        try {
            is = url.openStream();
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry entry = null;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory())
                    continue;

                File cache = new File(cacheDir, entry.getName());
                if (!cache.getParentFile().exists())
                    cache.getParentFile().mkdirs();
                ByteStreams.copy(zis, fos = new FileOutputStream(cache));
                fos.close();
            }

        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
}
