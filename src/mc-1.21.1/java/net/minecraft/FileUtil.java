package net.minecraft;

import com.mojang.serialization.DataResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;

public class FileUtil {
    private static final Pattern COPY_COUNTER_PATTERN = Pattern.compile("(<name>.*) \\((<count>\\d*)\\)", 66);
    private static final int MAX_FILE_NAME = 255;
    private static final Pattern RESERVED_WINDOWS_FILENAMES = Pattern.compile(".*\\.|(?:COM|CLOCK\\$|CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?", 2);
    private static final Pattern STRICT_PATH_SEGMENT_CHECK = Pattern.compile("[-._a-z0-9]+");

    public static String sanitizeName(String name) {
        for (char c : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
            name = name.replace(c, '_');
        }

        return name.replaceAll("[./\"]", "_");
    }

    public static String findAvailableName(Path dirPath, String fileName, String fileFormat) throws IOException {
        fileName = sanitizeName(fileName);
        if (RESERVED_WINDOWS_FILENAMES.matcher(fileName).matches()) {
            fileName = "_" + fileName + "_";
        }

        Matcher matcher = COPY_COUNTER_PATTERN.matcher(fileName);
        int i = 0;
        if (matcher.matches()) {
            fileName = matcher.group("name");
            i = Integer.parseInt(matcher.group("count"));
        }

        if (fileName.length() > 255 - fileFormat.length()) {
            fileName = fileName.substring(0, 255 - fileFormat.length());
        }

        while (true) {
            String string = fileName;
            if (i != 0) {
                String string2 = " (" + i + ")";
                int j = 255 - string2.length();
                if (string.length() > j) {
                    string = string.substring(0, j);
                }

                string = string + string2;
            }

            string = string + fileFormat;
            Path path = dirPath.resolve(string);

            try {
                Path path2 = Files.createDirectory(path);
                Files.deleteIfExists(path2);
                return dirPath.relativize(path2).toString();
            } catch (FileAlreadyExistsException fileAlreadyExistsException) {
                i++;
            }
        }
    }

    public static boolean isPathNormalized(Path path) {
        Path path2 = path.normalize();
        return path2.equals(path);
    }

    public static boolean isPathPortable(Path path) {
        for (Path path2 : path) {
            if (RESERVED_WINDOWS_FILENAMES.matcher(path2.toString()).matches()) {
                return false;
            }
        }

        return true;
    }

    public static Path createPathToResource(Path dirPath, String locationPath, String fileFormat) {
        String string = locationPath + fileFormat;
        Path path = Paths.get(string);
        if (path.endsWith(fileFormat)) {
            throw new InvalidPathException(string, "empty resource name");
        } else {
            return dirPath.resolve(path);
        }
    }

    public static String getFullResourcePath(String path) {
        return FilenameUtils.getFullPath(path).replace(File.separator, "/");
    }

    public static String normalizeResourcePath(String path) {
        return FilenameUtils.normalize(path).replace(File.separator, "/");
    }

    public static DataResult<List<String>> decomposePath(String path) {
        int i = path.indexOf(47);
        if (i == -1) {
            return switch (path) {
                case "", ".", ".." -> DataResult.error(() -> "Invalid path '" + path + "'");
                default -> !isValidStrictPathSegment(path) ? DataResult.error(() -> "Invalid path '" + path + "'") : DataResult.success(List.of(path));
            };
        } else {
            List<String> list = new ArrayList<>();
            int j = 0;
            boolean bl = false;

            while (true) {
                String string = path.substring(j, i);
                switch (string) {
                    case "":
                    case ".":
                    case "..":
                        return DataResult.error(() -> "Invalid segment '" + string + "' in path '" + path + "'");
                }

                if (!isValidStrictPathSegment(string)) {
                    return DataResult.error(() -> "Invalid segment '" + string + "' in path '" + path + "'");
                }

                list.add(string);
                if (bl) {
                    return DataResult.success(list);
                }

                j = i + 1;
                i = path.indexOf(47, j);
                if (i == -1) {
                    i = path.length();
                    bl = true;
                }
            }
        }
    }

    public static Path resolvePath(Path path, List<String> subdirectories) {
        int i = subdirectories.size();

        return switch (i) {
            case 0 -> path;
            case 1 -> path.resolve(subdirectories.get(0));
            default -> {
                String[] strings = new String[i - 1];

                for (int j = 1; j < i; j++) {
                    strings[j - 1] = subdirectories.get(j);
                }

                yield path.resolve(path.getFileSystem().getPath(subdirectories.get(0), strings));
            }
        };
    }

    public static boolean isValidStrictPathSegment(String segment) {
        return STRICT_PATH_SEGMENT_CHECK.matcher(segment).matches();
    }

    public static void validatePath(String... elements) {
        if (elements.length == 0) {
            throw new IllegalArgumentException("Path must have at least one element");
        }

        for (String string : elements) {
            if (string.equals("..") || string.equals(".") || !isValidStrictPathSegment(string)) {
                throw new IllegalArgumentException("Illegal segment " + string + " in path " + Arrays.toString(elements));
            }
        }
    }

    public static void createDirectoriesSafe(Path path) throws IOException {
        Files.createDirectories(Files.exists(path) ? path.toRealPath() : path);
    }
}
