package com.togg.companion.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppLogTest {
    @Test public void exportedTextRedactsCredentialsAndIdentity() {
        String value = AppLog.sanitize("Bearer private-token test@example.com https://example.com/?token=private WVWZZZ1JZXW000001 11111111-2222-4333-8444-555555555555");
        assertFalse(value.contains("private-token"));
        assertFalse(value.contains("test@example.com"));
        assertFalse(value.contains("https://"));
        assertFalse(value.contains("WVWZZZ1JZXW000001"));
        assertFalse(value.contains("11111111"));
    }
    @Test public void diagnosticStatusRemainsUseful() {
        assertEquals("CERT_HTTP GET_CERTIFICATE status=500", AppLog.sanitize("CERT_HTTP GET_CERTIFICATE status=500"));
    }
}
