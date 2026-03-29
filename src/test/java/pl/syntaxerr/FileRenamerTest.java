package pl.syntaxerr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRenamerTest {

    @Test
    void parseForbiddenWords_preservesSpaceInsideEntry() {
        List<String> words = FileRenamer.parseForbiddenWords("[ \n[rarbg.to]");

        assertEquals(List.of("[ ", "[rarbg.to]"), words);
    }

    @Test
    void parseForbiddenWords_supportsQuotedSingleLineEntry() {
        List<String> words = FileRenamer.parseForbiddenWords("\"[ \"\n\" [x] \"");

        assertEquals(List.of("[ ", " [x] "), words);
    }

    @Test
    void undoRenamesFromHistory_revertsUniqueOldFormatEntry(@TempDir Path tempDir) throws IOException {
        Path newFile = Files.createFile(tempDir.resolve("new-name.txt"));
        Files.writeString(Path.of("history.txt"), "Changed file/directory name: old-name.txt to new-name.txt\n");

        try {
            FileRenamer renamer = new FileRenamer(true);
            renamer.undoRenamesFromHistory(tempDir.toString());
        } finally {
            cleanupAppFiles();
        }

        assertFalse(Files.exists(newFile));
        assertTrue(Files.exists(tempDir.resolve("old-name.txt")));
    }

    @Test
    void undoRenamesFromHistory_skipsAmbiguousOldFormatEntry(@TempDir Path tempDir) throws IOException {
        Path one = Files.createDirectories(tempDir.resolve("one"));
        Path two = Files.createDirectories(tempDir.resolve("two"));
        Files.createFile(one.resolve("same-name.txt"));
        Files.createFile(two.resolve("same-name.txt"));
        Files.writeString(Path.of("history.txt"), "Changed file/directory name: original.txt to same-name.txt\n");

        try {
            FileRenamer renamer = new FileRenamer(true);
            renamer.undoRenamesFromHistory(tempDir.toString());
        } finally {
            cleanupAppFiles();
        }

        assertTrue(Files.exists(one.resolve("same-name.txt")));
        assertTrue(Files.exists(two.resolve("same-name.txt")));
        assertFalse(Files.exists(one.resolve("original.txt")));
        assertFalse(Files.exists(two.resolve("original.txt")));
    }

    private void cleanupAppFiles() throws IOException {
        Files.deleteIfExists(Path.of("history.txt"));
        Files.deleteIfExists(Path.of("blacklist.txt"));
        Files.deleteIfExists(Path.of("error.txt"));
    }
}
