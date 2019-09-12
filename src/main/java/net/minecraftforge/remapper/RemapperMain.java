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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JOptionPane;

public class RemapperMain {
    public static void main(String[] args) throws FileNotFoundException {
        File f = new File(RemapperMain.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        File output;
        if (f.isFile()) output = new File(f.getName() + ".log");
        else            output = new File("remapper.log");

        OutputStream log = new BufferedOutputStream(new FileOutputStream(output));
        System.setOut(new PrintStream(new ChainedStream(log, System.out)));
        System.setErr(new PrintStream(new ChainedStream(log, System.err)));

        loadGUI();
    }
    private static void loadGUI() {
        String path = RemapperMain.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (path.contains("!/")) {
            JOptionPane.showMessageDialog(null, "Due to java limitation, please do not run this jar in a folder ending with ! : \n"+ path, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        new RemapperGUI();
    }
}
