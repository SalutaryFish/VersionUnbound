package net.minecraft.util;

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class HttpUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HttpUtil() {
    }

    public static Path downloadFile(
        Path saveFile,
        URL url,
        Map<String, String> requestProperties,
        HashFunction hashFunction,
        @Nullable HashCode hash,
        int maxSize,
        Proxy proxy,
        HttpUtil.DownloadProgressListener progressListener
    ) {
        HttpURLConnection httpURLConnection = null;
        InputStream inputStream = null;
        progressListener.requestStart();
        Path path;
        if (hash != null) {
            path = cachedFilePath(saveFile, hash);

            try {
                if (checkExistingFile(path, hashFunction, hash)) {
                    LOGGER.info("Returning cached file since actual hash matches requested");
                    progressListener.requestFinished(true);
                    updateModificationTime(path);
                    return path;
                }
            } catch (IOException iOException) {
                LOGGER.warn("Failed to check cached file {}", path, iOException);
            }

            try {
                LOGGER.warn("Existing file {} not found or had mismatched hash", path);
                Files.deleteIfExists(path);
            } catch (IOException iOException) {
                progressListener.requestFinished(false);
                throw new UncheckedIOException("Failed to remove existing file " + path, iOException);
            }
        } else {
            path = null;
        }

        try {
            httpURLConnection = (HttpURLConnection)url.openConnection(proxy);
            httpURLConnection.setInstanceFollowRedirects(true);
            requestProperties.forEach(httpURLConnection::setRequestProperty);
            inputStream = httpURLConnection.getInputStream();
            long l = httpURLConnection.getContentLengthLong();
            OptionalLong optionalLong = l != -1L ? OptionalLong.of(l) : OptionalLong.empty();
            FileUtil.createDirectoriesSafe(saveFile);
            progressListener.downloadStart(optionalLong);
            if (optionalLong.isPresent() && optionalLong.getAsLong() > maxSize) {
                throw new IOException("Filesize is bigger than maximum allowed (file is " + optionalLong + ", limit is " + maxSize + ")");
            }

            if (path != null) {
                HashCode hashCode = downloadAndHash(hashFunction, maxSize, progressListener, inputStream, path);
                if (!hashCode.equals(hash)) {
                    throw new IOException("Hash of downloaded file (" + hashCode + ") did not match requested (" + hash + ")");
                }

                progressListener.requestFinished(true);
                return path;
            } else {
                Path path2 = Files.createTempFile(saveFile, "download", ".tmp");

                try {
                    HashCode hashCode2 = downloadAndHash(hashFunction, maxSize, progressListener, inputStream, path2);
                    Path path3 = cachedFilePath(saveFile, hashCode2);
                    if (!checkExistingFile(path3, hashFunction, hashCode2)) {
                        Files.move(path2, path3, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        updateModificationTime(path3);
                    }

                    progressListener.requestFinished(true);
                    return path3;
                } finally {
                    Files.deleteIfExists(path2);
                }
            }
        } catch (Throwable throwable) {
            if (httpURLConnection != null) {
                InputStream inputStream2 = httpURLConnection.getErrorStream();
                if (inputStream2 != null) {
                    try {
                        LOGGER.error("HTTP response error: {}", IOUtils.toString(inputStream2, StandardCharsets.UTF_8));
                    } catch (Exception exception) {
                        LOGGER.error("Failed to read response from server");
                    }
                }
            }

            progressListener.requestFinished(false);
            throw new IllegalStateException("Failed to download file " + url, throwable);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    private static void updateModificationTime(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException iOException) {
            LOGGER.warn("Failed to update modification time of {}", path, iOException);
        }
    }

    private static HashCode hashFile(Path path, HashFunction hashFunction) throws IOException {
        Hasher hasher = hashFunction.newHasher();

        try (
            OutputStream outputStream = Funnels.asOutputStream(hasher);
            InputStream inputStream = Files.newInputStream(path);
        ) {
            inputStream.transferTo(outputStream);
        }

        return hasher.hash();
    }

    private static boolean checkExistingFile(Path path, HashFunction hashFunction, HashCode expectedHash) throws IOException {
        if (Files.exists(path)) {
            HashCode hashCode = hashFile(path, hashFunction);
            if (hashCode.equals(expectedHash)) {
                return true;
            }

            LOGGER.warn("Mismatched hash of file {}, expected {} but found {}", path, expectedHash, hashCode);
        }

        return false;
    }

    private static Path cachedFilePath(Path path, HashCode hash) {
        return path.resolve(hash.toString());
    }

    private static HashCode downloadAndHash(
        HashFunction hashFuntion, int maxSize, HttpUtil.DownloadProgressListener progressListener, InputStream stream, Path outputPath
    ) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(outputPath, StandardOpenOption.CREATE)) {
            Hasher hasher = hashFuntion.newHasher();
            byte[] bs = new byte[8196];
            long l = 0L;

            int i;
            while ((i = stream.read(bs)) >= 0) {
                l += i;
                progressListener.downloadedBytes(l);
                if (l > maxSize) {
                    throw new IOException("Filesize was bigger than maximum allowed (got >= " + l + ", limit was " + maxSize + ")");
                }

                if (Thread.interrupted()) {
                    LOGGER.error("INTERRUPTED");
                    throw new IOException("Download interrupted");
                }

                outputStream.write(bs, 0, i);
                hasher.putBytes(bs, 0, i);
            }

            return hasher.hash();
        }
    }

    public static int getAvailablePort() {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException iOException) {
            return 25564;
        }
    }

    public static boolean isPortAvailable(int port) {
        if (port >= 0 && port <= 65535) {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                return serverSocket.getLocalPort() == port;
            } catch (IOException iOException) {
                return false;
            }
        } else {
            return false;
        }
    }

    public interface DownloadProgressListener {
        void requestStart();

        void downloadStart(OptionalLong var1);

        void downloadedBytes(long var1);

        void requestFinished(boolean var1);
    }
}
