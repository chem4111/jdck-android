package cn.xanderye.android.jdck.activity;

import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import cn.xanderye.android.jdck.R;
import cn.xanderye.android.jdck.config.Config;
import cn.xanderye.android.jdck.entity.QlEnv;
import cn.xanderye.android.jdck.entity.QlInfo;
import cn.xanderye.android.jdck.util.JDUtil;
import cn.xanderye.android.jdck.util.QinglongUtil;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private Context context;

    private Button addBtn, delBtn, inputBtn, getCookieBtn, clearCookieBtn,sendCK;

    private Spinner phoneSpinner;

    private WebView webView;

    // private static final String JD_URL = "https://home.m.jd.com/myJd/home.action";
    private static final String JD_URL = "https://plogin.m.jd.com/login/login";

    private static final Pattern PHONE_PATTERN = Pattern.compile("1\\d{10}");

    private SharedPreferences config;

    private String cookie = null;

    private Set<String> phoneSet = new HashSet<>();

    // ...existing code...


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        // 配置存储
        config = getSharedPreferences("CONFIG", Context.MODE_PRIVATE);
        // ...existing code...

        webView = findViewById(R.id.webView);
        Config.getInstance().setWebView(webView);
        //支持javascript
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        //自适应屏幕
        webView.getSettings().setLoadWithOverviewMode(true);
        resetWebview();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                //添加Cookie获取操作
                CookieManager cookieManager = CookieManager.getInstance();
                cookie = cookieManager.getCookie(url);
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Toast.makeText(view.getContext(), "网页加载失败，请检查网络或目标地址！", Toast.LENGTH_LONG).show();
                super.onReceivedError(view, request, error);
            }
        });
        // 配置账号下拉框
        phoneSpinner = findViewById(R.id.phoneSpinner);
        // 添加按钮
        addBtn = findViewById(R.id.addBtn);
        addBtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final AlertDialog dialog = builder.create();
            View dialogView = View.inflate(context, R.layout.activity_phone, null);
            //设置对话框布局
            dialog.setView(dialogView);
            dialog.show();

            EditText phoneText = dialogView.findViewById(R.id.phoneText);
            Button confirmBtn = dialogView.findViewById(R.id.confirmBtn);
            confirmBtn.setOnClickListener(v2 -> {
                String phone = phoneText.getText().toString();
                if (StringUtils.isBlank(phone)) {
                    Toast.makeText(this, "账号输入错误", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.cancel();
                phoneSet.add(phone);
                updatePhone();
                Toast.makeText(this, "添加成功", Toast.LENGTH_SHORT).show();
            });
        });
        // 删除按钮
        delBtn = findViewById(R.id.delBtn);
        delBtn.setOnClickListener(v -> {
            String selectedPhone = (String) phoneSpinner.getSelectedItem();
            if (selectedPhone == null) {
                Toast.makeText(this, "请先选择账号", Toast.LENGTH_SHORT).show();
                return;
            }
            phoneSet = phoneSet.stream().filter(phone -> !phone.equals(selectedPhone)).collect(Collectors.toSet());
            // 更新账号
            updatePhone();
            Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
        });

        // 一键输入按钮
        inputBtn = findViewById(R.id.inputBtn);
        inputBtn.setOnClickListener(v -> {
            String selectedPhone = (String) phoneSpinner.getSelectedItem();
            if (selectedPhone == null) {
                Toast.makeText(this, "请先选择账号", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] info=  selectedPhone.split(" ");
            String Phone=info[0];
            String execJs = "var account='" + Phone + "';";
            execJs += "document.getElementsByClassName('policy_tip-checkbox')[0].click();";
            execJs += "var evt=new InputEvent('input',{inputType:'insertText',data:account,dataTransfer:null,isComposing:false});";
            execJs += "document.getElementById('username').value=account;";
            execJs += "document.getElementById('username').dispatchEvent(evt);";
            Matcher matcher = PHONE_PATTERN.matcher(Phone);
            if (matcher.matches()) {
                execJs += "document.getElementsByClassName('acc-input mobile J_ping')[0].value=account;";
                execJs += "document.getElementsByClassName('acc-input mobile J_ping')[0].dispatchEvent(evt);";
            }
            if(info.length>1){
                String Pwd=info[1];
                execJs += "var password='" + Pwd + "';";
                execJs += "var evt=new InputEvent('input',{inputType:'insertText',data:password,dataTransfer:null,isComposing:false});";
                execJs += "document.getElementById('pwd').value=password;";
                execJs += "document.getElementById('pwd').dispatchEvent(evt);";
                execJs += "document.querySelector('#app>div>a').click()";
            }

            webView.loadUrl("javascript:" + execJs);
        });
        // 获取cookie按钮
        getCookieBtn = findViewById(R.id.getCookieBtn);
        getCookieBtn.setOnClickListener(v -> {
            Map<String, Object> map = JDUtil.formatCookies(cookie);
            String ptKey = (String) map.get("pt_key");
            String ptPin = (String) map.get("pt_pin");
            if (StringUtils.isAnyBlank(ptKey, ptPin)) {
                Toast.makeText(this, "未获取到Cookie，请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedPhone = (String) phoneSpinner.getSelectedItem();
            String Phone="";
            if (selectedPhone != null) {
                String[] info=  selectedPhone.split(" ");
                Phone=info[0];
            }

            String cookie = MessageFormat.format("pt_key={0};pt_pin={1};", ptKey, ptPin);
            QlInfo qlInfo = Config.getInstance().getQlInfo();
            copyToClipboard(cookie);
            if (qlInfo == null || qlInfo.getToken() == null) {
                Toast.makeText(this, "获取成功，已复制到剪切板", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "获取成功，已复制到剪切板，尝试自动更新青龙面板环境变量", Toast.LENGTH_SHORT).show();
                updateCookie(cookie,Phone);
            }
        });
        // 重置cookie刷新页面按钮
        clearCookieBtn = findViewById(R.id.clearCookieBtn);
        clearCookieBtn.setOnClickListener(v -> {
//            updateCookie("pt_key=test;pt_pin=Yclown;");
            resetWebview();
        });

        // 检查token有效 同时获取环境变量
        checkQlLogin();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    /**
     * 清空cookie并加载页面
     * @param
     * @return void
     * @author XanderYe
     * @date 2022/5/10
     */
    private void resetWebview() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
        webView.clearCache(true);
        webView.loadUrl(JD_URL);
    }

    /**
     * 更新下拉和存储中的账号
     * @return void
     * @author XanderYe
     * @date 2022/5/10
     */
    private void updatePhone() {
        // 更新账号
        String newPhoneStr = phoneSet.stream().collect(Collectors.joining("\r\n"));
        SharedPreferences.Editor edit = config.edit();
        edit.putString("phoneStr", newPhoneStr);
        edit.apply();
        String[] phones = phoneSet.toArray(new String[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, phones);
        phoneSpinner.setAdapter(adapter);
    }

    /**
     * 复制文字到剪切板
     * @param copyStr
     * @return boolean
     * @author XanderYe
     * @date 2022/5/10
     */
    private boolean copyToClipboard(String copyStr) {
        try {
            //获取剪贴板管理器
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建普通字符型ClipData
            ClipData mClipData = ClipData.newPlainText("Label", copyStr);
            // 将ClipData内容放到系统剪贴板里。
            cm.setPrimaryClip(mClipData);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkQlLogin() {
        String qlJSON = config.getString("qlJSON", null);
        if (qlJSON == null) {
            return;
        }
        QlInfo qlInfo = JSON.parseObject(qlJSON, QlInfo.class);
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        singleThreadExecutor.execute(() -> {
            Looper.prepare();


            try {
                List<QlEnv> qlEnvList = QinglongUtil.getEnvList(qlInfo,"");
                Config.getInstance().setQlEnvList(qlEnvList);
                Config.getInstance().setQlInfo(qlInfo);
                Toast.makeText(this, "青龙token有效", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "青龙token已失效，请重新登录", Toast.LENGTH_SHORT).show();
            }
            Looper.loop();
        });
        singleThreadExecutor.shutdown();
    }

    /**
     * 调用青龙接口更新cookie
     * @param cookie
     * @return void
     * @author XanderYe
     * @date 2022/5/11
     */
    private void updateCookie(String cookie,String phone) {
        Map<String, Object> map = JDUtil.formatCookies(cookie);
        String ptPin = (String) map.get("pt_pin");
        QlInfo qlInfo = Config.getInstance().getQlInfo();
        List<QlEnv> qlEnvList =Config.getInstance().getQlEnvList();
        if(qlEnvList==null){
            qlEnvList=new ArrayList<QlEnv>();
        }
        QlEnv targetEnv = null;
        for (QlEnv qlEnv : qlEnvList) {
            Map<String, Object> envMap = JDUtil.formatCookies(qlEnv.getValue());
            String tempPin = (String) envMap.get("pt_pin");
            if(ptPin.equals(tempPin)) {
                targetEnv = qlEnv;
                break;
            }
        }
        if (targetEnv == null) {
            targetEnv = new QlEnv();
            targetEnv.setName("JD_COOKIE");
        }
        targetEnv.setValue(cookie);
        targetEnv.setRemarks(phone);
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        QlEnv finalTargetEnv = targetEnv;
        singleThreadExecutor.execute(() -> {
            Looper.prepare();
            try {
                boolean success = QinglongUtil.saveEnv(qlInfo, finalTargetEnv);
                QinglongUtil.EableEnv(qlInfo,finalTargetEnv);
                if (success) {
                    Toast.makeText(this, "更新cookie成功", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            Looper.loop();
        });
        singleThreadExecutor.shutdown();

    }
}