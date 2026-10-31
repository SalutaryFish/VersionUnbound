package com.mojang.realmsclient.gui.screens;

import javax.annotation.Nullable;

public class UploadResult {
    public final int statusCode;
    @Nullable
    public final String errorMessage;

    UploadResult(int statusCode, String errorMessage) {
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    public static class Builder {
        private int statusCode = -1;
        private String errorMessage;

        public UploadResult.Builder withStatusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public UploadResult.Builder withErrorMessage(@Nullable String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public UploadResult build() {
            return new UploadResult(this.statusCode, this.errorMessage);
        }
    }
}
