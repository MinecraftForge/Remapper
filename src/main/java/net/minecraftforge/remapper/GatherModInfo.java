package net.minecraftforge.remapper;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import javax.swing.JOptionPane;

import com.google.common.base.Splitter;
import com.google.common.io.Files;

class GatherModInfo implements ActionListener, Runnable {
    private final RemapperGUI remapperGUI;
    GatherModInfo(RemapperGUI remapperGUI) {
        this.remapperGUI = remapperGUI;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new Thread(this).start();
    }
    public void run() {
        File dir = this.remapperGUI.targetDir;
        if (dir == null || !(new File(dir, "build.gradle").exists())) {
            JOptionPane.showMessageDialog(null, "You must first select a directory that contains a build.gradle file", "ERROR", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            if (!(new File(dir, RemapperGUI.IS_WINDOWS ? "gradlew.bat" : "gradlew.sh").exists())) {
                JOptionPane.showMessageDialog(null, "This setup must contain the gradle wrapper! So we can run a build and gather information.", "ERROR", JOptionPane.ERROR_MESSAGE);
                return;
            }

            println("Executing gradle build this may take some time!");

            File temp_build = new File(dir, "REMAP_MOD_TEMP.gradle");
            FileOutputStream fos = new FileOutputStream(temp_build);
            Files.copy(new File(dir, "build.gradle"), fos);
            String[] lines = new String[] {
                "task remapGetInfo << {",
                "   for (File f : sourceSets.main.compileClasspath)",
                "       println 'DEP: ' + f.getPath()",
                "   println 'DEP: ' + jar.archivePath",
                "   println 'MAPPING: ' + minecraft.mappings",
                "   println 'MINECRAFT: ' + minecraft.version",
                "   for (File f : sourceSets.main.java.srcDirs)",
                "       println 'SOURCE: ' + f.getPath()",
                "   println 'CACHE: ' + new File(project.getGradle().getGradleUserHomeDir(), '/caches/minecraft/').getAbsolutePath()",
                "}",
                "remapGetInfo.dependsOn('setupDevWorkspace', 'build')"
            };
            for (String line : lines) {
                fos.write(line.getBytes());
                fos.write('\n');
            }
            fos.close();

            this.remapperGUI.deps.clear();
            this.remapperGUI.srcs.clear();

            Splitter SPLITTER = Splitter.on(": ").limit(2);
            ProcessBuilder pb = new ProcessBuilder(RemapperGUI.IS_WINDOWS ? "./gradlew.bat" : "./gradlew.sh",
                    "--build-file", "REMAP_MOD_TEMP.gradle", "remapGetInfo");
            pb.directory(dir);
            Process p = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = null;
            while ((line = br.readLine()) != null) {
                //println(line);
                List<String> pts = SPLITTER.splitToList(line);
                if (pts.size() < 2)
                    continue;
                if (pts.get(0).equals("DEP")) {
                    println("DEPENDANCY: " + pts.get(1));
                    this.remapperGUI.deps.add(new File(pts.get(1)));
                }
                else if (pts.get(0).equals("SOURCE")) {
                    println(line);
                    this.remapperGUI.srcs.add(new File(pts.get(1)));
                }
                else if (pts.get(0).equals("MINECRAFT")) {
                    println(line);
                    this.remapperGUI.mcVersion = pts.get(1);
                    this.remapperGUI.updateGuiState();
                }
                else if (pts.get(0).equals("MAPPING")) {
                    println(line);
                    this.remapperGUI.oldMapping = pts.get(1);
                    this.remapperGUI.updateGuiState();
                }
                else if (pts.get(0).equals("CACHE")) {
                    println(line);
                    this.remapperGUI.cacheDir = new File(pts.get(1));
                    this.remapperGUI.updateGuiState();
                }
            }
            if (temp_build.exists())
                temp_build.delete();
            println("Gradle Build finished");
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }
    private void println(final String line) {
        System.out.println(line);
        this.remapperGUI.setStatus(line, Color.BLACK).run();
    }
}