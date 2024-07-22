package pl.syntaxerr;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FileRenamer {
    private static final Logger LOGGER = Logger.getLogger(FileRenamer.class.getName());
    private final JTextField directoryField;
    private final JTextArea forbiddenWordsArea;
    private final JFrame frame;
    private final File forbiddenWordsFile;
    private final File historyFile;

    public FileRenamer() {
        frame = new JFrame("T.F.N.C. - Torrent File Name Cleaner");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 300);

        directoryField = new JTextField(20); // Set the width of the text field
        directoryField.setToolTipText("Enter the directory path here or select using the button");
        JButton directoryChooserButton = new JButton("Select directory");

        Dimension buttonSize = directoryChooserButton.getPreferredSize();
        directoryChooserButton.setPreferredSize(new Dimension(buttonSize.width / 2, buttonSize.height));

        forbiddenWordsArea = new JTextArea();
        forbiddenWordsArea.setLineWrap(true);
        forbiddenWordsArea.setWrapStyleWord(true);
        forbiddenWordsArea.setToolTipText("Enter forbidden words here, separating them with spaces");

        JButton runButton = new JButton("Run");

        forbiddenWordsFile = new File("blacklist.txt");
        historyFile = new File("history.txt");

        // Add example words to forbiddenWordsArea
        forbiddenWordsArea.setText("[xtorrenty.org] [Ex-torrenty.org] [DEVIL-TORRENTS.PL] [POLSKIE-TORRENTY.EU] [superseed.byethost7.com] [Devil-Site.PL] [BEST-TORRENTS.ORG] [Feniks-site.com.pl] [helltorrents.com]");

        JPanel directoryPanel = new JPanel(new FlowLayout());
        directoryPanel.add(directoryField);
        directoryPanel.add(Box.createVerticalStrut(10)); // Add vertical spacing
        directoryPanel.add(directoryChooserButton);

        runButton.addActionListener(e -> {
            String directory = directoryField.getText();
            List<String> forbiddenWords = Arrays.asList(forbiddenWordsArea.getText().split("\\s+"));
            try {
                renameFilesAndDirectoriesInDirectory(directory, forbiddenWords);
                try (PrintWriter writer = new PrintWriter(new FileWriter(forbiddenWordsFile))) {
                    for (String word : forbiddenWords) {
                        writer.println(word);
                    }
                }
                LOGGER.info("Finished renaming all files and directories.");
            } catch (IOException ex) {
                LOGGER.severe("An error occurred: " + ex.getMessage());
            }
        });

        directoryChooserButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File("."));
            chooser.setDialogTitle("Select directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                directoryField.setText(chooser.getSelectedFile().toString());
            }
        });

        frame.setLayout(new BorderLayout());
        frame.add(new JLabel("Directory:"), BorderLayout.NORTH);
        frame.add(directoryPanel, BorderLayout.NORTH);
        frame.add(directoryChooserButton, BorderLayout.NORTH);
        frame.add(new JLabel("Forbidden words:"), BorderLayout.CENTER);
        frame.add(forbiddenWordsArea, BorderLayout.CENTER);
        frame.add(runButton, BorderLayout.SOUTH);
        frame.setVisible(true);
        frame.revalidate();
        frame.repaint();
    }
    public void renameFilesAndDirectoriesInDirectory(String directory, List<String> forbiddenWords) throws IOException {
        // Rename files
        Path start = Paths.get(directory);
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            renameIfNecessary(path, forbiddenWords);
                        } catch (IOException e) {
                            LOGGER.severe("An error occurred: " + e.getMessage());
                        }
                    });
        }
        LOGGER.info("File names changed...");

        // Rename directories
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getNameCount).reversed())
                    .forEach(path -> {
                        try {
                            renameIfNecessary(path, forbiddenWords);
                        } catch (IOException e) {
                            LOGGER.severe("An error occurred: " + e.getMessage());
                        }
                    });
        }
        LOGGER.info("Directory names changed...");
    }
    private void renameIfNecessary(Path path, List<String> forbiddenWords) throws IOException {
        String name = path.getFileName().toString();
        for (String word : forbiddenWords) {
            String pattern = "(?i)" + Pattern.quote(word);
            if (Pattern.compile(pattern).matcher(name).find()) {
                String newName = name.replaceAll(pattern, "").trim();
                try {
                    if (!Files.isWritable(path)) {
                        LOGGER.severe("File is read-only: " + path);
                        try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                            writer.println("File is read-only: " + path);
                        }
                        return;
                    }
                    Files.move(path, path.resolveSibling(newName));
                    LOGGER.severe("Changed file/directory name: " + name + " to " + newName);
                    try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                        writer.println("Changed file/directory name: " + name + " to " + newName);
                    }
                } catch (AccessDeniedException e) {
                    LOGGER.severe("No permission to rename: " + path);
                    try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                        writer.println("No permission to rename: " + path);
                    }
                } catch (FileSystemException e) {
                    LOGGER.severe("File system error during renaming: " + path);
                    try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                        writer.println("File system error during renaming: " + path);
                    }
                } catch (IOException e) {
                    LOGGER.severe("An error occurred: " + e.getMessage());
                    try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                        writer.println("An error occurred: " + e.getMessage());
                    }
                }
                break;
            }
        }
    }


    public static void main(String[] args)
    {
        new FileRenamer();
    }
}
