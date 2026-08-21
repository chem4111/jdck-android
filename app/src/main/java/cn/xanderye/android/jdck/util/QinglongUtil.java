package cn.xanderye.android.jdck.util;

import cn.xanderye.android.jdck.entity.QlEnv;
import cn.xanderye.android.jdck.entity.QlInfo;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author XanderYe
 * @description:
 * @date 2022/5/11 14:04
 */
public class QinglongUtil {

    /** 统一去掉地址末尾的斜杠，避免生成 // 路径 */
    private static String normalizeAddress(String address) {
        if (address == null) return "";
        if (address.endsWith("/")) {
            address = address.substring(0, address.length() - 1);
        }
        return address;
    }

    /**
     * 安全地把 QlEnv._id（String）转为 int，满足青龙 array[integer] 的接口要求
     */
    private static Integer envIdToInt(String id) throws IOException {
        if (id == null || id.isEmpty()) {
            throw new IOException("环境变量ID为空");
        }
        try {
            return Integer.parseInt(id.trim());
        } catch (NumberFormatException e) {
            throw new IOException("环境变量ID格式错误: " + id);
        }
    }

    /**
     * 登录
     * @param qlInfo
     * @return java.lang.String
     * @description:
     * @date 2024/3/22 11:00
     */

    public static String login(QlInfo qlInfo) throws IOException {
        String url = normalizeAddress(qlInfo.getAddress());

        url += "/open/auth/token?client_id="+qlInfo.getUsername()+"&client_secret="+qlInfo.getPassword();

        HttpUtil.ResEntity resEntity = HttpUtil.doGet(url, null);
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        return res.getJSONObject("data").getString("token");
    }


    public static List<QlEnv> getEnvList(QlInfo qlInfo) throws IOException {
        String url = normalizeAddress(qlInfo.getAddress()) + "/api/envs";
        url += "?searchValue=&t=" + System.currentTimeMillis();
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        HttpUtil.ResEntity resEntity = HttpUtil.doGet(url, headers, null, null);
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        return res.getJSONArray("data").toJavaList(QlEnv.class);
    }
    /**
     * 获取环境变量

     * @date 2024/3/22 11:00
     */
    public static List<QlEnv> getEnvList(QlInfo qlInfo,String key) throws IOException {
        String url = normalizeAddress(qlInfo.getAddress()) + "/open/envs?searchValue="+key;
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        HttpUtil.ResEntity resEntity = HttpUtil.doGet(url, headers, null, null);
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        return res.getJSONArray("data").toJavaList(QlEnv.class);
    }
    /**
     * 更新环境变量
     * @param qlInfo
     * @param qlEnv
     * @return boolean
     * @author yclown
     * @description:
     * @date 2024/3/22 11:00
     */
    public static boolean saveEnv(QlInfo qlInfo, QlEnv qlEnv) throws IOException {
        String url = normalizeAddress(qlInfo.getAddress()) + "/open/envs";
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        JSONObject params = new JSONObject();
        params.put("name", qlEnv.getName());
        params.put("remarks", qlEnv.getRemarks());
        params.put("value", qlEnv.getValue());
        HttpUtil.ResEntity resEntity;
        if (qlEnv.get_id() != null) {
            // 更新 —— id 必须是 integer，否则青龙报参数错误
            params.put("id", envIdToInt(qlEnv.get_id()));
            resEntity = HttpUtil.doPutJSON(url, headers, null, params.toJSONString());

        } else {
            // 新增
            JSONArray adds=new JSONArray();
            adds.add(params);
            resEntity = HttpUtil.doPostJSON(url, headers, null, adds.toJSONString());
        }
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("发送 服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        // 新增成功后把青龙返回的 _id 回写到 qlEnv，便于紧接着 enable
        if (qlEnv.get_id() == null) {
            JSONArray data = res.getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                JSONObject created = data.getJSONObject(0);
                if (created != null) {
                    Object idObj = created.get("id");
                    if (idObj != null) {
                        qlEnv.set_id(String.valueOf(idObj));
                    }
                }
            }
        }
        return true;
    }
    /**
     * 启用环境变量
     * @param qlInfo
     * @param qlEnv
     * @author yclown
     * @description:
     * @date 2024/3/22 11:00
     */
    public static void EableEnv(QlInfo qlInfo,QlEnv qlEnv) throws IOException {
        String url = normalizeAddress(qlInfo.getAddress()) + "/open/envs/enable";
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        HttpUtil.ResEntity resEntity;
        if (qlEnv.get_id() != null) {
            // 青龙要求 array[integer]，把 String 类型的 _id 转成 int
            JSONArray ja=new JSONArray();
            ja.add(envIdToInt(qlEnv.get_id()));

            resEntity = HttpUtil.doPutJSON(url, headers, null, ja.toJSONString());
            if (resEntity.getStatusCode() != 200) {
                throw new IOException("启用 服务器" + resEntity.getStatusCode() + "错误");
            }
            JSONObject res = JSON.parseObject(resEntity.getResponse());
            if (res.getInteger("code") != 200) {
                throw new IOException(res.getString("message"));
            }
        }
        return ;
    }

    /**
     * 获取所有定时任务 (增加搜索关键词以提升速度)
     */
    public static JSONArray getCrons(QlInfo qlInfo, String searchValue) throws IOException {
        String url = normalizeAddress(qlInfo.getAddress()) + "/open/crons?searchValue=" + (searchValue != null ? searchValue : "");
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        HttpUtil.ResEntity resEntity = HttpUtil.doGet(url, headers, null, null);
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        Object dataObj = res.get("data");
        if (dataObj instanceof JSONArray) {
            return (JSONArray) dataObj;
        } else if (dataObj instanceof JSONObject) {
            return ((JSONObject) dataObj).getJSONArray("data");
        }
        return new JSONArray();
    }

    /**
     * 获取任务日志内容
     */
    public static String getCronLog(QlInfo qlInfo, String taskId) throws IOException {
        String url = normalizeAddress(qlInfo.getAddress()) + "/open/crons/" + taskId + "/log";
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        HttpUtil.ResEntity resEntity = HttpUtil.doGet(url, headers, null, null);
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        return res.getString("data");
    }
}
