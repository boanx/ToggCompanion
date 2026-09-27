package com.togg.companion.core;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Manages OAuth 2.0 PKCE authentication flow with Togg Keycloak IAM (toggid.togg.cloud).
 */
public final class ToggAuthManager {

    public static final String CLIENT_ID = "super-app";
    public static final String REDIRECT_URI = "superapp://prod/callback";
    public static final String AUTH_ENDPOINT = "https://toggid.togg.cloud/auth/realms/toggid/protocol/openid-connect/auth";
    public static final String TOKEN_ENDPOINT = "https://toggid.togg.cloud/auth/realms/toggid/protocol/openid-connect/token";

    public interface TokenCallback {
        void onSuccess(String accessToken, String refreshToken);
        void onError(String errorMessage);
    }

    /**
     * Generates a cryptographically random, URL-safe PKCE code verifier (43-128 characters).
     */
    public static String generateCodeVerifier() {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[32];
        sr.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /**
     * Generates a SHA-256 Base64URL-encoded PKCE code challenge from the code verifier.
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            return codeVerifier;
        }
    }

    public static final String AUTH_SCOPES = "openid profile email phone roles offline_access";

    /**
     * Builds the complete Togg ID authorization URL with client_id, redirect_uri, and PKCE parameters.
     */
    public static String buildAuthorizationUrl(String codeChallenge) {
        return AUTH_ENDPOINT
                + "?client_id=" + CLIENT_ID
                + "&redirect_uri=" + Uri.encode(REDIRECT_URI)
                + "&response_type=code"
                + "&scope=" + Uri.encode(AUTH_SCOPES)
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256";
    }

    /**
     * Exchanges the authorization code received from the redirect URL for OAuth Access and Refresh tokens.
     */
    public static void exchangeCodeForToken(String code, String codeVerifier, TokenCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(TOKEN_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Trumore/2.1.25 (Linux; Android 15; SM-S938B)");

                String params = "grant_type=authorization_code"
                        + "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8")
                        + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                        + "&code=" + URLEncoder.encode(code, "UTF-8")
                        + "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int codeStatus = conn.getResponseCode();
                InputStream is = (codeStatus >= 200 && codeStatus < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (codeStatus >= 200 && codeStatus < 300) {
                    JSONObject json = new JSONObject(response.toString());
                    String accessToken = json.optString("access_token", "");
                    String refreshToken = json.optString("refresh_token", "");
                    if (!accessToken.isEmpty()) {
                        callback.onSuccess(accessToken, refreshToken);
                    } else {
                        callback.onError("Sunucudan geçerli oturum anahtarı alınamadı.");
                    }
                } else {
                    String desc;
                    try {
                        JSONObject err = new JSONObject(response.toString());
                        desc = err.optString("error_description", err.optString("error", "HTTP " + codeStatus));
                    } catch (Exception ignored) {
                        desc = "Sunucu yanıtı: HTTP " + codeStatus;
                    }
                    callback.onError(desc);
                }
            } catch (Exception e) {
                callback.onError("Bağlantı hatası: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    /**
     * Refreshes an expired access token using the stored refresh token.
     */
    public static void refreshAccessToken(String refreshToken, TokenCallback callback) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            callback.onError("Yenileme anahtarı (refresh token) bulunamadı.");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(TOKEN_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "Trumore/2.1.25 (Linux; Android 15; SM-S938B)");

                String params = "grant_type=refresh_token"
                        + "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8")
                        + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(params.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int codeStatus = conn.getResponseCode();
                InputStream is = (codeStatus >= 200 && codeStatus < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                if (codeStatus >= 200 && codeStatus < 300) {
                    JSONObject json = new JSONObject(response.toString());
                    String newAccessToken = json.optString("access_token", "");
                    String newRefreshToken = json.optString("refresh_token", refreshToken);
                    if (!newAccessToken.isEmpty()) {
                        callback.onSuccess(newAccessToken, newRefreshToken);
                    } else {
                        callback.onError("Yenileme yanıtında erişim anahtarı bulunamadı.");
                    }
                } else {
                    String desc;
                    try {
                        JSONObject err = new JSONObject(response.toString());
                        desc = err.optString("error_description", err.optString("error", "HTTP " + codeStatus));
                    } catch (Exception ignored) {
                        desc = "Oturum yenileme hatası: HTTP " + codeStatus;
                    }
                    callback.onError(desc);
                }
            } catch (Exception e) {
                callback.onError("Oturum yenileme bağlantı hatası: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
}

