package com.togg.companion.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.togg.companion.R;
import com.togg.companion.core.ToggAuthManager;
import com.togg.companion.data.SecureTokenStorage;
import com.togg.companion.core.AppLog;
import com.togg.companion.data.SmartAccessAccountStorage;

public final class ToggLoginActivity extends Activity {
    public static final String EXTRA_SMART_ACCESS_ACCOUNT = "smart_access_guest_account";

    private WebView webView;
    private ProgressBar progressBar;
    private View layoutLoading;
    private View statusBarSpacer;

    private String codeVerifier;
    private boolean isHandled = false;
    private boolean smartAccessAccount;
    private String oauthState;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        smartAccessAccount = getIntent().getBooleanExtra(EXTRA_SMART_ACCESS_ACCOUNT, false);
        setContentView(R.layout.activity_togg_login);

        webView = findViewById(R.id.web_view);
        progressBar = findViewById(R.id.progress_bar);
        layoutLoading = findViewById(R.id.layout_loading);
        statusBarSpacer = findViewById(R.id.status_bar_spacer);

        setupWindowInsets();
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        setupWebView();
        startAuthentication();
    }

    private void setupWindowInsets() {
        View decor = getWindow().getDecorView();
        decor.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            if (statusBarSpacer != null) {
                int calculatedTop = Math.max(top + dpToPx(10), dpToPx(56));
                statusBarSpacer.getLayoutParams().height = calculatedTop;
                statusBarSpacer.requestLayout();
            }
            return view.onApplyWindowInsets(insets);
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 15; SM-S938B Build/AP2A.240905.003; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.107 Mobile Safari/537.36 Trumore/2.1.25");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            private boolean handleUrl(String url) {
                if (url != null && url.startsWith("superapp://prod/callback")) {
                    handleCallbackUrl(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }
                if (url != null && url.startsWith("superapp://prod/callback")) {
                    handleCallbackUrl(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void startAuthentication() {
        isHandled = false;
        codeVerifier = ToggAuthManager.generateCodeVerifier();
        String codeChallenge = ToggAuthManager.generateCodeChallenge(codeVerifier);
        String authUrl = ToggAuthManager.buildAuthorizationUrl(codeChallenge);
        if (smartAccessAccount) {
            oauthState = ToggAuthManager.generateCodeVerifier();
            authUrl += "&prompt=login&state=" + Uri.encode(oauthState);
        }
        webView.loadUrl(authUrl);
    }

    private synchronized void handleCallbackUrl(String url) {
        if (isHandled) return;
        Uri uri = Uri.parse(url);
        if (smartAccessAccount && (!"superapp".equals(uri.getScheme())
                || !"prod".equals(uri.getHost()) || !"/callback".equals(uri.getPath())
                || oauthState == null || !oauthState.equals(uri.getQueryParameter("state")))) {
            Toast.makeText(this, "Paylaşılan hesap giriş yanıtı doğrulanamadı; yeniden giriş yapın.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        isHandled = true;

        String code = uri.getQueryParameter("code");
        String error = uri.getQueryParameter("error");
        String errorDesc = uri.getQueryParameter("error_description");

        if (error != null) {
            AppLog.event(this, "LOGIN", errorDesc != null ? errorDesc : error);
            Toast.makeText(this, "Giriş tamamlanamadı. Bilgilerinizi kontrol edip yeniden deneyin.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (code != null && !code.isEmpty()) {
            runOnUiThread(() -> {
                if (layoutLoading != null) layoutLoading.setVisibility(View.VISIBLE);
                if (webView != null) webView.setVisibility(View.GONE);
            });

            ToggAuthManager.exchangeCodeForToken(code, codeVerifier, new ToggAuthManager.TokenCallback() {
                @Override
                public void onSuccess(String accessToken, String refreshToken) {
                    SecureTokenStorage storage = new SecureTokenStorage(ToggLoginActivity.this);
                    if (smartAccessAccount) {
                        try {
                            String telemetrySubject = SmartAccessAccountStorage.subject(storage.getAccessToken());
                            new SmartAccessAccountStorage(ToggLoginActivity.this)
                                    .saveTokens(accessToken, refreshToken, telemetrySubject);
                        } catch (IllegalArgumentException | IllegalStateException error) {
                            onError(error.getMessage());
                            return;
                        }
                        runOnUiThread(() -> {
                            Toast.makeText(ToggLoginActivity.this,
                                    "Paylaşılan anahtar hesabı kaydedildi. Davet kabulü henüz tamamlanmadı.", Toast.LENGTH_LONG).show();
                            setResult(RESULT_OK);
                            finish();
                        });
                        return;
                    }
                    storage.setAccessToken(accessToken);
                    if (refreshToken != null && !refreshToken.isEmpty()) {
                        storage.setRefreshToken(refreshToken);
                    }
                    storage.setDemoMode(false);

                    runOnUiThread(() -> {
                        Toast.makeText(ToggLoginActivity.this, "Togg ID Girişi Başarılı! Canlı veriler bağlanıyor...", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    AppLog.event(ToggLoginActivity.this, "LOGIN", errorMessage);
                    runOnUiThread(() -> {
                        if (layoutLoading != null) layoutLoading.setVisibility(View.GONE);
                        if (webView != null) webView.setVisibility(View.VISIBLE);

                        new AlertDialog.Builder(ToggLoginActivity.this)
                                .setTitle("Giriş tamamlanamadı")
                                .setMessage("Oturum açılamadı. Bağlantınızı kontrol edip yeniden deneyin.")
                                .setPositiveButton("Tekrar Dene", (d, w) -> startAuthentication())
                                .setNegativeButton("Kapat", (d, w) -> finish())
                                .show();
                    });
                }
            });
        }
    }

    private void handleBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else finish();
    }
    @Override public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (android.os.Build.VERSION.SDK_INT < 33 && keyCode == android.view.KeyEvent.KEYCODE_BACK
                && webView != null && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyUp(keyCode, event);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
