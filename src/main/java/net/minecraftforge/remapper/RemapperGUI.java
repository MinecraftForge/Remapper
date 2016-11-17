package net.minecraftforge.remapper;

import com.google.common.collect.Lists;

import net.minecraftforge.remapper.RemapperTask.IProgressListener;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class RemapperGUI {
    private static final int MARGIN = 15;

    static boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
    private JFrame mainFrame;
    private JLabel status;
    private JButton btnGetModInfo;
    File targetDir = new File(".");
    ListModel<File> deps = new ListModel<File>();
    ListModel<File> srcs = new ListModel<File>();
    public boolean buildFailed;

    File cacheDir = new File(".");
    private JComboBox<String> jmcVersion;
    private JComboBox<String> joldMapping, jnewMapping;
    private JButton           jDownloadOld, jDownloadNew;
    String mcVersion = "UNLOADED";
    String oldMapping = "UNLOADED";
    private String newMapping = "UNLOADED";

    private JButton btnRemapMod;

    private List<Runnable> changeListeners = Lists.newArrayList();


    public RemapperGUI() {
        mainFrame = new JFrame("Java Source Remapper");
        mainFrame.setSize(500, 500);

        /*
         * +---------------------+
         * |Folder and load info |
         * |                     |
         * +----------+----------+
         * |   Deps   | Sources  |
         * |          |          |
         * |          |          |
         * +----------+----------+
         * |          |cache dir |
         * |mcver+old  newmapping|
         * |map.+btns   buttons  |
         * +----------+----------+
         *
         */
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();

        JComponent folderAndLoadInfo = createFolderAndLoadInfoComponent();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 2;
        constraints.gridheight = 2;//2 units high
        constraints.weighty = 0;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(MARGIN, MARGIN, 0, MARGIN);
        mainPanel.add(folderAndLoadInfo, constraints);

        JComponent dependencies = createDependenciesComponent();
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridwidth = 1;
        constraints.gridheight = 3;//3 units high
        constraints.weighty = 1.0;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(5, MARGIN, 5, 5);
        mainPanel.add(dependencies, constraints);

        JComponent sources = createSourcesComponent();
        constraints.gridx = 1;
        constraints.gridy = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridwidth = 1;
        constraints.gridheight = 3;//3 units high
        constraints.weighty = 1.0;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(5, 5, 5, MARGIN);
        mainPanel.add(sources, constraints);

        JComponent mappingsSelectionLeft = createMappingsSelectionLeft();
        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 1;
        constraints.gridheight = 2;
        constraints.weighty = 0;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(5, MARGIN, 20, 5);
        mainPanel.add(mappingsSelectionLeft, constraints);

        JComponent mappingsSelectionRight = createMappingsSelectionRight();
        constraints.gridx = 1;
        constraints.gridy = 5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 1;
        constraints.gridheight = 2;
        constraints.weighty = 0;
        constraints.weightx = 1.0;
        constraints.insets = new Insets(5, 5, 20, MARGIN);
        mainPanel.add(mappingsSelectionRight, constraints);

        mainFrame.getContentPane().add(mainPanel, BorderLayout.CENTER);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setMinimumSize(new Dimension(440, 320));
        mainFrame.setVisible(true);

        MappingDownloader.downloadMappingList(new Runnable(){
            @Override
            public void run() {
                updateGuiState();
            }
        });
    }

    private JComponent createFolderAndLoadInfoComponent() {
        Box box = Box.createVerticalBox();
        //==================================================
        // Folder select
        JPanel folderSelect = createFolderSelectionComponent();
        //=====================================================
        status = new JLabelMax("Select Folder!", SwingConstants.CENTER);
        status.setOpaque(true);
        //=====================================================
        JPanel buttonList = new JPanel();
        btnGetModInfo = new JButton("Load Info");
        btnGetModInfo.setEnabled(false);
        btnGetModInfo.setToolTipText("Load information from a build.gradle");
        btnGetModInfo.addActionListener(new GatherModInfo(this));
        buttonList.add(btnGetModInfo);
        //=====================================================
        box.add(folderSelect);
        box.add(status);
        box.add(buttonList);

        return box;
    }

    private JPanel createFolderSelectionComponent() {
        JPanel folderSelect = new JPanel();
        folderSelect.setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();

        constraints.gridx = 0;
        folderSelect.add(new JLabel("Project Folder:"), constraints);

        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        folderSelect.add(createBrowseBox(25, new IBrowseListener() {
            @Override public File getValue() { return targetDir; }
            @Override public void setValue(File value) { targetDir = value; }
        }), constraints);
        return folderSelect;
    }

    private JComponent createDependenciesComponent() {
        Box depBox = Box.createVerticalBox();

        depBox.add(new JLabelMax("Dependencies:", JLabel.LEADING));

        JList<File> depList = new JList<File>(this.deps);
        depList.setCellRenderer(new FileListRenderer());

        depBox.add(new JScrollPane(depList));
        depBox.setMinimumSize(new Dimension(225, 200));

        return depBox;
    }

    private JComponent createSourcesComponent() {
        Box srcBox = Box.createVerticalBox();

        srcBox.add(new JLabelMax("Sources:", JLabel.LEADING));

        JList<File> srcList = new JList<File>(this.srcs);
        srcList.setCellRenderer(new FileListRenderer());

        srcBox.add(new JScrollPane(srcList));
        srcBox.setMinimumSize(new Dimension(225, 200));

        return srcBox;
    }

    private JComponent createMappingsSelectionLeft() {

        /*
         *     2    :      3
         * +--------+------------+
         * |MC Ver  |Select      |
         * +--------+------------+
         * |Old:    |Select      |
         * +--------+------------+
         * |        |    Download|
         * +--------+------------+
         */
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();

        JComponent minecraftVersionLabel = makeLabel("Minecraft: ");
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 2;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 0;
        panel.add(minecraftVersionLabel, constraints);

        JComponent selectMinecraftVersion = createSelectMcVersionComponent();
        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 1.0;
        panel.add(selectMinecraftVersion, constraints);

        JComponent oldMappingsLabel = makeLabel("Old: ");
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 2;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 0;;
        panel.add(oldMappingsLabel, constraints);

        JComponent oldMappingsSelection = createOldMappingsSelection();
        constraints.gridx = 2;
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 1.0;
        panel.add(oldMappingsSelection, constraints);

        JComponent downloadOldButton = createDownloadOldButton();
        constraints.gridx = 2;
        constraints.gridy = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 0;
        panel.add(downloadOldButton, constraints);

        return panel;
    }

    private JComponent createSelectMcVersionComponent() {
        this.jmcVersion = new JComboBox<String>();
        this.jmcVersion.setEnabled(false);
        this.jmcVersion.addItemListener(new ItemListener(){
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() != ItemEvent.SELECTED || !jmcVersion.isEnabled())
                    return;
                RemapperGUI.this.mcVersion = (String)jmcVersion.getSelectedItem();
                updateGuiState();
            }
        });
        return this.jmcVersion;
    }

    private JComponent createOldMappingsSelection() {
        this.joldMapping = new JComboBox<String>();
        this.joldMapping.addItemListener(new ItemListener(){
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() != ItemEvent.SELECTED || !joldMapping.isEnabled())
                    return;
                RemapperGUI.this.oldMapping = (String)joldMapping.getSelectedItem();
                updateGuiState();
            }
        });
        this.joldMapping.setEnabled(false);
        return this.joldMapping;
    }

    private JComponent createDownloadOldButton() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        this.jDownloadOld = new JButton("Download");
        this.jDownloadOld.setEnabled(false);
        this.jDownloadOld.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
                if (MappingDownloader.needsDownload(mcVersion, oldMapping, cacheDir)) {
                    setStatus("Downloading " + oldMapping + " for " + mcVersion, Color.BLACK).run();
                    MappingDownloader.download(mcVersion, oldMapping, cacheDir,
                    new Runnable(){
                        public void run(){
                            setStatus("Download Complete!", Color.BLACK).run();
                            updateGuiState();
                        }
                    });
                }
                jDownloadOld.setEnabled(false);
            }
        });

        panel.add(this.jDownloadOld, BorderLayout.EAST);
        return panel;
    }

    private JComponent createMappingsSelectionRight() {
        /*
         *     2    :      3
         * +--------+------------+
         * |CacheDir|Text And Btn|
         * +--------+------------+
         * |New:    |Select      |
         * +--------+------------+
         * |        |    Download|
         * +--------+------------+
         * |        | start remap|
         * +--------+------------+
         */
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();

        JComponent cacheDirLabel = makeLabel("Cache Dir: ");
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 2;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 0;
        panel.add(cacheDirLabel, constraints);

        JComponent cacheDirBrowse = createBrowseBox(15, new IBrowseListener() {
            @Override public File getValue() { return cacheDir; }
            @Override public void setValue(File value) { cacheDir = value; }
        });
        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 1.0;
        panel.add(cacheDirBrowse, constraints);

        JComponent newMappingsLabel = makeLabel("New:");
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 2;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 0;
        panel.add(newMappingsLabel, constraints);

        JComponent newMappingSelection = createNewMappingsSelection();
        constraints.gridx = 2;
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 1.0;
        panel.add(newMappingSelection, constraints);

        JComponent downloadButton = createDownloadButton();
        constraints.gridx = 2;
        constraints.gridy = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 0;
        panel.add(downloadButton, constraints);

        JComponent startRemapButton = createRemapButton();
        constraints.gridx = 2;
        constraints.gridy = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weighty = 0;
        constraints.weightx = 0;
        panel.add(startRemapButton, constraints);

        return panel;
    }

    private JComponent createNewMappingsSelection() {
        this.jnewMapping = new JComboBox<String>();
        this.jnewMapping.addItemListener(new ItemListener(){
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() != ItemEvent.SELECTED || !jnewMapping.isEnabled())
                    return;
                RemapperGUI.this.newMapping = (String)jnewMapping.getSelectedItem();
                updateGuiState();
            }
        });
        this.jnewMapping.setEnabled(false);

        return this.jnewMapping;
    }

    private JComponent createDownloadButton() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        this.jDownloadNew = new JButton("Download");
        this.jDownloadNew.setEnabled(false);
        this.jDownloadNew.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
                if (MappingDownloader.needsDownload(mcVersion, newMapping, cacheDir)) {
                    setStatus("Downloading " + newMapping + " for " + mcVersion, Color.BLACK).run();
                    MappingDownloader.download(mcVersion, newMapping, cacheDir,
                    new Runnable(){
                        public void run(){
                            setStatus("Download Complete!", Color.BLACK).run();
                            updateGuiState();
                        }
                    });
                }
                jDownloadNew.setEnabled(false);
            }
        });

        panel.add(this.jDownloadNew, BorderLayout.EAST);
        return panel;
    }

    private JComponent createRemapButton() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        this.btnRemapMod = new JButton("Start Remap");
        this.btnRemapMod.setEnabled(false);
        this.btnRemapMod.addActionListener(new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e) {
                RemapperTask.runRemapMod(deps, srcs, mcVersion, oldMapping, newMapping, cacheDir, new IProgressListener(){
                    @Override
                    public void writeLine(String line) {
                        setStatus(line, Color.BLACK).run();
                    }
                });
            }
        });

        panel.add(this.btnRemapMod, BorderLayout.EAST);
        return panel;
    }

    private void updateMCVersionList() {
        if (MappingDownloader.mappings.size() <= 0)
            return;
        jmcVersion.setEnabled(false);
        jmcVersion.removeAllItems();
        for (String version : MappingDownloader.mappings.keySet())
            jmcVersion.addItem(version);
        if (((DefaultComboBoxModel<String>)jmcVersion.getModel()).getIndexOf(mcVersion) == -1)
            jmcVersion.addItem(mcVersion);
        jmcVersion.setSelectedItem(mcVersion);
        jmcVersion.setEnabled(true);
    }

    private void updateOldMappings() {
        joldMapping.setEnabled(false);
        joldMapping.removeAllItems();
        joldMapping.addItem("SRG");
        if (MappingDownloader.mappings.containsKey(mcVersion)) {
            for (String mapping : MappingDownloader.mappings.get(mcVersion)) {
                joldMapping.addItem(mapping);
            }
        }
        if (((DefaultComboBoxModel<String>)joldMapping.getModel()).getIndexOf(oldMapping) == -1)
            joldMapping.addItem(oldMapping);
        joldMapping.setSelectedItem(oldMapping);
        joldMapping.setEnabled(true);
    }

    private void updateNewMappings() {
        jnewMapping.setEnabled(false);
        jnewMapping.removeAllItems();
        jnewMapping.addItem("SRG");
        if (MappingDownloader.mappings.containsKey(mcVersion)) {
            for (String mapping : MappingDownloader.mappings.get(mcVersion)) {
                if (newMapping.equals("UNLOADED"))
                    newMapping = mapping;
                jnewMapping.addItem(mapping);
            }
        }
        if (((DefaultComboBoxModel<String>)jnewMapping.getModel()).getIndexOf(newMapping) == -1)
            jnewMapping.addItem(newMapping);
        jnewMapping.setSelectedItem(newMapping);
        jnewMapping.setEnabled(true);
    }

    void updateGuiState() {
        for (Runnable r : this.changeListeners)
            r.run();
        if (!new File(targetDir, "build.gradle").exists()) {
            status.setText("build.gradle missing in taget dir!");
            status.setForeground(Color.RED);
            btnGetModInfo.setEnabled(false);
        }
        else if (this.buildFailed) {
            status.setText("gradle task failed!");
            status.setForeground(Color.RED);
            btnGetModInfo.setEnabled(false);
        }
        else {
            status.setText("build.gradle found:");
            status.setForeground(Color.YELLOW);
            btnGetModInfo.setEnabled(true);
        }
        updateMCVersionList();
        updateOldMappings();
        updateNewMappings();
        jDownloadOld.setEnabled(MappingDownloader.needsDownload(mcVersion, oldMapping, cacheDir));
        jDownloadNew.setEnabled(MappingDownloader.needsDownload(mcVersion, newMapping, cacheDir));
        btnRemapMod.setEnabled(!jDownloadOld.isEnabled() && !jDownloadNew.isEnabled() && deps.size() > 0 && srcs.size() > 0);
    }

    Runnable setStatus(final String line, final Color color) {
        return new Runnable() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(new Runnable(){
                    public void run() {
                        RemapperGUI.this.status.setText(line);
                        RemapperGUI.this.status.setForeground(color);
                    }
                });
            }
        };
    }

    private JLabel makeLabel(String text) {
        JLabel ret = new JLabel(text);
        ret.setAlignmentX(Component.TOP_ALIGNMENT);
        return ret;
    }

    private static class JLabelMax extends JLabel {
        private static final long serialVersionUID = 1L;
        public JLabelMax(String text, int horizontalAlignment) {
            super(text, horizontalAlignment);
            setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        @Override
        public Dimension getMaximumSize() {
            Dimension ret = super.getMaximumSize();
            ret.width = Integer.MAX_VALUE;
            return ret;
        }
    }

    static class ListModel<T> extends AbstractListModel<T> implements List<T> {
        private static final long serialVersionUID = -8633567737555564475L;
        private List<T> list = Lists.newArrayList();

        @Override
        public int getSize() {
            return list.size();
        }

        @Override
        public T getElementAt(int index) {
            return list.get(index);
        }

        @Override public int size() { return list.size(); }
        @Override public boolean isEmpty() { return list.isEmpty(); }
        @Override public boolean contains(Object o) { return list.contains(o); }
        @Override public Iterator<T> iterator() { return list.iterator(); }
        @Override public Object[] toArray() { return list.toArray(); }
        @Override public <R> R[] toArray(R[] a) { return list.<R>toArray(a); }
        @Override
        public boolean add(T e) {
            boolean ret = list.add(e);
            changed();
            return ret;
        }
        @Override
        public boolean remove(Object o) {
            boolean ret = list.remove(o);
            changed();
            return ret;
        }
        @Override public boolean containsAll(Collection<?> c) { return list.containsAll(c); }
        @Override
        public boolean addAll(Collection<? extends T> c) {
            boolean ret = list.addAll(c);
            changed();
            return ret;
        }

        @Override
        public boolean addAll(int index, Collection<? extends T> c) {
            boolean ret = list.addAll(index, c);
            changed();
            return ret;
        }
        @Override
        public boolean removeAll(Collection<?> c) {
            boolean ret = list.removeAll(c);
            changed();
            return ret;
        }
        @Override
        public boolean retainAll(Collection<?> c) {
            boolean ret = list.retainAll(c);
            changed();
            return ret;
        }
        @Override
        public void clear() {
            list.clear();
            changed();
        }
        @Override public T get(int index) { return list.get(index); }
        @Override
        public T set(int index, T element) {
            T ret = list.set(index, element);
            changed();
            return ret;
        }
        @Override public void add(int index, T element) { list.add(index, element); }
        @Override public T remove(int index) {
            T ret = list.remove(index);
            changed();
            return ret;
        }
        @Override public int indexOf(Object o) { return list.indexOf(o); }
        @Override public int lastIndexOf(Object o) { return list.lastIndexOf(o); }
        @Override public ListIterator<T> listIterator() { return list.listIterator(); }
        @Override public ListIterator<T> listIterator(int index) { return list.listIterator(index); }
        @Override public List<T> subList(int fromIndex, int toIndex) { return list.subList(fromIndex, toIndex); }
        private void changed(){ this.fireContentsChanged(this, 0, list.size()); }
    }

    private static class FileListRenderer implements ListCellRenderer<File> {
        private JLabel label = new JLabelMax("", SwingConstants.LEFT);

        @Override
        public Component getListCellRendererComponent(JList<? extends File> list, File value, int index, boolean isSelected, boolean cellHasFocus)
        {
            label.setText(value.getName());
            return label;
        }
    }

    private static abstract class DocChangeListener implements DocumentListener {
        @SuppressWarnings("unused")
        private JTextField field;
        public DocChangeListener(JTextField field) {
            this.field = field;
        }
        @Override public void insertUpdate(DocumentEvent e) { update(); }
        @Override public void removeUpdate(DocumentEvent e) { update(); }
        @Override public void changedUpdate(DocumentEvent e) { }
        protected abstract void update();
    }

    private static interface IBrowseListener {
        File getValue();
        void setValue(File value);
    }

    private JComponent createBrowseBox(int columns, final IBrowseListener listener) {
        final JTextField text = new JTextField();
        text.getDocument().addDocumentListener(new DocChangeListener(text) {
            @Override
            protected void update() {
                File f = text.getText() == null ? new File(".")  : new File(text.getText());
                if (f.exists() && !listener.getValue().equals(f)) {
                    listener.setValue(f);
                    updateGuiState();
                }
            }
        });
        JButton browse = new JButton("...");
        browse.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setFileHidingEnabled(false);
                chooser.ensureFileIsVisible(targetDir);
                chooser.setSelectedFile(targetDir);
                int response = chooser.showOpenDialog(RemapperGUI.this.mainFrame);
                switch (response) {
                    case JFileChooser.APPROVE_OPTION:
                        File t = chooser.getSelectedFile();
                        if (!listener.getValue().equals(t)) {
                            listener.setValue(t);
                            updateGuiState();
                        }
                        break;
                    default:
                        break;
                }

            }
        });
        this.changeListeners.add(new Runnable(){
            @Override
            public void run() {
                if (!listener.getValue().toString().equals(text.getText()))
                    text.setText(listener.getValue().toString());

            }
        });
        Box ret = Box.createHorizontalBox();
        ret.add(text);
        ret.add(browse);
        return ret;
    }
}
