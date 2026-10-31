package net.minecraft.util.eventlog;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class EventLogDirectory {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int COMPRESS_BUFFER_SIZE = 4096;
    private static final String COMPRESSED_EXTENSION = ".gz";
    private final Path root;
    private final String extension;

    private EventLogDirectory(Path root, String extension) {
        this.root = root;
        this.extension = extension;
    }

    public static EventLogDirectory open(Path root, String extension) throws IOException {
        Files.createDirectories(root);
        return new EventLogDirectory(root, extension);
    }

    public EventLogDirectory.FileList listFiles() throws IOException {
        try (Stream<Path> stream = Files.list(this.root)) {
            return new EventLogDirectory.FileList(stream.filter(path -> Files.isRegularFile(path)).map(this::parseFile).filter(Objects::nonNull).toList());
        }
    }

    @Nullable
    private EventLogDirectory.File parseFile(Path path) {
        String string = path.getFileName().toString();
        int i = string.indexOf(46);
        if (i == -1) {
            return null;
        }

        EventLogDirectory.FileId fileId = EventLogDirectory.FileId.parse(string.substring(0, i));
        if (fileId != null) {
            String string2 = string.substring(i);
            if (string2.equals(this.extension)) {
                return new EventLogDirectory.RawFile(path, fileId);
            }

            if (string2.equals(this.extension + ".gz")) {
                return new EventLogDirectory.CompressedFile(path, fileId);
            }
        }

        return null;
    }

    static void tryCompress(Path path, Path outputPath) throws IOException {
        if (Files.exists(outputPath)) {
            throw new IOException("Compressed target file already exists: " + outputPath);
        }

        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            FileLock fileLock = fileChannel.tryLock();
            if (fileLock == null) {
                throw new IOException("Raw log file is already locked, cannot compress: " + path);
            }

            writeCompressed(fileChannel, outputPath);
            fileChannel.truncate(0L);
        }

        Files.delete(path);
    }

    private static void writeCompressed(ReadableByteChannel channel, Path outputPath) throws IOException {
        try (OutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(outputPath))) {
            byte[] bs = new byte[4096];
            ByteBuffer byteBuffer = ByteBuffer.wrap(bs);

            while (channel.read(byteBuffer) >= 0) {
                byteBuffer.flip();
                outputStream.write(bs, 0, byteBuffer.limit());
                byteBuffer.clear();
            }
        }
    }

    public EventLogDirectory.RawFile createNewFile(LocalDate date) throws IOException {
        int i = 1;
        Set<EventLogDirectory.FileId> set = this.listFiles().ids();

        EventLogDirectory.FileId fileId;
        do {
            fileId = new EventLogDirectory.FileId(date, i++);
        } while (set.contains(fileId));

        EventLogDirectory.RawFile rawFile = new EventLogDirectory.RawFile(this.root.resolve(fileId.toFileName(this.extension)), fileId);
        Files.createFile(rawFile.path());
        return rawFile;
    }

    public record CompressedFile(Path path, EventLogDirectory.FileId id) implements EventLogDirectory.File {
        @Nullable
        @Override
        public Reader openReader() throws IOException {
            return !Files.exists(this.path) ? null : new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(this.path))));
        }

        @Override
        public EventLogDirectory.CompressedFile compress() {
            return this;
        }
    }

    public interface File {
        Path path();

        EventLogDirectory.FileId id();

        @Nullable
        Reader openReader() throws IOException;

        EventLogDirectory.CompressedFile compress() throws IOException;
    }

    public record FileId(LocalDate date, int index) {
        private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

        @Nullable
        public static EventLogDirectory.FileId parse(String fileName) {
            int i = fileName.indexOf("-");
            if (i == -1) {
                return null;
            }

            String string = fileName.substring(0, i);
            String string2 = fileName.substring(i + 1);

            try {
                return new EventLogDirectory.FileId(LocalDate.parse(string, DATE_FORMATTER), Integer.parseInt(string2));
            } catch (NumberFormatException | DateTimeParseException runtimeException) {
                return null;
            }
        }

        @Override
        public String toString() {
            return DATE_FORMATTER.format(this.date) + "-" + this.index;
        }

        public String toFileName(String extension) {
            return this + extension;
        }
    }

    public static class FileList implements Iterable<EventLogDirectory.File> {
        private final List<EventLogDirectory.File> files;

        FileList(List<EventLogDirectory.File> files) {
            this.files = new ArrayList<>(files);
        }

        public EventLogDirectory.FileList prune(LocalDate date, int daysToKeep) {
            this.files.removeIf(file -> {
                EventLogDirectory.FileId fileId = file.id();
                LocalDate localDate2 = fileId.date().plusDays(daysToKeep);
                if (!date.isBefore(localDate2)) {
                    try {
                        Files.delete(file.path());
                        return true;
                    } catch (IOException iOException) {
                        EventLogDirectory.LOGGER.warn("Failed to delete expired event log file: {}", file.path(), iOException);
                    }
                }

                return false;
            });
            return this;
        }

        public EventLogDirectory.FileList compressAll() {
            ListIterator<EventLogDirectory.File> listIterator = this.files.listIterator();

            while (listIterator.hasNext()) {
                EventLogDirectory.File file = listIterator.next();

                try {
                    listIterator.set(file.compress());
                } catch (IOException iOException) {
                    EventLogDirectory.LOGGER.warn("Failed to compress event log file: {}", file.path(), iOException);
                }
            }

            return this;
        }

        @Override
        public Iterator<EventLogDirectory.File> iterator() {
            return this.files.iterator();
        }

        public Stream<EventLogDirectory.File> stream() {
            return this.files.stream();
        }

        public Set<EventLogDirectory.FileId> ids() {
            return this.files.stream().map(EventLogDirectory.File::id).collect(Collectors.toSet());
        }
    }

    public record RawFile(Path path, EventLogDirectory.FileId id) implements EventLogDirectory.File {
        public FileChannel openChannel() throws IOException {
            return FileChannel.open(this.path, StandardOpenOption.WRITE, StandardOpenOption.READ);
        }

        @Nullable
        @Override
        public Reader openReader() throws IOException {
            return Files.exists(this.path) ? Files.newBufferedReader(this.path) : null;
        }

        @Override
        public EventLogDirectory.CompressedFile compress() throws IOException {
            Path path = this.path.resolveSibling(this.path.getFileName().toString() + ".gz");
            EventLogDirectory.tryCompress(this.path, path);
            return new EventLogDirectory.CompressedFile(path, this.id);
        }
    }
}
