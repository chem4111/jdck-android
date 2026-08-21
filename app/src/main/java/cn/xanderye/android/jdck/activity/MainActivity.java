package cn.xanderye.android.jdck.activity;

import android.content.*;
import android.os.Bundle;
import android.webkit.*;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import cn.xanderye.android.jdck.R;
import cn.xanderye.android.jdck.config.Config;
import cn.xanderye.android.jdck.entity.QlEnv;
import cn.xanderye.android.jdck.entity.QlInfo;
import cn.xanderye.android.jdck.util.JDUtil;
import cn.xanderye.android.jdck.util.QinglongUtil;
import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 精简版 MainActivity：只做一件事 —— WebView 打开京东登录页，
 * 用户登录后点"获取CK"复制 pt_key/pt_pin 到剪贴板，并推送到青龙；
 * 点"重置"才清 cookie 退出登录，其它任何切页/退出/切后台/被杀回来都保持登录。
 */
public class MainActivity extends AppCompatActivity {

    private Button getCookieBtn, clearCookieBtn;
    private WebView webView;

    // 基础地址与回调配置
    private static final String JD_LOGIN_BASE = "https://plogin.m.jd.com/login/login";
    private static final String DEFAULT_CALLBACK = "https://m.jd.com/";
    
    private static final String KEY_WEBVIEW_STATE = "web_view_state";

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView(savedInstanceState);
    }

    private void initView(Bundle savedInstanceState) {
        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // 仅在主框架加载失败时提示，忽略第三方脚本或图片的网络报错
                if (request.isForMainFrame()) {
                    Toast.makeText(view.getContext(), "主页面加载失败，请检查网络", Toast.LENGTH_SHORT).show();
                }
                super.onReceivedError(view, request, error);
            }
        });

        // 恢复状态或加载初始 URL
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_WEBVIEW_STATE)) {
            Bundle webState = savedInstanceState.getBundle(KEY_WEBVIEW_STATE);
            if (webState != null) {
                webView.restoreState(webState);
            } else {
                loadJdLogin();
            }
        } else {
            loadJdLogin();
        }

        // 获取 CK（复制 + 推送青龙）
        getCookieBtn = findViewById(R.id.getCookieBtn);
        getCookieBtn.setOnClickListener(v -> {
            String currentUrl = webView.getUrl();
            String cookie = CookieManager.getInstance().getCookie(currentUrl);
            
            Map<String, Object> map = JDUtil.formatCookies(cookie);
            String ptKey = (String) map.get("pt_key");
            String ptPin = (String) map.get("pt_pin");
            
            if (StringUtils.isAnyBlank(ptKey, ptPin)) {
                Toast.makeText(this, "未获取到完整Cookie，请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String ck = MessageFormat.format("pt_key={0};pt_pin={1};", ptKey, ptPin);
            if (copyToClipboard(ck)) {
                Toast.makeText(this, "已复制并开始推送青龙", Toast.LENGTH_SHORT).show();
                // 推送到青龙
                pushToQinglong(ptPin, ck);
            }
        });

        // 重置
        clearCookieBtn = findViewById(R.id.clearCookieBtn);
        clearCookieBtn.setOnClickListener(v -> resetWebview());
    }

    /** 推送 Cookie 到青龙面板 */
    private void pushToQinglong(String ptPin, String cookieValue) {
        QlInfo qlInfo = Config.getInstance().getQlInfo();
        if (qlInfo == null || StringUtils.isBlank(qlInfo.getToken())) {
            Toast.makeText(this, "推送失败：未登录青龙面板", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            try {
                // 1. 获取所有环境变量，寻找匹配该账号的 JD_COOKIE
                List<QlEnv> envList = QinglongUtil.getEnvList(qlInfo, "JD_COOKIE");
                QlEnv targetEnv = null;
                for (QlEnv env : envList) {
                    if (env.getValue() != null && env.getValue().contains("pt_pin=" + ptPin)) {
                        targetEnv = env;
                        break;
                    }
                }

                if (targetEnv == null) {
                    // 新增
                    targetEnv = new QlEnv();
                    targetEnv.setName("JD_COOKIE");
                    targetEnv.setValue(cookieValue);
                    targetEnv.setRemarks("app:" + ptPin);
                } else {
                    // 更新
                    targetEnv.setValue(cookieValue);
                    targetEnv.setRemarks("app:" + ptPin);
                }

                // 2. 保存/更新
                QinglongUtil.saveEnv(qlInfo, targetEnv);
                // 3. 启用
                QinglongUtil.EableEnv(qlInfo, targetEnv);

                runOnUiThread(() -> Toast.makeText(this, "青龙推送成功！", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "推送出错: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void loadJdLogin() {
        try {
            String url = JD_LOGIN_BASE + "?appid=300&returnurl=" + URLEncoder.encode(DEFAULT_CALLBACK, "UTF-8");
            webView.loadUrl(url);
        } catch (Exception e) {
            webView.loadUrl(JD_LOGIN_BASE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Bundle webState = new Bundle();
        webView.saveState(webState);
        outState.putBundle(KEY_WEBVIEW_STATE, webState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void resetWebview() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
        
        webView.clearHistory();
        webView.clearCache(true);
        webView.evaluateJavascript("try { localStorage.clear(); sessionStorage.clear(); } catch (e) {}", null);
        
        loadJdLogin();
        Toast.makeText(this, "已重置登录状态", Toast.LENGTH_SHORT).show();
    }

    private boolean copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("JDCK", text);
            cm.setPrimaryClip(clip);
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
