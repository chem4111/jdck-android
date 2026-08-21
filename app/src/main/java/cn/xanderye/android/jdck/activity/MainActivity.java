package cn.xanderye.android.jdck.activity;

import android.content.*;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.*;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import cn.xanderye.android.jdck.R;
import cn.xanderye.android.jdck.config.Config;
import cn.xanderye.android.jdck.entity.QlEnv;
import cn.xanderye.android.jdck.entity.QlInfo;
import cn.xanderye.android.jdck.util.JDUtil;
import cn.xanderye.android.jdck.util.QinglongUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import android.util.Log;

/**
 * 精简版 MainActivity：只做一件事 —— WebView 打开京东登录页，
 * 用户登录后点"获取CK"复制 pt_key/pt_pin 到剪贴板，并推送到青龙；
 * 点"重置"才清 cookie 退出登录，其它任何切页/退出/切后台/被杀回来都保持登录。
 */
public class MainActivity extends AppCompatActivity {

    private Button getCookieBtn, assetQueryBtn, clearCookieBtn;
    private WebView webView;
    private AlertDialog progressDialog;

    // 基础地址与回调配置
    private static final String JD_LOGIN_BASE = "https://plogin.m.jd.com/login/login";
    private static final String DEFAULT_CALLBACK = "https://m.jd.com/";
    
    private static final String ASSET_TASK_CMD = "task 6dylan6_jdpro/jd_bean_change.js";

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

        // 资产查询
        assetQueryBtn = findViewById(R.id.assetQueryBtn);
        assetQueryBtn.setOnClickListener(v -> {
            String currentUrl = webView.getUrl();
            String cookie = CookieManager.getInstance().getCookie(currentUrl);
            Map<String, Object> map = JDUtil.formatCookies(cookie);
            String ptPin = (String) map.get("pt_pin");
            if (StringUtils.isBlank(ptPin)) {
                Toast.makeText(this, "未获取到 pt_pin，请确保已登录", Toast.LENGTH_SHORT).show();
                return;
            }
            fetchAssetInfo(ptPin);
        });

        // 重置
        clearCookieBtn = findViewById(R.id.clearCookieBtn);
        clearCookieBtn.setOnClickListener(v -> resetWebview());
    }

    /** 获取资产信息 */
    private void fetchAssetInfo(String ptPin) {
        QlInfo qlInfo = Config.getInstance().getQlInfo();
        if (qlInfo == null || StringUtils.isBlank(qlInfo.getToken())) {
            Toast.makeText(this, "未登录青龙面板，无法查询", Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用自定义现代化加载 UI
        View loadingView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
        TextView msgTv = loadingView.findViewById(R.id.loading_msg);
        msgTv.setText("正在同步云端资产…");
        
        progressDialog = new AlertDialog.Builder(this)
                .setView(loadingView)
                .setCancelable(false)
                .create();
        
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        progressDialog.show();

        executorService.execute(() -> {
            try {
                long startTime = System.currentTimeMillis();
                // 1. 精准搜索资产统计任务 (极大减少数据传输量)
                JSONArray crons = QinglongUtil.getCrons(qlInfo, "jd_bean_change.js");
                Log.d("AssetQuery", "获取任务列表耗时: " + (System.currentTimeMillis() - startTime) + "ms");
                
                String taskId = null;
                for (int i = 0; i < crons.size(); i++) {
                    JSONObject cron = crons.getJSONObject(i);
                    String command = cron.getString("command");
                    if (command != null && command.contains("jd_bean_change.js")) {
                        taskId = String.valueOf(cron.get("id") != null ? cron.get("id") : cron.get("_id"));
                        break;
                    }
                }

                if (taskId == null) {
                    throw new Exception("未在青龙中找到资产统计任务 [jd_bean_change.js]");
                }

                // 2. 获取日志内容
                long logStartTime = System.currentTimeMillis();
                String log = QinglongUtil.getCronLog(qlInfo, taskId);
                Log.d("AssetQuery", "获取日志耗时: " + (System.currentTimeMillis() - logStartTime) + "ms");
                if (StringUtils.isBlank(log)) {
                    throw new Exception("任务日志为空，请先在青龙手动执行一次统计任务");
                }

                // 3. 解析属于当前 pt_pin 的日志段
                String userLog = extractUserLog(log, ptPin);
                
                runOnUiThread(() -> {
                    if (progressDialog != null) progressDialog.dismiss();
                    showLogDialog(userLog);
                });

            } catch (Exception e) {
                Log.e("AssetQuery", "查询资产异常", e);
                String fullError = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> {
                    if (progressDialog != null) progressDialog.dismiss();
                    Toast.makeText(this, "查询失败: " + fullError, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** 提取指定用户的日志段 */
    private String extractUserLog(String fullLog, String ptPin) {
        String separator = "----------开始查询";
        // 查找包含该账号的行
        int targetIndex = fullLog.indexOf(ptPin);
        if (targetIndex == -1) {
            return "未在日志中找到您的资产信息 [" + ptPin + "]，请确认脚本已统计该账号。";
        }
        
        // 找到该账号对应的分隔符位置（向上找）
        int start = fullLog.lastIndexOf(separator, targetIndex);
        if (start == -1) {
            return "日志格式不匹配，未找到资产信息。";
        }
        
        // 找到下一个分隔符或日志结束位置（向下找）
        int end = fullLog.indexOf(separator, start + separator.length());
        if (end == -1) {
            // 如果没有下一个用户，尝试查找统计结束标志
            end = fullLog.indexOf("添加缓存", start);
            if (end == -1) end = fullLog.length();
        }
        
        return fullLog.substring(start, end).trim();
    }

    /** 显示日志弹窗 */
    private void showLogDialog(String content) {
        View resultView = LayoutInflater.from(this).inflate(R.layout.dialog_asset_result, null);
        TextView contentTv = resultView.findViewById(R.id.asset_content);
        contentTv.setText(content);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(resultView)
                .create();

        resultView.findViewById(R.id.btn_close).setOnClickListener(v -> dialog.dismiss());

        // 设置圆角背景
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.show();
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
        cookieManager.removeAllCookies(value -> {});
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
