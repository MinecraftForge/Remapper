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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.minecraftforge.remapper.json.Config;
import net.minecraftforge.remapper.json.MCPConfigV1;

public class MappingDownloader implements Runnable {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Map<MinecraftVersion, List<String>> mappings = new TreeMap<>();
    private static Map<String, byte[]> mcpdata_cache = new HashMap<>();

    public static void downloadMappingList(Runnable callback) {
        new Thread(new MappingDownloader(callback)).run();
    }

    private Runnable callback;
    private MappingDownloader(Runnable callback) {
        this.callback = callback;
    }

    private String toString(File file) throws IOException {
        return toString(file.toPath());
    }
    private String toString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
    private String toString(InputStream in) throws IOException {
        StringBuilder buf = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            int c = 0;
            while ((c = reader.read()) != -1)
                buf.append((char) c);
        }
        return buf.toString();
    }

    private boolean loadCache() {
        File cache = new File("./mappings.json");
        if (!cache.exists())
            return false;

        try {
            loadJson(toString(cache));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean download() {
        Path cache = Paths.get("./mappings.json");
        try {
            URLConnection con = (new URL("http://export.mcpbot.bspk.rs/versions.json")).openConnection();
            String data = toString(con.getInputStream());
            JsonObject obj = new JsonParser().parse(data).getAsJsonObject();
            Files.write(cache, GSON.toJson(obj).getBytes(StandardCharsets.UTF_8));
            loadJson(data);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void loadJson(String data) {
        try {
            Map<String, Map<String, int[]>> json = GSON.fromJson(data, new TypeToken<Map<String, Map<String, int[]>>>() {}.getType());
            mappings.clear();
            for (String mcver : json.keySet()) {
                Map<String, int[]> values = json.get(mcver);
                List<String> tmp = new ArrayList<>();
                mappings.put(MinecraftVersion.from(mcver), tmp);
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

    public static boolean needsDownload(MinecraftVersion mcVersion, String mapping, File cacheDir) {
        if (mcVersion == null  || "UNLOADED".equals(mapping))
            return false;
        if (!getMcp(mcVersion.toString(), cacheDir).exists())
            return true;

        return getCsvs(mapping, cacheDir) != null && !getCsvs(mapping, cacheDir).exists();
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

    public static byte[] getMcpData(String version, File cache, String key, boolean optional) throws IOException {
        byte[] data = mcpdata_cache.get(version + " " + key);
        if (data != null)
            return data;

        File mcp = getMcp(version, cache);
        if (!mcp.exists())
            return null;

        try (ZipFile zip = new ZipFile(mcp)) {
            data = mcpdata_cache.get(version + " config.json");
            if (data == null) {
                ZipEntry entry = zip.getEntry("config.json");
                if (entry == null)
                    throw new IOException("Zip Missing Entry: config.json File: " + mcp.getAbsolutePath());
                data = getBytes(zip.getInputStream(entry));
                mcpdata_cache.put(version + " config.json", data);
            }

            if ("config.json".equals(key))
                return data;

            int spec = Config.getSpec(data);
            if (spec != 1)
                throw new IllegalArgumentException("Unknown MCPConfig Spec version: " + spec + " in " + mcp.getAbsolutePath());

            MCPConfigV1 cfg = MCPConfigV1.get(data);
            String entryName = cfg.getData(key);
            if (entryName == null) {
                if (optional) {
                    data = new byte[0];
                    mcpdata_cache.put(version + " " + key, data);
                    return data;
                }
                throw new IllegalArgumentException("MCPConfig did not have data entry for \"" + key + "\" in " + mcp.getAbsolutePath());
            }

            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null)
                throw new IOException("Zip Missing Entry: \"" + entryName + "\" For Data: \"" + key + "\" in " + mcp.getAbsolutePath());
            data = getBytes(zip.getInputStream(entry));
            mcpdata_cache.put(version + " " + key, data);
            return data;
        }

    }
    private static byte[] getBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out);
        return out.toByteArray();
    }
    private static int copy(InputStream in, OutputStream out) throws IOException {
        int count = 0;
        int c = 0;
        byte[] buf = new byte[0x100];
        while ((c = in.read(buf, 0, buf.length)) != -1) {
            out.write(buf, 0, c);
            count += c;
        }
        return count;
    }

    public static File getMcp(String version, File cache) {
        return new File(cache, "de/oceanlabs/mcp/mcp_config/" + version + "/mcp_config-" + version + ".zip");
    }
    public static File getMcpRoot(String version, File cache) {
        return new File(cache, "de/oceanlabs/mcp/mcp_config/" + version + "/");
    }
    public static File getCsvs(String mapping, File cacheDir) {
        if ("SRG".equals(mapping))
            return null;
        mapping = mapping.replace("_nodoc", "");
        String channel = mapping;
        String version = mapping;
        if (mapping.indexOf('_') != -1) {
            channel = mapping.substring(0, mapping.lastIndexOf('_'));
            version = mapping.substring(mapping.lastIndexOf('_') + 1);
        }
        return new File(cacheDir, "de/oceanlabs/mcp/mcp_" + channel + "_nodoc/" + version + "/mcp_" + channel + "_nodoc-" + version + ".zip");
    }

    public static File getMappingRoot(String mapping, File cache) {
        if ("SRG".equals(mapping))
            return null;
        mapping = mapping.replace("_nodoc", "");
        String channel = mapping;
        String version = mapping;
        if (mapping.indexOf('_') != -1) {
            channel = mapping.substring(0, mapping.lastIndexOf('_'));
            version = mapping.substring(mapping.lastIndexOf('_') + 1);
        }
        return new File(cache, "de/oceanlabs/mcp/mcp_" + channel + "_nodoc/" + version + "/");
    }

    public static void download(final String mcVersion, final String mapping, final File cacheDir) {
        download(mcVersion, mapping, cacheDir, (status) -> {});
    }
    public static void download(final String mcVersion, final String mapping, final File cacheDir, final Consumer<Boolean> callback) {
        new Thread(() -> {
            if (!getMcp(mcVersion, cacheDir).exists()) {
                if (!download(getMaven("de.oceanlabs.mcp", "mcp_config", mcVersion, null, "zip"), getMcp(mcVersion, cacheDir))) {
                    callback.accept(false);
                    return;
                }
            }

            String tmp = mapping.replace("_nodoc", "");
            String channel = tmp.substring(0, tmp.lastIndexOf('_'));
            String version = tmp.substring(tmp.lastIndexOf('_') + 1);
            File csvs = getCsvs(mapping, cacheDir);


            if (csvs != null && !csvs.exists()) {
                String mavenVer = mcVersion;
                for (Entry<MinecraftVersion, List<String>> entry : mappings.entrySet()) {
                    if (entry.getValue().contains(channel + "_" + version)) {
                        mavenVer = entry.getKey().toString();
                        break;
                    }
                }

                URL maven = getMaven("de.oceanlabs.mcp", "mcp_" + channel +"_nodoc", version + "-" + mavenVer, null, "zip");
                if (!download(maven, csvs.getParentFile())) {
                    callback.accept(false);
                    return;
                }
            }

            callback.accept(true);
        }).start();
    }

    private static boolean download(URL url, File target) {
        System.out.println("Downloading: " + url);
        System.out.println("To:          " + target.getAbsolutePath());

        try (InputStream is = url.openStream()) {
            Files.copy(is, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
