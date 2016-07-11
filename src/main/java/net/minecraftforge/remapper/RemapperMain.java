package net.minecraftforge.remapper;

import javax.swing.JOptionPane;

public class RemapperMain {
    public static void main(String[] args) {
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
