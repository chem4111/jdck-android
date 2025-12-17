package cn.xanderye.android.jdck.activity;

import android.content.Context;
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

        // 自动登录逻辑
        QlInfo finalQlInfo = qlInfo;
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        singleThreadExecutor.execute(() -> {
            try {
                String tk = QinglongUtil.login(finalQlInfo);
                if (StringUtils.isBlank(tk)) {
                    runOnUiThread(() -> Toast.makeText(this, "自动登录失败，token为空", Toast.LENGTH_SHORT).show());
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
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
        singleThreadExecutor.shutdown();
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
