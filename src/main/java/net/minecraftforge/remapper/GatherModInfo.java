package net.minecraftforge.remapper;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import javax.swing.*;

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
            File gradlew = getGradleWrapper(dir);
            if (gradlew == null) {
                JOptionPane.showMessageDialog(null, "This setup must contain the gradle wrapper (gradlew.bat on windows, gradlew or gradlew.sh otherwise)! So we can run a build and gather information.", "ERROR", JOptionPane.ERROR_MESSAGE);
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
            ProcessBuilder pb = new ProcessBuilder(gradlew.getPath(),
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

    private File getGradleWrapper(File dir) {
        for(String filename : getPossibleGradleWrapperFiles()) {
            if(new File(dir, filename).exists()) {
                return new File(dir, filename);
            }
        }
        return null;
    }

    private List<String> getPossibleGradleWrapperFiles() {
        if(RemapperGUI.IS_WINDOWS) {
            return Lists.newArrayList("gradlew.bat");
        } else {
            return Lists.newArrayList("gradlew", "gradlew.sh");
        }
    }

    private void println(final String line) {
        System.out.println(line);
        this.remapperGUI.setStatus(line, Color.BLACK).run();
    }
}