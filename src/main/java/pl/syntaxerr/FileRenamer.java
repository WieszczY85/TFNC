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
    private static final Logger LOGGER = Logger.getLogger(FileRenamer.class.getName());
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
        forbiddenWordsArea.setToolTipText("Enter forbidden words here, separating them with spaces");

        JButton runButton = new JButton("Run");

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
        for (String word : forbiddenWords) {
            String pattern = "(?i)" + Pattern.quote(word);
            if (Pattern.compile(pattern).matcher(name).find()) {
                String newName = name.replaceAll(pattern, "").trim();
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
                        writer.println("Changed file/directory name: " + name + " to " + newName);
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
                break;
            }
        }
    }

    private static void runCli(List<String> args) {
        if (args.size() < 2) {
            LOGGER.severe("Tryb CLI wymaga argumentów: <katalog> <zakazane-słowo-1> [zakazane-słowo-2] ...");
            System.exit(1);
            return;
        }

        String directory = args.get(0);
        List<String> forbiddenWords = args.subList(1, args.size());
        new FileRenamer(true).renameFilesAndDirectoriesInDirectory(directory, forbiddenWords);
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
        LOGGER.severe("W przeciwnym razie użyj trybu CLI: --cli <katalog> <zakazane-słowo-1> ...");
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

        if ((forceCli && forceGui) || (diagnoseGui && (forceCli || forceGui))) {
            LOGGER.severe("Nieprawidłowa kombinacja flag. Użyj tylko jednego trybu: --gui, --cli lub --diagnose-gui.");
            System.exit(2);
            return;
        }

        List<String> filteredArgs = Arrays.stream(args)
                .filter(arg -> !"--cli".equalsIgnoreCase(arg) && !"--gui".equalsIgnoreCase(arg) && !"--diagnose-gui".equalsIgnoreCase(arg))
                .toList();

        if (diagnoseGui) {
            System.exit(runGuiDiagnostics());
            return;
        }

        if (forceCli) {
            runCli(filteredArgs);
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

            LOGGER.severe("Brak argumentów do trybu CLI. Podaj: --cli <katalog> <zakazane-słowo-1> [zakazane-słowo-2] ...");
            System.exit(1);
        }
    }
}
