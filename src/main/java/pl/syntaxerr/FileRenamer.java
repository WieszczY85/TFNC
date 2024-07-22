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
    private final JProgressBar progressBar;
    private final JLabel statusLabel;

    public FileRenamer() {
        frame = new JFrame("T.F.N.C. - Torrent File Name Cleaner");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        progressBar = new JProgressBar();
        statusLabel = new JLabel("Ready...");

        directoryField = new JTextField(20); // Set the width of the text field
        directoryField.setToolTipText("Enter the directory path here or select using the button");
        JButton directoryChooserButton = new JButton("Select directory");

        forbiddenWordsArea = new JTextArea();
        forbiddenWordsArea.setLineWrap(true);
        forbiddenWordsArea.setWrapStyleWord(true);
        forbiddenWordsArea.setToolTipText("Enter forbidden words here, separating them with spaces");

        JButton runButton = new JButton("Run");

        forbiddenWordsFile = new File("blacklist.txt");
        historyFile = new File("history.txt");

        forbiddenWordsArea.setText("[xtorrenty.org] [Ex-torrenty.org] [DEVIL-TORRENTS.PL] [POLSKIE-TORRENTY.EU] [superseed.byethost7.com] [Devil-Site.PL] [BEST-TORRENTS.ORG] [Feniks-site.com.pl] [helltorrents.com] [electro-torrent.pl]");

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

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(new JLabel("Directory:"), BorderLayout.NORTH);
        northPanel.add(directoryPanel, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JLabel("Forbidden words:"), BorderLayout.NORTH);
        centerPanel.add(forbiddenWordsArea, BorderLayout.CENTER);

        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.SOUTH);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(progressPanel, BorderLayout.NORTH); // Add the progress panel here
        southPanel.add(runButton, BorderLayout.SOUTH);

        frame.add(northPanel, BorderLayout.NORTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(southPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
        frame.revalidate();
        frame.repaint();

    }

    public void renameFilesAndDirectoriesInDirectory(String directory, List<String> forbiddenWords) {
        SwingUtilities.invokeLater(() -> progressBar.setIndeterminate(true));
        statusLabel.setText("Renaming files and directories...");
        Path start = Paths.get(directory);
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> renameIfNecessary(path, forbiddenWords));
        } catch (IOException exx) {
            LOGGER.severe("An error occurred: " + exx.getMessage());
        }
        LOGGER.info("File names changed...");
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getNameCount).reversed())
                    .forEach(path -> renameIfNecessary(path, forbiddenWords));
        } catch (IOException exxx) {
            LOGGER.severe("An error occurred: " + exxx.getMessage());
        }
        LOGGER.info("Directory names changed...");
        LOGGER.info("Finished renaming all files and directories.");
        SwingUtilities.invokeLater(() -> progressBar.setIndeterminate(false));
        statusLabel.setText("Finished renaming all files and directories");
    }

    private void renameIfNecessary(Path path, List<String> forbiddenWords) {
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
                    } catch (IOException epath) {
                        LOGGER.severe("An error occurred: " + epath.getMessage());
                    }
                } catch (AccessDeniedException e) {
                    LOGGER.severe("No permission to rename: " + path);
                    try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                        writer.println("No permission to rename: " + path);
                    } catch (IOException eAccess) {
                        LOGGER.severe("An error occurred: " + eAccess.getMessage());
                    }
                } catch (FileSystemException e) {
                    LOGGER.severe("File system error during renaming: " + path);
                    try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                        writer.println("File system error during renaming: " + path);
                    } catch (IOException eFileSystem) {
                        LOGGER.severe("An error occurred: " + eFileSystem.getMessage());
                    }
                } catch (IOException e) {
                    LOGGER.severe("An error occurred: " + e.getMessage());
                    try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                        writer.println("An error occurred: " + e.getMessage());
                    } catch (IOException eIOE) {
                        LOGGER.severe("An error occurred: " + eIOE.getMessage());
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
