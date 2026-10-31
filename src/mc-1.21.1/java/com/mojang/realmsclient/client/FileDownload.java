package com.mojang.realmsclient.client;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.dto.WorldDownload;
import com.mojang.realmsclient.exception.RealmsDefaultUncaughtExceptionHandler;
import com.mojang.realmsclient.gui.screens.RealmsDownloadLatestWorldScreen;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.ContentValidationException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;

public class FileDownload {
    static final Logger LOGGER = LogUtils.getLogger();
    volatile boolean cancelled;
    volatile boolean finished;
    volatile boolean error;
    volatile boolean extracting;
    @Nullable
    private volatile File tempFile;
    volatile File resourcePackPath;
    @Nullable
    private volatile HttpGet request;
    @Nullable
    private Thread currentThread;
    private final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(120000).setConnectTimeout(120000).build();
    private static final String[] INVALID_FILE_NAMES = new String[]{
        "CON",
        "COM",
        "PRN",
        "AUX",
        "CLOCK$",
        "NUL",
        "COM1",
        "COM2",
        "COM3",
        "COM4",
        "COM5",
        "COM6",
        "COM7",
        "COM8",
        "COM9",
        "LPT1",
        "LPT2",
        "LPT3",
        "LPT4",
        "LPT5",
        "LPT6",
        "LPT7",
        "LPT8",
        "LPT9"
    };

    public long contentLength(String uri) {
        CloseableHttpClient closeableHttpClient = null;
        HttpGet httpGet = null;

        try {
            httpGet = new HttpGet(uri);
            closeableHttpClient = HttpClientBuilder.create().setDefaultRequestConfig(this.requestConfig).build();
            CloseableHttpResponse closeableHttpResponse = closeableHttpClient.execute(httpGet);
            return Long.parseLong(closeableHttpResponse.getFirstHeader("Content-Length").getValue());
        } catch (Throwable throwable) {
            LOGGER.error("Unable to get content length for download");
            return 0L;
        } finally {
            if (httpGet != null) {
                httpGet.releaseConnection();
            }

            if (closeableHttpClient != null) {
                try {
                    closeableHttpClient.close();
                } catch (IOException iOException2) {
                    LOGGER.error("Could not close http client", iOException2);
                }
            }
        }
    }

    public void download(WorldDownload download, String worldName, RealmsDownloadLatestWorldScreen.DownloadStatus status, LevelStorageSource source) {
        if (this.currentThread == null) {
            this.currentThread = new Thread(
                () -> {
                    CloseableHttpClient closeableHttpClient = null;

                    try {
                        this.tempFile = File.createTempFile("backup", ".tar.gz");
                        this.request = new HttpGet(download.downloadLink);
                        closeableHttpClient = HttpClientBuilder.create().setDefaultRequestConfig(this.requestConfig).build();
                        HttpResponse httpResponse = closeableHttpClient.execute(this.request);
                        status.totalBytes = Long.parseLong(httpResponse.getFirstHeader("Content-Length").getValue());
                        if (httpResponse.getStatusLine().getStatusCode() == 200) {
                            OutputStream outputStream2 = new FileOutputStream(this.tempFile);
                            FileDownload.ProgressListener progressListener = new FileDownload.ProgressListener(worldName.trim(), this.tempFile, source, status);
                            FileDownload.DownloadCountingOutputStream downloadCountingOutputStream2 = new FileDownload.DownloadCountingOutputStream(
                                outputStream2
                            );
                            downloadCountingOutputStream2.setListener(progressListener);
                            IOUtils.copy(httpResponse.getEntity().getContent(), downloadCountingOutputStream2);
                            return;
                        }

                        this.error = true;
                        this.request.abort();
                    } catch (Exception exception2) {
                        LOGGER.error("Caught exception while downloading: {}", exception2.getMessage());
                        this.error = true;
                        return;
                    } finally {
                        this.request.releaseConnection();
                        if (this.tempFile != null) {
                            this.tempFile.delete();
                        }

                        if (!this.error) {
                            if (!download.resourcePackUrl.isEmpty() && !download.resourcePackHash.isEmpty()) {
                                try {
                                    this.tempFile = File.createTempFile("resources", ".tar.gz");
                                    this.request = new HttpGet(download.resourcePackUrl);
                                    HttpResponse httpResponse3 = closeableHttpClient.execute(this.request);
                                    status.totalBytes = Long.parseLong(httpResponse3.getFirstHeader("Content-Length").getValue());
                                    if (httpResponse3.getStatusLine().getStatusCode() != 200) {
                                        this.error = true;
                                        this.request.abort();
                                        return;
                                    }

                                    OutputStream outputStream3 = new FileOutputStream(this.tempFile);
                                    FileDownload.ResourcePackProgressListener resourcePackProgressListener3 = new FileDownload.ResourcePackProgressListener(
                                        this.tempFile, status, download
                                    );
                                    FileDownload.DownloadCountingOutputStream downloadCountingOutputStream3 = new FileDownload.DownloadCountingOutputStream(
                                        outputStream3
                                    );
                                    downloadCountingOutputStream3.setListener(resourcePackProgressListener3);
                                    IOUtils.copy(httpResponse3.getEntity().getContent(), downloadCountingOutputStream3);
                                } catch (Exception exception3) {
                                    LOGGER.error("Caught exception while downloading: {}", exception3.getMessage());
                                    this.error = true;
                                } finally {
                                    this.request.releaseConnection();
                                    if (this.tempFile != null) {
                                        this.tempFile.delete();
                                    }
                                }
                            } else {
                                this.finished = true;
                            }
                        }

                        if (closeableHttpClient != null) {
                            try {
                                closeableHttpClient.close();
                            } catch (IOException iOException3) {
                                LOGGER.error("Failed to close Realms download client");
                            }
                        }
                    }
                }
            );
            this.currentThread.setUncaughtExceptionHandler(new RealmsDefaultUncaughtExceptionHandler(LOGGER));
            this.currentThread.start();
        }
    }

    public void cancel() {
        if (this.request != null) {
            this.request.abort();
        }

        if (this.tempFile != null) {
            this.tempFile.delete();
        }

        this.cancelled = true;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public boolean isError() {
        return this.error;
    }

    public boolean isExtracting() {
        return this.extracting;
    }

    /**
     * Modifies a folder name to make sure it is valid to store on disk.
     * @return the modified folder name
     *
     * @param folderName The folder name to modify
     */
    public static String findAvailableFolderName(String folderName) {
        folderName = folderName.replaceAll("[\\./\"]", "_");

        for (String string : INVALID_FILE_NAMES) {
            if (folderName.equalsIgnoreCase(string)) {
                folderName = "_" + folderName + "_";
            }
        }

        return folderName;
    }

    void untarGzipArchive(String worldName, @Nullable File tempFile, LevelStorageSource levelStorageSource) throws IOException {
        Pattern pattern = Pattern.compile(".*-([0-9]+)$");
        int i = 1;

        for (char c : SharedConstants.ILLEGAL_FILE_CHARACTERS) {
            worldName = worldName.replace(c, '_');
        }

        if (StringUtils.isEmpty(worldName)) {
            worldName = "Realm";
        }

        worldName = findAvailableFolderName(worldName);

        try {
            for (LevelStorageSource.LevelDirectory levelDirectory : levelStorageSource.findLevelCandidates()) {
                String string = levelDirectory.directoryName();
                if (string.toLowerCase(Locale.ROOT).startsWith(worldName.toLowerCase(Locale.ROOT))) {
                    Matcher matcher = pattern.matcher(string);
                    if (matcher.matches()) {
                        int j = Integer.parseInt(matcher.group(1));
                        if (j > i) {
                            i = j;
                        }
                    } else {
                        i++;
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Error getting level list", exception);
            this.error = true;
            return;
        }

        String string2;
        if (levelStorageSource.isNewLevelIdAcceptable(worldName) && i <= 1) {
            string2 = worldName;
        } else {
            string2 = worldName + (i == 1 ? "" : "-" + i);
            if (!levelStorageSource.isNewLevelIdAcceptable(string2)) {
                boolean bl = false;

                while (!bl) {
                    i++;
                    string2 = worldName + (i == 1 ? "" : "-" + i);
                    if (levelStorageSource.isNewLevelIdAcceptable(string2)) {
                        bl = true;
                    }
                }
            }
        }

        TarArchiveInputStream tarArchiveInputStream = null;
        File file = new File(Minecraft.getInstance().gameDirectory.getAbsolutePath(), "saves");

        try {
            file.mkdir();
            tarArchiveInputStream = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(tempFile))));

            for (TarArchiveEntry tarArchiveEntry = tarArchiveInputStream.getNextTarEntry();
                tarArchiveEntry != null;
                tarArchiveEntry = tarArchiveInputStream.getNextTarEntry()
            ) {
                File file2 = new File(file, tarArchiveEntry.getName().replace("world", string2));
                if (tarArchiveEntry.isDirectory()) {
                    file2.mkdirs();
                } else {
                    file2.createNewFile();

                    try (FileOutputStream fileOutputStream = new FileOutputStream(file2)) {
                        IOUtils.copy(tarArchiveInputStream, fileOutputStream);
                    }
                }
            }
        } catch (Exception exception2) {
            LOGGER.error("Error extracting world", exception2);
            this.error = true;
        } finally {
            if (tarArchiveInputStream != null) {
                tarArchiveInputStream.close();
            }

            if (tempFile != null) {
                tempFile.delete();
            }

            try (LevelStorageSource.LevelStorageAccess levelStorageAccess2 = levelStorageSource.validateAndCreateAccess(string2)) {
                levelStorageAccess2.renameAndDropPlayer(string2);
            } catch (IOException | NbtException | ReportedNbtException exception3) {
                LOGGER.error("Failed to modify unpacked realms level {}", string2, exception3);
            } catch (ContentValidationException contentValidationException2) {
                LOGGER.warn("{}", contentValidationException2.getMessage());
            }

            this.resourcePackPath = new File(file, string2 + File.separator + "resources.zip");
        }
    }

    static class DownloadCountingOutputStream extends CountingOutputStream {
        @Nullable
        private ActionListener listener;

        public DownloadCountingOutputStream(OutputStream out) {
            super(out);
        }

        public void setListener(ActionListener listener) {
            this.listener = listener;
        }

        @Override
        protected void afterWrite(int bytesWritten) throws IOException {
            super.afterWrite(bytesWritten);
            if (this.listener != null) {
                this.listener.actionPerformed(new ActionEvent(this, 0, null));
            }
        }
    }

    class ProgressListener implements ActionListener {
        private final String worldName;
        private final File tempFile;
        private final LevelStorageSource levelStorageSource;
        private final RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus;

        ProgressListener(
            final String worldName,
            final File tempFile,
            final LevelStorageSource levelStorageSource,
            final RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus
        ) {
            this.worldName = worldName;
            this.tempFile = tempFile;
            this.levelStorageSource = levelStorageSource;
            this.downloadStatus = downloadStatus;
        }

        @Override
        public void actionPerformed(ActionEvent action) {
            this.downloadStatus.bytesWritten = ((FileDownload.DownloadCountingOutputStream)action.getSource()).getByteCount();
            if (this.downloadStatus.bytesWritten >= this.downloadStatus.totalBytes && !FileDownload.this.cancelled && !FileDownload.this.error) {
                try {
                    FileDownload.this.extracting = true;
                    FileDownload.this.untarGzipArchive(this.worldName, this.tempFile, this.levelStorageSource);
                } catch (IOException iOException) {
                    FileDownload.LOGGER.error("Error extracting archive", iOException);
                    FileDownload.this.error = true;
                }
            }
        }
    }

    class ResourcePackProgressListener implements ActionListener {
        private final File tempFile;
        private final RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus;
        private final WorldDownload worldDownload;

        ResourcePackProgressListener(
            final File tempFile, final RealmsDownloadLatestWorldScreen.DownloadStatus downloadStatus, final WorldDownload worldDownload
        ) {
            this.tempFile = tempFile;
            this.downloadStatus = downloadStatus;
            this.worldDownload = worldDownload;
        }

        @Override
        public void actionPerformed(ActionEvent action) {
            this.downloadStatus.bytesWritten = ((FileDownload.DownloadCountingOutputStream)action.getSource()).getByteCount();
            if (this.downloadStatus.bytesWritten >= this.downloadStatus.totalBytes && !FileDownload.this.cancelled) {
                try {
                    String string = Hashing.sha1().hashBytes(Files.toByteArray(this.tempFile)).toString();
                    if (string.equals(this.worldDownload.resourcePackHash)) {
                        FileUtils.copyFile(this.tempFile, FileDownload.this.resourcePackPath);
                        FileDownload.this.finished = true;
                    } else {
                        FileDownload.LOGGER
                            .error("Resourcepack had wrong hash (expected {}, found {}). Deleting it.", this.worldDownload.resourcePackHash, string);
                        FileUtils.deleteQuietly(this.tempFile);
                        FileDownload.this.error = true;
                    }
                } catch (IOException iOException) {
                    FileDownload.LOGGER.error("Error copying resourcepack file: {}", iOException.getMessage());
                    FileDownload.this.error = true;
                }
            }
        }
    }
}
