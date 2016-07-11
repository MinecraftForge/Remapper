package net.minecraftforge.remapper;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.google.common.collect.Lists;

import net.minecraftforge.remapper.RemapperTask.IProgressListener;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class RemapperGUI {
    static boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
    private JFrame mainFrame;
    private JLabel status;
    private JButton btnGetModInfo;
    File targetDir = new File(".");
    ListModel<File> deps = new ListModel<File>();
    ListModel<File> srcs = new ListModel<File>();

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

        //==================================================
        // Folder select
        JPanel folderSelect = new JPanel();
        folderSelect.setLayout(new FlowLayout());
        folderSelect.add(new JLabel("Project Folder:"));
        folderSelect.add(createBrowseBox(25, new IBrowseListener() {
            @Override public File getValue() { return targetDir; }
            @Override public void setValue(File value) { targetDir = value; }
        }));
        folderSelect.setMaximumSize(folderSelect.getPreferredSize());
        folderSelect.setMinimumSize(folderSelect.getPreferredSize());
        //=====================================================
        status = new JLabelMax("Select Folder!", SwingConstants.CENTER);
        status.setOpaque(true);
        //=====================================================
        JPanel buttonList = new JPanel();
        folderSelect.setLayout(new FlowLayout());
        btnGetModInfo = new JButton("Load Info");
        btnGetModInfo.setEnabled(false);
        btnGetModInfo.setToolTipText("Load information from a build.gradle");
        btnGetModInfo.addActionListener(new GatherModInfo(this));
        buttonList.add(btnGetModInfo);
        buttonList.setMaximumSize(buttonList.getPreferredSize());
        buttonList.setMinimumSize(buttonList.getPreferredSize());
        //=====================================================
        JList<File> depList = new JList<File>(this.deps);
        depList.setCellRenderer(new FileListRenderer());
        depList.setSize(40, 50);
        Box box = Box.createHorizontalBox();
        box.add(new JLabelMax("Dependancies:", JLabel.LEADING));
        box.add(Box.createHorizontalGlue());
        Box depBox = Box.createVerticalBox();
        depBox.add(box);
        depBox.add(new JScrollPane(depList));
        depBox.setMinimumSize(new Dimension(225, 200));
        depBox.setMaximumSize(depBox.getMinimumSize());

        JList<File> srcList = new JList<File>(this.srcs);
        srcList.setCellRenderer(new FileListRenderer());
        box = Box.createHorizontalBox();
        box.add(new JLabelMax("Sources:", JLabel.LEADING));
        box.add(Box.createHorizontalGlue());
        Box srcBox = Box.createVerticalBox();
        srcBox.add(box);
        srcBox.add(new JScrollPane(srcList));
        srcBox.setMinimumSize(new Dimension(225, 200));
        srcBox.setMaximumSize(srcBox.getMinimumSize());

        Box lists = Box.createHorizontalBox();
        lists.add(depBox);
        lists.add(Box.createRigidArea(new Dimension(10, 0)));
        lists.add(srcBox);
        //=====================================================
        Box[] left = new Box[]{Box.createVerticalBox(), Box.createVerticalBox()};
        left[0].add(makeLabel("Minecraft: "));
        left[0].add(Box.createRigidArea(new Dimension(10, 10))); //Annoying but needed cuz labels are smaller then text/lists
        left[1].add(this.jmcVersion = new JComboBox<String>());
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
        left[0].add(makeLabel("Old: "));
        left[1].add(this.joldMapping = new JComboBox<String>());
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
        left[0].add(Box.createGlue());
        left[1].add(this.jDownloadOld = new JButton("Download"));
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

        Box[] right = new Box[]{Box.createVerticalBox(), Box.createVerticalBox()};
        right[0].add(makeLabel("Cache Dir: "));
        right[0].add(Box.createRigidArea(new Dimension(10, 10))); //Annoying but needed cuz labels are smaller then text/lists
        right[1].add(createBrowseBox(15, new IBrowseListener() {
            @Override public File getValue() { return cacheDir; }
            @Override public void setValue(File value) { cacheDir = value; }
        }));
        right[0].add(makeLabel("New:"));
        right[1].add(this.jnewMapping = new JComboBox<String>());
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
        right[0].add(Box.createGlue());
        right[1].add(createHorizontalBox(Box.createGlue(), this.jDownloadNew = new JButton("Download")));
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
        //=====================================================
        right[1].add(createHorizontalBox(Box.createGlue(), this.btnRemapMod = new JButton("Start Remap")));
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

        //=====================================================

        Box infoBox = Box.createHorizontalBox();
        Box t = createHorizontalBox(left[0], left[1]);
        t.setMaximumSize(new Dimension(225, t.getPreferredSize().height));
        infoBox.add(t);
        infoBox.add(Box.createRigidArea(new Dimension(12, 10)));
        t = createHorizontalBox(right[0], right[1]);
        t.setMaximumSize(new Dimension(225, t.getPreferredSize().height));
        infoBox.add(t);
        //=====================================================


        Box mainPanel = Box.createVerticalBox();
        mainPanel.add(folderSelect);
        mainPanel.add(status);
        mainPanel.add(buttonList);
        mainPanel.add(lists);
        mainPanel.add(infoBox);


        mainFrame.getContentPane().add(mainPanel, BorderLayout.CENTER);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setMinimumSize(new Dimension(mainFrame.getWidth(), mainFrame.getHeight()));
        mainFrame.setVisible(true);

        MappingDownloader.downloadMappingList(new Runnable(){
            @Override
            public void run() {
                updateGuiState();
            }
        });
    }

    private Box createHorizontalBox(Component... parts) {
        Box ret = Box.createHorizontalBox();
        for(Component c : parts)
            ret.add(c);
        return ret;
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
        final JTextField text = new JTextField(columns);
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
