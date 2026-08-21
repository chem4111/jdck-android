package cn.xanderye.android.jdck.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
// ...existing code...
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import cn.xanderye.android.jdck.R;
import cn.xanderye.android.jdck.config.Config;
import cn.xanderye.android.jdck.entity.QlEnv;
import cn.xanderye.android.jdck.entity.QlInfo;
import cn.xanderye.android.jdck.util.HttpUtil;
import cn.xanderye.android.jdck.util.QinglongUtil;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @origin XanderYe
 * @author yclown
 * @description:
 * @date 2024/3/22 11:00
 */
public class LoginActivity extends AppCompatActivity {

    private Context context;

    private SharedPreferences config;

    // ...existing code...


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 如果应用已经在跑（MainActivity 在前台/后台栈中），直接走已登录流程
        // 这是为了避免从桌面点图标时又走一遍自动登录，浪费时间
        QlInfo alreadyInConfig = Config.getInstance().getQlInfo();
        if (alreadyInConfig != null && StringUtils.isNotBlank(alreadyInConfig.getToken())) {
            startActivity(new Intent(this, MainActivity.class));
            this.finish();
            return;
        }

        setContentView(R.layout.activity_login);
        context = this;

        // 配置存储
        config = getSharedPreferences("CONFIG", Context.MODE_PRIVATE);

        // ====== 关键：只在第一次写默认值 ======
        if (!config.contains("qlJSON")) {
            QlInfo defaultInfo = new QlInfo();
            defaultInfo.setAddress("https://chem4111.dpdns.org");
            defaultInfo.setUsername("TfNzmuyQ9wV_");
            defaultInfo.setPassword("I07-jbDwhFbK9F5u6gnvhvvu");

            config.edit()
                    .putString("qlJSON", JSON.toJSONString(defaultInfo))
                    .apply();
        }

        String qlJSON = config.getString("qlJSON", null);
        QlInfo qlInfo = new QlInfo("", true, "", "", "");
        if (qlJSON != null) {
            qlInfo = JSON.parseObject(qlJSON, QlInfo.class);
            Config.getInstance().setQlInfo(qlInfo);
        }

        // 如果 SharedPreferences 里已经存过带 token 的 qlInfo（上次登录成功过），
        // 且 token 非空，直接跳过网络请求进入 Main，省一次等待
        if (qlInfo != null && StringUtils.isNotBlank(qlInfo.getToken())) {
            runOnUiThread(() -> toMainActivity());
            return;
        }

        // 自动登录逻辑
        QlInfo finalQlInfo = qlInfo;
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        singleThreadExecutor.execute(() -> {
            // 自动登录使用极速超时
            try {
                String tk = QinglongUtil.login(finalQlInfo);
                if (StringUtils.isBlank(tk)) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "自动登录失败，token为空", Toast.LENGTH_SHORT).show();
                        toMainActivity();
                    });
                    return;
                }
                finalQlInfo.setToken(tk);
                //登陆后 更新环境变量
                List<QlEnv> qlEnvList = QinglongUtil.getEnvList(finalQlInfo,"");
                Config.getInstance().setQlEnvList(qlEnvList);
                runOnUiThread(() -> {
                    try {
                        loginSuccess(finalQlInfo);
                    } catch (IOException e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                        toMainActivity();
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    // 自动登录失败也进入主界面，用户仍可在 WebView 里手动登录 JD 并复制 CK
                    toMainActivity();
                });
            } finally {
                HttpUtil.setTimeout(30000, 30000);
            }
        });
        singleThreadExecutor.shutdown();
    }
    

    /** 直接跳转到主界面（自动登录失败或不想用时的 fallback） */
    private void toMainActivity() {
        startActivity(new android.content.Intent(this, MainActivity.class));
        this.finish();
    }

    private void loginSuccess(QlInfo qlInfo) throws IOException {
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
        // 存储内存
        Config.getInstance().setQlInfo(qlInfo);
        // 数据持久化
        SharedPreferences.Editor edit = config.edit();
        edit.putString("qlJSON", JSON.toJSONString(qlInfo));
        edit.apply();
        // 跳转到主界面
        startActivity(new android.content.Intent(this, MainActivity.class));
        this.finish();
    }
}
