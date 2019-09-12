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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

            System.out.println("Executing gradle build this may take some time!");
            println("Executing gradle build this may take some time!");

            File temp_build = new File(dir, "REMAP_MOD_TEMP.gradle");
            FileOutputStream fos = new FileOutputStream(temp_build);
            Files.copy(new File(dir, "build.gradle").toPath(), fos);
            String[] lines = new String[] {
                "task remapGetInfo {",
                "  doLast {",
                "    for (File f : sourceSets.main.compileClasspath)",
                "      println 'DEP: ' + f.getPath()",
                "    println 'DEP: ' + jar.archivePath",
                "    println 'MAPPING: ' + minecraft.mappings",
                "    println 'MINECRAFT: ' + minecraft.version",
                "    for (File f : sourceSets.main.java.srcDirs)",
                "      println 'SOURCE: ' + f.getPath()",
                "    println 'CACHE: ' + new File(project.getGradle().getGradleUserHomeDir(), '/caches/minecraft/').getAbsolutePath()",
                "  }",
                "}",
                "if (project.tasks.findByName('setupDevWorkspace'))",
                "  remapGetInfo.dependsOn('setupDevWorkspace')",
                "remapGetInfo.dependsOn('build')"
            };
            for (String line : lines) {
                fos.write(line.getBytes());
                fos.write('\n');
            }
            fos.close();

            this.remapperGUI.buildFailed = false;
            this.remapperGUI.deps.clear();
            this.remapperGUI.srcs.clear();

            ProcessBuilder pb = new ProcessBuilder(gradlew.getPath(), "--build-file", "REMAP_MOD_TEMP.gradle", "--console=plain", "--stacktrace", "remapGetInfo");
            pb.redirectErrorStream(true);
            pb.directory(dir);
            pb.environment().put("JAVA_HOME", this.remapperGUI.jdkDir.getAbsolutePath());
            System.out.println("Process:   " + pb.command().stream().collect(Collectors.joining(" ")));
            System.out.println("Directory: " + pb.directory());
            System.out.println("Java Home: " + this.remapperGUI.jdkDir);
            Process p = pb.start();
            while (p.isAlive()) {
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line = null;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                    int index = line.indexOf(':');
                    if (index == -1)
                        continue;
                    String prefix = line.substring(0, index);
                    String suffix = line.substring(index + 1).trim();
                    if (prefix.equals("DEP")) {
                        println("DEPENDANCY: " + suffix);
                        this.remapperGUI.deps.add(new File(suffix));
                    }
                    else if (prefix.equals("SOURCE")) {
                        println(line);
                        this.remapperGUI.srcs.add(new File(suffix));
                    }
                    else if (prefix.equals("MINECRAFT")) {
                        println(line);
                        this.remapperGUI.mcVersion = MinecraftVersion.from(suffix);
                        this.remapperGUI.updateGuiState();
                    }
                    else if (prefix.equals("MAPPING")) {
                        println(line);
                        this.remapperGUI.oldMapping = suffix;
                        this.remapperGUI.updateGuiState();
                    }
                    else if (prefix.equals("CACHE")) {
                        println(line);
                        this.remapperGUI.cacheDir = new File(suffix);
                        this.remapperGUI.updateGuiState();
                    }
                    else if(line.startsWith("BUILD FAILED") || line.startsWith("FAILURE")) {
                        this.remapperGUI.buildFailed = true;
                        this.remapperGUI.updateGuiState();
                    }
                }
            }
            if (temp_build.exists())
                temp_build.delete();
            if(!remapperGUI.buildFailed) {
                println("Gradle Build finished");
            }
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
            return Arrays.asList("gradlew.bat");
        } else {
            return Arrays.asList("gradlew", "gradlew.sh");
        }
    }

    private void println(final String line) {
        this.remapperGUI.setStatus(line, Color.BLACK).run();
    }
}
