package com.droidscope.android.transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public final class UploadResultTest {
    @Test
    public void successHasNoErrorAndKeepsHttpStatus() {
        UploadResult result = UploadResult.success(201);

        assertTrue(result.isSuccess());
        assertEquals(201, result.getHttpCode());
        assertNull(result.getError());
        assertEquals("ok", result.getMessage());
    }

    @Test
    public void pcNotConnectedFailureKeepsOriginalMessage() {
        UploadResult result = UploadResult.failure(UploadError.PC_NOT_CONNECTED, -1, "Connection refused");

        assertFalse(result.isSuccess());
        assertEquals(UploadError.PC_NOT_CONNECTED, result.getError());
        assertEquals(-1, result.getHttpCode());
        assertEquals("Connection refused", result.getMessage());
    }

    @Test
    public void timeoutFailureKeepsOriginalMessage() {
        UploadResult result = UploadResult.failure(UploadError.TIMEOUT, -1, "Read timed out");

        assertFalse(result.isSuccess());
        assertEquals(UploadError.TIMEOUT, result.getError());
        assertEquals(-1, result.getHttpCode());
        assertEquals("Read timed out", result.getMessage());
    }

    @Test
    public void httpNon2xxMapsToServerErrorAndKeepsHttpResponse() {
        UploadResult result = UploadResult.failure(503, "Service Unavailable");

        assertFalse(result.isSuccess());
        assertEquals(UploadError.SERVER_ERROR, result.getError());
        assertEquals(503, result.getHttpCode());
        assertEquals("Service Unavailable", result.getMessage());
    }

    @Test
    public void connectionExceptionMapsToPcNotConnected() {
        UploadResult result = UploadResult.failure(new ConnectException("Connection refused"));

        assertEquals(UploadError.PC_NOT_CONNECTED, result.getError());
        assertEquals("Connection refused", result.getMessage());
    }

    @Test
    public void socketTimeoutExceptionMapsToTimeout() {
        UploadResult result = UploadResult.failure(new SocketTimeoutException("Read timed out"));

        assertEquals(UploadError.TIMEOUT, result.getError());
        assertEquals("Read timed out", result.getMessage());
    }

    @Test
    public void socketExceptionMapsToPcNotConnected() {
        UploadResult result = UploadResult.failure(new SocketException("Connection reset"));

        assertEquals(UploadError.PC_NOT_CONNECTED, result.getError());
        assertEquals("Connection reset", result.getMessage());
    }
}
