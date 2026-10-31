package com.mojang.realmsclient.exception;

public class RealmsHttpException extends RuntimeException {
    public RealmsHttpException(String message, Exception cause) {
        super(message, cause);
    }
}
