package com.mojang.realmsclient.client;

import com.mojang.realmsclient.exception.RealmsHttpException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

public abstract class Request<T extends Request<T>> {
    protected HttpURLConnection connection;
    private boolean connected;
    protected String url;
    private static final int DEFAULT_READ_TIMEOUT = 60000;
    private static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    private static final String IS_SNAPSHOT_KEY = "Is-Prerelease";
    private static final String COOKIE_KEY = "Cookie";

    public Request(String url, int connectTimeout, int readTimeout) {
        try {
            this.url = url;
            Proxy proxy = RealmsClientConfig.getProxy();
            if (proxy != null) {
                this.connection = (HttpURLConnection)new URL(url).openConnection(proxy);
            } else {
                this.connection = (HttpURLConnection)new URL(url).openConnection();
            }

            this.connection.setConnectTimeout(connectTimeout);
            this.connection.setReadTimeout(readTimeout);
        } catch (MalformedURLException malformedURLException) {
            throw new RealmsHttpException(malformedURLException.getMessage(), malformedURLException);
        } catch (IOException iOException) {
            throw new RealmsHttpException(iOException.getMessage(), iOException);
        }
    }

    public void cookie(String key, String value) {
        cookie(this.connection, key, value);
    }

    public static void cookie(HttpURLConnection connection, String key, String value) {
        String string = connection.getRequestProperty("Cookie");
        if (string == null) {
            connection.setRequestProperty("Cookie", key + "=" + value);
        } else {
            connection.setRequestProperty("Cookie", string + ";" + key + "=" + value);
        }
    }

    public void addSnapshotHeader(boolean isSnapshot) {
        this.connection.addRequestProperty("Is-Prerelease", String.valueOf(isSnapshot));
    }

    public int getRetryAfterHeader() {
        return getRetryAfterHeader(this.connection);
    }

    public static int getRetryAfterHeader(HttpURLConnection connection) {
        String string = connection.getHeaderField("Retry-After");

        try {
            return Integer.valueOf(string);
        } catch (Exception exception) {
            return 5;
        }
    }

    public int responseCode() {
        try {
            this.connect();
            return this.connection.getResponseCode();
        } catch (Exception exception) {
            throw new RealmsHttpException(exception.getMessage(), exception);
        }
    }

    public String text() {
        try {
            this.connect();
            String string;
            if (this.responseCode() >= 400) {
                string = this.read(this.connection.getErrorStream());
            } else {
                string = this.read(this.connection.getInputStream());
            }

            this.dispose();
            return string;
        } catch (IOException iOException) {
            throw new RealmsHttpException(iOException.getMessage(), iOException);
        }
    }

    private String read(@Nullable InputStream in) throws IOException {
        if (in == null) {
            return "";
        }

        InputStreamReader inputStreamReader = new InputStreamReader(in, StandardCharsets.UTF_8);
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = inputStreamReader.read(); i != -1; i = inputStreamReader.read()) {
            stringBuilder.append((char)i);
        }

        return stringBuilder.toString();
    }

    private void dispose() {
        byte[] bs = new byte[1024];

        try {
            InputStream inputStream = this.connection.getInputStream();

            while (inputStream.read(bs) > 0) {
            }

            inputStream.close();
            return;
        } catch (Exception exception) {
            try {
                InputStream inputStream2 = this.connection.getErrorStream();
                if (inputStream2 != null) {
                    while (inputStream2.read(bs) > 0) {
                    }

                    inputStream2.close();
                    return;
                }
            } catch (IOException var8) {
                return;
            }
        } finally {
            if (this.connection != null) {
                this.connection.disconnect();
            }
        }
    }

    protected T connect() {
        if (this.connected) {
            return (T)this;
        }

        T request = this.doConnect();
        this.connected = true;
        return request;
    }

    protected abstract T doConnect();

    public static Request<?> get(String url) {
        return new Request.Get(url, 5000, 60000);
    }

    public static Request<?> get(String url, int connectTimeout, int readTimeout) {
        return new Request.Get(url, connectTimeout, readTimeout);
    }

    public static Request<?> post(String url, String content) {
        return new Request.Post(url, content, 5000, 60000);
    }

    public static Request<?> post(String url, String content, int connectTimeout, int readTimeout) {
        return new Request.Post(url, content, connectTimeout, readTimeout);
    }

    public static Request<?> delete(String url) {
        return new Request.Delete(url, 5000, 60000);
    }

    public static Request<?> put(String url, String content) {
        return new Request.Put(url, content, 5000, 60000);
    }

    public static Request<?> put(String url, String content, int connectTimeout, int readTimeout) {
        return new Request.Put(url, content, connectTimeout, readTimeout);
    }

    public String getHeader(String name) {
        return getHeader(this.connection, name);
    }

    public static String getHeader(HttpURLConnection connection, String name) {
        try {
            return connection.getHeaderField(name);
        } catch (Exception exception) {
            return "";
        }
    }

    public static class Delete extends Request<Request.Delete> {
        public Delete(String string, int i, int j) {
            super(string, i, j);
        }

        public Request.Delete doConnect() {
            try {
                this.connection.setDoOutput(true);
                this.connection.setRequestMethod("DELETE");
                this.connection.connect();
                return this;
            } catch (Exception exception) {
                throw new RealmsHttpException(exception.getMessage(), exception);
            }
        }
    }

    public static class Get extends Request<Request.Get> {
        public Get(String string, int i, int j) {
            super(string, i, j);
        }

        public Request.Get doConnect() {
            try {
                this.connection.setDoInput(true);
                this.connection.setDoOutput(true);
                this.connection.setUseCaches(false);
                this.connection.setRequestMethod("GET");
                return this;
            } catch (Exception exception) {
                throw new RealmsHttpException(exception.getMessage(), exception);
            }
        }
    }

    public static class Post extends Request<Request.Post> {
        private final String content;

        public Post(String url, String content, int connectTimeout, int readTimeout) {
            super(url, connectTimeout, readTimeout);
            this.content = content;
        }

        public Request.Post doConnect() {
            try {
                if (this.content != null) {
                    this.connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }

                this.connection.setDoInput(true);
                this.connection.setDoOutput(true);
                this.connection.setUseCaches(false);
                this.connection.setRequestMethod("POST");
                OutputStream outputStream = this.connection.getOutputStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, "UTF-8");
                outputStreamWriter.write(this.content);
                outputStreamWriter.close();
                outputStream.flush();
                return this;
            } catch (Exception exception) {
                throw new RealmsHttpException(exception.getMessage(), exception);
            }
        }
    }

    public static class Put extends Request<Request.Put> {
        private final String content;

        public Put(String url, String content, int connectTimeout, int readTimeout) {
            super(url, connectTimeout, readTimeout);
            this.content = content;
        }

        public Request.Put doConnect() {
            try {
                if (this.content != null) {
                    this.connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                }

                this.connection.setDoOutput(true);
                this.connection.setDoInput(true);
                this.connection.setRequestMethod("PUT");
                OutputStream outputStream = this.connection.getOutputStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, "UTF-8");
                outputStreamWriter.write(this.content);
                outputStreamWriter.close();
                outputStream.flush();
                return this;
            } catch (Exception exception) {
                throw new RealmsHttpException(exception.getMessage(), exception);
            }
        }
    }
}
