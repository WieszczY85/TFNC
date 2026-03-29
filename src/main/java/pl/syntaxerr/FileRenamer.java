package pl.syntaxerr;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class FileRenamer {
    private static final String HISTORY_PREFIX = "Changed file/directory name: ";
    private static final String HISTORY_SEPARATOR = " to ";
    private static final String STRUCTURED_HISTORY_PREFIX = "RENAMED\t";
    private static final Logger LOGGER = Logger.getLogger(FileRenamer.class.getName());
    private static final List<String> DEFAULT_FORBIDDEN_WORDS = List.of(
            "[xtorrenty.org]", "[Ex-torrenty.org]", "[DEVIL-TORRENTS.PL]", "[POLSKIE-TORRENTY.EU]", "[superseed.byethost7.com]",
            "[Devil-Site.PL]", "[BEST-TORRENTS.ORG]", "[Feniks-site.com.pl]", "[helltorrents.com]", "[electro-torrent.pl]",
            "[rarbg.to]", "[1337x.to]", "[torrentgalaxy.to]", "[yts.mx]", "[thepiratebay.org]", "[eztv.re]",
            "[katcr.co]", "[limetorrents.lol]", "[nyaa.si]", "[zooqle.com]", "[torlock.com]", "[torrentdownloads.me]"
    );
    private final JTextField directoryField;
    private final JTextArea forbiddenWordsArea;
    private final JFrame frame;
    private final File forbiddenWordsFile;
    private final File historyFile;
    private final File errorFile;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final boolean headlessMode;

    public FileRenamer() {
        this(false);
    }

    public FileRenamer(boolean headlessMode) {
        this.headlessMode = headlessMode;
        forbiddenWordsFile = new File("blacklist.txt");
        historyFile = new File("history.txt");
        errorFile = new File("error.txt");
        ensureAppFilesExist();

        if (headlessMode) {
            frame = null;
            progressBar = null;
            statusLabel = null;
            directoryField = null;
            forbiddenWordsArea = null;
            return;
        }

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
        forbiddenWordsArea.setToolTipText("Enter forbidden words here, one per line");

        JButton runButton = new JButton("Run");
        JButton undoButton = new JButton("Undo from history");

        forbiddenWordsArea.setText(String.join(System.lineSeparator(), readForbiddenWordsFromFile()));

        JPanel directoryPanel = new JPanel(new FlowLayout());
        directoryPanel.add(directoryField);
        directoryPanel.add(Box.createVerticalStrut(10)); // Add vertical spacing
        directoryPanel.add(directoryChooserButton);

        runButton.addActionListener(e -> {
            String directory = directoryField.getText();
            List<String> forbiddenWords = parseForbiddenWords(forbiddenWordsArea.getText());
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

        undoButton.addActionListener(e -> {
            String directory = directoryField.getText();
            if (directory == null || directory.isBlank()) {
                setStatus("Podaj katalog do cofania zmian.");
                LOGGER.severe("Brak katalogu dla cofania zmian.");
                return;
            }
            undoRenamesFromHistory(directory);
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

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonsPanel.add(runButton);
        buttonsPanel.add(undoButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(progressPanel, BorderLayout.NORTH); // Add the progress panel here
        southPanel.add(buttonsPanel, BorderLayout.SOUTH);

        frame.add(northPanel, BorderLayout.NORTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(southPanel, BorderLayout.SOUTH);
        frame.setVisible(true);
        frame.revalidate();
        frame.repaint();
    }

    private void ensureAppFilesExist() {
        try {
            if (!forbiddenWordsFile.exists()) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(forbiddenWordsFile))) {
                    for (String word : DEFAULT_FORBIDDEN_WORDS) {
                        writer.println(word);
                    }
                }
            }

            if (!historyFile.exists() && !historyFile.createNewFile()) {
                LOGGER.warning("Nie udało się utworzyć pliku historii: " + historyFile.getAbsolutePath());
            }

            if (!errorFile.exists() && !errorFile.createNewFile()) {
                LOGGER.warning("Nie udało się utworzyć pliku błędów: " + errorFile.getAbsolutePath());
            }
        } catch (IOException ex) {
            LOGGER.severe("Nie udało się przygotować plików aplikacji: " + ex.getMessage());
        }
    }

    private List<String> readForbiddenWordsFromFile() {
        if (!forbiddenWordsFile.exists()) {
            return new ArrayList<>(DEFAULT_FORBIDDEN_WORDS);
        }

        try {
            List<String> words = Files.readAllLines(forbiddenWordsFile.toPath()).stream()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .toList();
            if (!words.isEmpty()) {
                return words;
            }
        } catch (IOException ex) {
            LOGGER.severe("Nie udało się odczytać blacklist.txt: " + ex.getMessage());
        }
        return new ArrayList<>(DEFAULT_FORBIDDEN_WORDS);
    }

    public void renameFilesAndDirectoriesInDirectory(String directory, List<String> forbiddenWords) {
        setProgressIndeterminate(true);
        setStatus("Renaming files and directories... Please wait!");

        Path start = Paths.get(directory);
        try {
            processFiles(start, forbiddenWords);
        } catch (IOException ex) {
            LOGGER.severe("An error occurred while walking through files: " + ex.getMessage());
            setStatus("Error walking through files.");
        }
        LOGGER.info("File names changed...");

        try {
            processDirectories(start, forbiddenWords);
        } catch (IOException ex) {
            LOGGER.severe("An error occurred while walking through directories: " + ex.getMessage());
            setStatus("Error walking through directories.");
        }
        LOGGER.info("Directory names changed...");
        LOGGER.info("Finished renaming all files and directories.");
        setProgressIndeterminate(false);
        setStatus("Finished renaming all files and directories");
    }

    private void setStatus(String text) {
        if (headlessMode || statusLabel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void setProgressIndeterminate(boolean indeterminate) {
        if (headlessMode || progressBar == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> progressBar.setIndeterminate(indeterminate));
    }

    private void processFiles(Path start, List<String> forbiddenWords) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                renameIfNecessary(file, forbiddenWords);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                handleVisitFailure(file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void processDirectories(Path start, List<String> forbiddenWords) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) {
                    handleVisitFailure(dir, exc);
                } else {
                    renameIfNecessary(dir, forbiddenWords);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                handleVisitFailure(file, exc);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void handleVisitFailure(Path path, IOException exc) {
        String message = "Failed to access: " + path + " (" + exc.getMessage() + ")";
        LOGGER.severe(message);
        try (PrintWriter writer = new PrintWriter(new FileWriter(errorFile, true))) {
            writer.println(message);
        } catch (IOException errorFileException) {
            LOGGER.severe("An error occurred: " + errorFileException.getMessage());
        }
        setStatus("Error accessing " + path);
    }

    private void renameIfNecessary(Path path, List<String> forbiddenWords) {
        String name = path.getFileName().toString();
        String newName = name;
        boolean changed = false;

        for (String word : forbiddenWords) {
            if (word == null || word.isBlank()) {
                continue;
            }

            String pattern = "(?i)" + Pattern.quote(word);
            if (Pattern.compile(pattern).matcher(newName).find()) {
                newName = newName.replaceAll(pattern, "");
                changed = true;
            }
        }

        if (changed) {
            newName = newName.trim();

            if (newName.isBlank()) {
                String message = "Pominięto zmianę, bo nowa nazwa byłaby pusta: " + path;
                LOGGER.severe(message);
                try (PrintWriter writer = new PrintWriter(new FileWriter(errorFile, true))) {
                    writer.println(message);
                } catch (IOException eBlank) {
                    LOGGER.severe("An error occurred: " + eBlank.getMessage());
                }
                return;
            }

            try {
                if (!Files.isWritable(path)) {
                    LOGGER.severe("File is read-only: " + path);
                    try (PrintWriter writer = new PrintWriter(new FileWriter(errorFile, true))) {
                        writer.println("File is read-only: " + path);
                    }
                    return;
                }
                Files.move(path, path.resolveSibling(newName));
                LOGGER.severe("Changed file/directory name: " + name + " to " + newName);
                try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                    writer.println(HISTORY_PREFIX + name + HISTORY_SEPARATOR + newName);
                    writer.println(STRUCTURED_HISTORY_PREFIX + path.toAbsolutePath() + "\t" + path.resolveSibling(newName).toAbsolutePath());
                } catch (IOException epath) {
                    LOGGER.severe("An error occurred: " + epath.getMessage());
                }
            } catch (AccessDeniedException e) {
                LOGGER.severe("No permission to rename: " + path);
                try (PrintWriter writer = new PrintWriter(new FileWriter(errorFile, true))) {
                    writer.println("No permission to rename: " + path);
                } catch (IOException eAccess) {
                    LOGGER.severe("An error occurred: " + eAccess.getMessage());
                }
                setStatus("Error: No permission to rename " + path);
            } catch (FileSystemException e) {
                LOGGER.severe("File system error during renaming: " + path);
                try (PrintWriter writer = new PrintWriter(new FileWriter(errorFile, true))) {
                    writer.println("File system error during renaming: " + path);
                } catch (IOException eFileSystem) {
                    LOGGER.severe("An error occurred: " + eFileSystem.getMessage());
                }
                setStatus("Error: File system error during renaming " + path);
            } catch (IOException e) {
                LOGGER.severe("An error occurred: " + e.getMessage());
                try (PrintWriter writer = new PrintWriter(new FileWriter(errorFile, true))) {
                    writer.println("An error occurred: " + e.getMessage());
                } catch (IOException eIOE) {
                    LOGGER.severe("An error occurred: " + eIOE.getMessage());
                }
                setStatus("Error: An error occurred during renaming " + path);
            }
        }
    }

    static List<String> parseForbiddenWords(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        List<String> words = new ArrayList<>();
        for (String line : input.split("\\R")) {
            if (line == null) {
                continue;
            }

            if (line.trim().isEmpty()) {
                continue;
            }

            String value = line;
            if (line.length() >= 2 && line.startsWith("\"") && line.endsWith("\"")) {
                value = line.substring(1, line.length() - 1);
            }

            words.add(value);
        }

        return words;
    }

    private static void runCli(List<String> args) {
        if (args.isEmpty()) {
            LOGGER.severe("Tryb CLI wymaga argumentów: <katalog> [dodatkowe-zakazane-słowo-1] [dodatkowe-zakazane-słowo-2] ...");
            System.exit(1);
            return;
        }

        String directory = args.get(0);
        FileRenamer renamer = new FileRenamer(true);

        LinkedHashSet<String> mergedForbiddenWords = new LinkedHashSet<>(renamer.readForbiddenWordsFromFile());
        mergedForbiddenWords.addAll(args.subList(1, args.size()));

        if (mergedForbiddenWords.isEmpty()) {
            LOGGER.severe("Brak zakazanych słów. Uzupełnij blacklist.txt lub podaj dodatkowe słowa w CLI.");
            System.exit(1);
            return;
        }

        renamer.renameFilesAndDirectoriesInDirectory(directory, new ArrayList<>(mergedForbiddenWords));
    }

    private record HistoryEntry(String oldName, String newName, Path oldPath, Path newPath) {
        boolean hasAbsolutePaths() {
            return oldPath != null && newPath != null;
        }
    }

    private List<HistoryEntry> readHistoryEntries() {
        if (!historyFile.exists()) {
            return List.of();
        }

        List<HistoryEntry> entries = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(historyFile.toPath())) {
                if (line == null || line.isBlank()) {
                    continue;
                }

                if (line.startsWith(STRUCTURED_HISTORY_PREFIX)) {
                    String payload = line.substring(STRUCTURED_HISTORY_PREFIX.length());
                    String[] parts = payload.split("\t", 2);
                    if (parts.length == 2) {
                        Path oldPath = Path.of(parts[0]);
                        Path newPath = Path.of(parts[1]);
                        entries.add(new HistoryEntry(oldPath.getFileName().toString(), newPath.getFileName().toString(), oldPath, newPath));
                    }
                    continue;
                }

                if (!line.startsWith(HISTORY_PREFIX)) {
                    continue;
                }

                String payload = line.substring(HISTORY_PREFIX.length());
                int separator = payload.lastIndexOf(HISTORY_SEPARATOR);
                if (separator <= 0 || separator + HISTORY_SEPARATOR.length() >= payload.length()) {
                    continue;
                }

                String oldName = payload.substring(0, separator);
                String newName = payload.substring(separator + HISTORY_SEPARATOR.length());
                entries.add(new HistoryEntry(oldName, newName, null, null));
            }
        } catch (IOException ex) {
            LOGGER.severe("Nie udało się odczytać historii zmian: " + ex.getMessage());
        }

        return entries;
    }

    void undoRenamesFromHistory(String directory) {
        setProgressIndeterminate(true);
        setStatus("Cofanie zmian nazw... Proszę czekać.");
        Path root = Paths.get(directory);
        if (!Files.isDirectory(root)) {
            LOGGER.severe("Podana ścieżka nie jest katalogiem: " + directory);
            setProgressIndeterminate(false);
            setStatus("Błędny katalog: " + directory);
            return;
        }

        List<HistoryEntry> entries = readHistoryEntries();
        if (entries.isEmpty()) {
            LOGGER.severe("Brak wpisów historii do cofnięcia.");
            setProgressIndeterminate(false);
            setStatus("Brak wpisów historii.");
            return;
        }

        int reverted = 0;
        int skipped = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            HistoryEntry entry = entries.get(i);
            boolean success = entry.hasAbsolutePaths()
                    ? revertByAbsolutePath(entry)
                    : revertByName(root, entry.oldName, entry.newName);
            if (success) {
                reverted++;
            } else {
                skipped++;
            }
        }

        LOGGER.info("Cofanie zmian zakończone. Przywrócone=" + reverted + ", pominięte=" + skipped);
        setProgressIndeterminate(false);
        setStatus("Cofanie zakończone. Przywrócone=" + reverted + ", pominięte=" + skipped);
    }

    private boolean revertByAbsolutePath(HistoryEntry entry) {
        if (!Files.exists(entry.newPath)) {
            return false;
        }

        if (Files.exists(entry.oldPath)) {
            LOGGER.severe("Pominięto cofnięcie (cel już istnieje): " + entry.oldPath);
            return false;
        }

        try {
            Files.move(entry.newPath, entry.oldPath);
            LOGGER.info("Przywrócono nazwę: " + entry.newPath + " -> " + entry.oldPath);
            return true;
        } catch (IOException ex) {
            LOGGER.severe("Nie udało się cofnąć zmiany dla: " + entry.newPath + " (" + ex.getMessage() + ")");
            return false;
        }
    }

    private boolean revertByName(Path root, String oldName, String newName) {
        List<Path> matches = findPathsByName(root, newName);

        if (matches.size() != 1) {
            LOGGER.severe("Pominięto cofnięcie dla '" + newName + "' (liczba dopasowań=" + matches.size() + ")");
            return false;
        }

        Path current = matches.getFirst();
        Path target = current.resolveSibling(oldName);
        if (Files.exists(target)) {
            LOGGER.severe("Pominięto cofnięcie, bo istnieje już: " + target);
            return false;
        }

        try {
            Files.move(current, target);
            LOGGER.info("Przywrócono nazwę: " + current + " -> " + target);
            return true;
        } catch (IOException ex) {
            LOGGER.severe("Nie udało się cofnąć zmiany dla: " + current + " (" + ex.getMessage() + ")");
            return false;
        }
    }

    private List<Path> findPathsByName(Path root, String nameToFind) {
        List<Path> matches = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.getFileName() != null && dir.getFileName().toString().equals(nameToFind)) {
                        matches.add(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName() != null && file.getFileName().toString().equals(nameToFind)) {
                        matches.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    handleVisitFailure(file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            LOGGER.severe("Nie udało się przeszukać katalogu do cofania zmian: " + ex.getMessage());
        }
        return matches;
    }

    private static void logGuiTroubleshooting(Throwable ex) {
        String display = System.getenv("DISPLAY");
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
        String xdgRuntimeDir = System.getenv("XDG_RUNTIME_DIR");
        String xauthority = System.getenv("XAUTHORITY");
        String javaHeadless = System.getProperty("java.awt.headless");
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String javaRuntime = System.getProperty("java.runtime.name");
        String sudoUser = System.getenv("SUDO_USER");
        String currentUser = System.getProperty("user.name");

        LOGGER.severe("Brak dostępu do sesji graficznej AWT/Swing.");
        LOGGER.severe("Przyczyna: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        LOGGER.severe("Użytkownik procesu=" + currentUser + ", SUDO_USER=" + sudoUser);
        LOGGER.severe("JVM=" + javaRuntime + " " + javaVersion + " (" + javaVendor + "), java.awt.headless=" + javaHeadless);
        LOGGER.severe("DISPLAY=" + display + ", WAYLAND_DISPLAY=" + waylandDisplay + ", XDG_SESSION_TYPE=" + xdgSessionType + ", XDG_RUNTIME_DIR=" + xdgRuntimeDir + ", XAUTHORITY=" + xauthority);

        String lowered = String.valueOf(ex.getMessage()).toLowerCase(Locale.ROOT);
        if (lowered.contains("headful library") || lowered.contains("headless")) {
            LOGGER.severe("To wygląda na środowisko JRE bez pełnych bibliotek GUI (np. pakiet headless) albo wymuszony tryb headless.");
            LOGGER.severe("Na Debianie sprawdź: java -version oraz czy masz pełny pakiet JRE/JDK (nie headless). Przykład: sudo apt install openjdk-21-jre");
        }

        if ("root".equals(currentUser) && sudoUser != null) {
            LOGGER.severe("Aplikacja działa jako root przez sudo. W Wayland to zwykle blokuje GUI (brak dostępu do sesji użytkownika).");
            LOGGER.severe("Uruchom bez sudo, albo zachowaj zmienne sesji i autoryzację X11/Wayland.");
            LOGGER.severe("Przykład (X11): sudo --preserve-env=DISPLAY,XAUTHORITY java -jar target/T.F.N.C.-1.0-beta-4.jar --gui");
            LOGGER.severe("Przykład (Wayland): sudo --preserve-env=WAYLAND_DISPLAY,XDG_RUNTIME_DIR java -jar target/T.F.N.C.-1.0-beta-4.jar --gui");
        }

            LOGGER.severe("Jeśli chcesz GUI na Debian/KDE/Wayland, uruchom aplikację w tej samej sesji użytkownika co Plasma.");
        LOGGER.severe("W przeciwnym razie użyj trybu CLI: --cli <katalog> [dodatkowe-zakazane-słowo-1] ...");
    }

    private static int runGuiDiagnostics() {
        String display = System.getenv("DISPLAY");
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        String xdgRuntimeDir = System.getenv("XDG_RUNTIME_DIR");
        String xauthority = System.getenv("XAUTHORITY");
        String currentUser = System.getProperty("user.name");
        String sudoUser = System.getenv("SUDO_USER");
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String javaRuntime = System.getProperty("java.runtime.name");

        LOGGER.info("=== Diagnostyka GUI ===");
        LOGGER.info("Użytkownik procesu=" + currentUser + ", SUDO_USER=" + sudoUser);
        LOGGER.info("JVM=" + javaRuntime + " " + javaVersion + " (" + javaVendor + ")");
        LOGGER.info("java.awt.headless=" + System.getProperty("java.awt.headless"));
        LOGGER.info("DISPLAY=" + display);
        LOGGER.info("WAYLAND_DISPLAY=" + waylandDisplay);
        LOGGER.info("XDG_RUNTIME_DIR=" + xdgRuntimeDir);
        LOGGER.info("XAUTHORITY=" + xauthority);

        boolean desktopModulePresent = ModuleLayer.boot().findModule("java.desktop").isPresent();
        LOGGER.info("Moduł java.desktop dostępny=" + desktopModulePresent);
        LOGGER.info("GraphicsEnvironment.isHeadless()=" + GraphicsEnvironment.isHeadless());

        if (display != null) {
            Path x11Socket = Path.of("/tmp/.X11-unix", display.replace(":", "X"));
            LOGGER.info("X11 socket istnieje=" + Files.exists(x11Socket) + " (" + x11Socket + ")");
        }

        if (waylandDisplay != null && xdgRuntimeDir != null) {
            Path waylandSocket = Path.of(xdgRuntimeDir, waylandDisplay);
            LOGGER.info("Wayland socket istnieje=" + Files.exists(waylandSocket) + " (" + waylandSocket + ")");
        }

        if (!desktopModulePresent) {
            LOGGER.severe("Wniosek: brak modułu java.desktop w runtime. To wyklucza GUI.");
            return 1;
        }

        boolean likelyNoGuiSession = (display == null && waylandDisplay == null) || xdgRuntimeDir == null;
        if (likelyNoGuiSession) {
            LOGGER.severe("Wniosek: proces nie ma poprawnego dostępu do sesji GUI.");
            return 1;
        }

        LOGGER.info("Wniosek: środowisko wygląda na GUI-ready. Jeśli GUI nadal nie startuje, sprawdź czy używasz pakietu JRE/JDK bez headless i czy biblioteki X11 są doinstalowane.");
        LOGGER.info("Debian (przykład): sudo apt install openjdk-21-jre libx11-6 libxext6 libxrender1 libxtst6 libxi6 libfreetype6 libfontconfig1");
        return 0;
    }

    public static void main(String[] args) {
        boolean forceCli = Arrays.stream(args).anyMatch(arg -> "--cli".equalsIgnoreCase(arg));
        boolean forceGui = Arrays.stream(args).anyMatch(arg -> "--gui".equalsIgnoreCase(arg));
        boolean diagnoseGui = Arrays.stream(args).anyMatch(arg -> "--diagnose-gui".equalsIgnoreCase(arg));
        boolean undoHistory = Arrays.stream(args).anyMatch(arg -> "--undo-history".equalsIgnoreCase(arg));

        if ((forceCli && forceGui) || (diagnoseGui && (forceCli || forceGui || undoHistory)) || (undoHistory && (forceCli || forceGui))) {
            LOGGER.severe("Nieprawidłowa kombinacja flag. Użyj tylko jednego trybu: --gui, --cli, --undo-history lub --diagnose-gui.");
            System.exit(2);
            return;
        }

        List<String> filteredArgs = Arrays.stream(args)
                .filter(arg -> !"--cli".equalsIgnoreCase(arg) && !"--gui".equalsIgnoreCase(arg) && !"--diagnose-gui".equalsIgnoreCase(arg) && !"--undo-history".equalsIgnoreCase(arg))
                .toList();

        if (diagnoseGui) {
            System.exit(runGuiDiagnostics());
            return;
        }

        if (forceCli) {
            runCli(filteredArgs);
            return;
        }

        if (undoHistory) {
            if (filteredArgs.size() != 1) {
                LOGGER.severe("Tryb --undo-history wymaga argumentu: <katalog>");
                System.exit(1);
                return;
            }
            new FileRenamer(true).undoRenamesFromHistory(filteredArgs.getFirst());
            return;
        }

        try {
            new FileRenamer(false);
        } catch (HeadlessException | AWTError | UnsatisfiedLinkError ex) {
            logGuiTroubleshooting(ex);

            if (forceGui) {
                System.exit(1);
                return;
            }

            if (filteredArgs.size() >= 2) {
                LOGGER.info("Przełączanie do trybu CLI po nieudanej próbie startu GUI.");
                runCli(filteredArgs);
                return;
            }

            LOGGER.severe("Brak argumentów do trybu CLI. Podaj: --cli <katalog> [dodatkowe-zakazane-słowo-1] [dodatkowe-zakazane-słowo-2] ...");
            System.exit(1);
        }
    }
}
