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
    private final JButton directoryChooserButton;
    private final JButton runButton;
    private final JFrame frame;
    private final File forbiddenWordsFile;
    private final File historyFile;

    public FileRenamer() {
        frame = new JFrame("T.F.N.C. - Torrent File Name Cleaner");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 300);

        directoryField = new JTextField(20); // Ustawiamy szerokość pola tekstowego
        directoryField.setToolTipText("Wpisz ścieżkę do katalogu tutaj lub wybierz za pomocą przycisku");
        directoryChooserButton = new JButton("Wybierz katalog");

        Dimension buttonSize = directoryChooserButton.getPreferredSize();
        directoryChooserButton.setPreferredSize(new Dimension(buttonSize.width / 2, buttonSize.height));

        forbiddenWordsArea = new JTextArea();
        forbiddenWordsArea.setLineWrap(true);
        forbiddenWordsArea.setWrapStyleWord(true);
        forbiddenWordsArea.setToolTipText("Wpisz zakazane słowa tutaj, oddzielając je spacjami");

        runButton = new JButton("Uruchom");

        forbiddenWordsFile = new File("blacklist.txt");
        historyFile = new File("history.txt");

        // Dodajemy przykładowe słowa do forbiddenWordsArea
        forbiddenWordsArea.setText("[xtorrenty.org] [Ex-torrenty.org] [DEVIL-TORRENTS.PL] [POLSKIE-TORRENTY.EU] [superseed.byethost7.com] [Devil-Site.PL] [BEST-TORRENTS.ORG]");

        JPanel directoryPanel = new JPanel(new FlowLayout());
        directoryPanel.add(directoryField);
        directoryPanel.add(Box.createVerticalStrut(10)); // Dodajemy pionowy odstęp
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
                LOGGER.severe("Wystapił błąd: " + ex.getMessage());
            }
        });

        directoryChooserButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File("."));
            chooser.setDialogTitle("Wybierz katalog");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);

            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                directoryField.setText(chooser.getSelectedFile().toString());
            }
        });

        frame.setLayout(new BorderLayout());
        frame.add(new JLabel("Katalog:"), BorderLayout.NORTH);
        frame.add(directoryPanel, BorderLayout.NORTH);
        frame.add(directoryChooserButton, BorderLayout.NORTH);
        frame.add(new JLabel("Zakazane słowa:"), BorderLayout.CENTER);
        frame.add(forbiddenWordsArea, BorderLayout.CENTER);
        frame.add(runButton, BorderLayout.SOUTH);

        frame.setVisible(true);
        frame.revalidate();
        frame.repaint();
    }

    public void renameFilesAndDirectoriesInDirectory(String directory, List<String> forbiddenWords) throws IOException {
        // Zmiana nazw plików
        Path start = Paths.get(directory);
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> renameIfNecessary(path, forbiddenWords));
        }
        LOGGER.info("Zmieniono nazwy plików...");

        // Zmiana nazw katalogów
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getNameCount).reversed())
                    .forEach(path -> renameIfNecessary(path, forbiddenWords));
        }
        LOGGER.info("Zmieniono nazwy katalogów...");
    }

    private void renameIfNecessary(Path path, List<String> forbiddenWords) {
        String name = path.getFileName().toString();
        for (String word : forbiddenWords) {
            String pattern = "(?i)" + Pattern.quote(word);
            if (Pattern.compile(pattern).matcher(name).find()) {
                String newName = name.replaceAll(pattern, "").trim();
                try {
                    Files.move(path, path.resolveSibling(newName));
                    try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                        writer.println("Zmieniono nazwę pliku/katalogu: " + name + " na " + newName);
                        LOGGER.severe("Zmieniono nazwę pliku/katalogu: " + name + " na " + newName);
                    }
                } catch (IOException e) {
                    LOGGER.severe("Wystapił błąd: " + e.getMessage());
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
