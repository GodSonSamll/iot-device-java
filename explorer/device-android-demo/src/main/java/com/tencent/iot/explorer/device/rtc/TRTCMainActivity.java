package com.tencent.iot.explorer.device.rtc;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.PermissionUtils;
import com.tencent.iot.explorer.device.android.app.BuildConfig;
import com.tencent.iot.explorer.device.android.app.R;
import com.tencent.iot.explorer.device.android.utils.TXLog;
import com.tencent.iot.explorer.device.java.data_template.TXDataTemplateDownStreamCallBack;
import com.tencent.iot.explorer.device.java.mqtt.TXMqttRequest;
import com.tencent.iot.explorer.device.rtc.adapter.UserListAdapter;
import com.tencent.iot.explorer.device.rtc.data_template.TRTCCallStatus;
import com.tencent.iot.explorer.device.rtc.data_template.TRTCDataTemplateSample;
import com.tencent.iot.explorer.device.rtc.data_template.TRTCExplorerDemoSessionManager;
import com.tencent.iot.explorer.device.rtc.data_template.TXTRTCCallBack;
import com.tencent.iot.explorer.device.rtc.data_template.model.RoomKey;
import com.tencent.iot.explorer.device.rtc.data_template.model.TRTCCalling;
import com.tencent.iot.explorer.device.rtc.data_template.model.TRTCUIManager;
import com.tencent.iot.explorer.device.rtc.data_template.ui.audiocall.TRTCAudioCallActivity;
import com.tencent.iot.explorer.device.rtc.data_template.ui.videocall.TRTCVideoCallActivity;
import com.tencent.iot.explorer.device.rtc.entity.UserEntity;
import com.tencent.iot.explorer.device.rtc.utils.ZXingUtils;
import com.tencent.iot.hub.device.java.core.common.Status;
import com.tencent.iot.hub.device.java.core.mqtt.TXMqttActionCallBack;

import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import static com.tencent.iot.explorer.device.rtc.data_template.model.TXTRTCDataTemplateConstants.PROPERTY_SYS_CALL_USERLIST;
import static com.tencent.iot.explorer.device.rtc.data_template.model.TXTRTCDataTemplateConstants.PROPERTY_SYS_CALL_USERLIST_NICKNAME;
import static com.tencent.iot.explorer.device.rtc.data_template.model.TXTRTCDataTemplateConstants.PROPERTY_SYS_CALL_USERLIST_USERID;

public class TRTCMainActivity extends AppCompatActivity {

    private static final String TAG = "TRTCMainActivity";

    private TRTCDataTemplateSample mDataTemplateSample;

    private ImageView mQRCodeImgView;
    private Button mConnectBtn;
    private Button mCloseConnectBtn;
    private Button mAudioCallBtn;
    private Button mVideoCallBtn;

    private EditText mBrokerURLEditText;
    private EditText mProductIdEditText;
    private EditText mDevNameEditText;
    private EditText mDevPSKEditText;
    private Button mGeneralQRCodeBtn;

    private TextView mLogInfoText;

    private RecyclerView mRecyclerView = null;
    private UserListAdapter mAdapter = null;
    private ArrayList<UserEntity> mDatas = null;

    // Default testing parameters
    private String mBrokerURL = null;  //传入null，即使用腾讯云物联网通信默认地址 "${ProductId}.iotcloud.tencentdevices.com:8883"  https://cloud.tencent.com/document/product/634/32546
    private String mProductID = BuildConfig.SUB_PRODUCT_ID;
    private String mDevName = BuildConfig.SUB_DEV_NAME;
    private String mDevPSK  = BuildConfig.SUB_DEV_PSK; //若使用证书验证，设为null

    private String mDevCert = "";           // Cert String
    private String mDevPriv = "";           // Priv String

    private Integer mCallType = TRTCCalling.TYPE_UNKNOWN;
    private Integer mCallMobileNumber = 0;

    private final static String mJsonFileName = "TRTC_watch.json";

    private static final int REQUEST_PERMISSTION = 1;

    private final static String BROKER_URL = "broker_url";
    private final static String PRODUCT_ID = "product_id";
    private final static String DEVICE_NAME = "dev_name";
    private final static String DEVICE_PSK = "dev_psk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trtc_main);
        MultiDex.install(this);

        mQRCodeImgView = findViewById(R.id.iv_qrcode);
        mConnectBtn = findViewById(R.id.connect);
        mCloseConnectBtn = findViewById(R.id.close_connect);
        mAudioCallBtn = findViewById(R.id.select_audio_call);
        mVideoCallBtn = findViewById(R.id.select_video_call);
        mLogInfoText = findViewById(R.id.log_info);

        mBrokerURLEditText = findViewById(R.id.et_broker_url);
        mProductIdEditText = findViewById(R.id.et_productId);
        mDevNameEditText = findViewById(R.id.et_deviceName);
        mDevPSKEditText = findViewById(R.id.et_devicePsk);
        mGeneralQRCodeBtn = findViewById(R.id.qrcode);

        // 获取组件
        mRecyclerView = (RecyclerView) findViewById(R.id.rv_user_list);

        // 设置管理器
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);
        // 如果可以确定每个item的高度是固定的，设置这个选项可以提高性能
        mRecyclerView.setHasFixedSize(true);
        mDatas = new ArrayList<UserEntity>();
        // 设置适配器，刷新展示用户列表
        mAdapter = new UserListAdapter(TRTCMainActivity.this, mDatas);
        mRecyclerView.setAdapter(mAdapter);
        SharedPreferences settings = getSharedPreferences("rtc_config", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = settings.edit();
        mBrokerURL = settings.getString(BROKER_URL, mBrokerURL);
        mProductID = settings.getString(PRODUCT_ID, mProductID);
        mDevName = settings.getString(DEVICE_NAME, mDevName);
        mDevPSK = settings.getString(DEVICE_PSK, mDevPSK);
        editor.commit();

        if (!mProductID.equals("")) {
            mProductIdEditText.setText(mProductID);
        }

        if (!mDevName.equals("")) {
            mDevNameEditText.setText(mDevName);
        }

        if (!mDevPSK.equals("")) {
            mDevPSKEditText.setText(mDevPSK);
        }

        mConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!checkInput()) {
                    return;
                }
                SharedPreferences settings = getSharedPreferences("rtc_config", Context.MODE_PRIVATE);

                SharedPreferences.Editor editor = settings.edit();
                editor.putString(BROKER_URL, mBrokerURL);
                editor.putString(PRODUCT_ID, mProductID);
                editor.putString(DEVICE_NAME, mDevName);
                editor.putString(DEVICE_PSK, mDevPSK);
                editor.commit();
                if (mDataTemplateSample != null) {
                    return;
                }
                mDataTemplateSample = new TRTCDataTemplateSample(TRTCMainActivity.this, mBrokerURL, mProductID, mDevName, mDevPSK, new SelfMqttActionCallBack(), mJsonFileName, new SelfDownStreamCallBack(), new TRTCCallBack());
                mDataTemplateSample.connect();
            }
        });

        mCloseConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDataTemplateSample == null)
                    return;
                mDataTemplateSample.disconnect();
                mDataTemplateSample = null;
            }
        });

        mVideoCallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDataTemplateSample == null)
                    return;
                TRTCUIManager.getInstance().callMobile = true;
                String userId = selectedUserIds();
                mDataTemplateSample.reportCallStatusProperty(TRTCCallStatus.TYPE_CALLING, TRTCCalling.TYPE_VIDEO_CALL, userId);//后续要从_sys_call_userlist选取传递userid
                TRTCUIManager.getInstance().setSessionManager(new TRTCExplorerDemoSessionManager(mDataTemplateSample));
                TRTCUIManager.getInstance().isCalling = true;
                TRTCVideoCallActivity.startCallSomeone(TRTCMainActivity.this, new RoomKey(), userId);
            }
        });
        mAudioCallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mDataTemplateSample == null)
                    return;
                TRTCUIManager.getInstance().callMobile = true;
                String userId = selectedUserIds();
                mDataTemplateSample.reportCallStatusProperty(TRTCCallStatus.TYPE_CALLING, TRTCCalling.TYPE_AUDIO_CALL,userId);//后续要从_sys_call_userlist选取传递userid
                TRTCUIManager.getInstance().setSessionManager(new TRTCExplorerDemoSessionManager(mDataTemplateSample));
                TRTCUIManager.getInstance().isCalling = true;
                TRTCAudioCallActivity.startCallSomeone(TRTCMainActivity.this, new RoomKey(), userId);
            }
        });
        mGeneralQRCodeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        initPermission();
    }

    private String selectedUserIds() {
        String userIds = "";
        Integer callMobileNumber = 0;
        for (int i = 0; i < mDatas.size(); i++) {
            UserEntity user = mDatas.get(i);
            if (user.getIsSelect()) { //被勾选将要发起通话请求
                userIds = userIds + user.getUserid() + ";";
                callMobileNumber++;
            }
        }
        if (userIds.length() > 0) {
             userIds = userIds.substring(0, userIds.length() - 1);
        }
        if (callMobileNumber == 0) {
            mCallMobileNumber = mDatas.size();
        } else {
            mCallMobileNumber = callMobileNumber;
        }
        return userIds;
    }

    private boolean checkInput() {
        String inputBrokerURL = String.valueOf(mBrokerURLEditText.getText());
        if (inputBrokerURL.equals("")) {
        } else {
            mBrokerURL = inputBrokerURL;
        }

        String inputProductId = String.valueOf(mProductIdEditText.getText());
        if (inputProductId.equals("")) {
            Toast toast = Toast.makeText(getApplicationContext(), "请输入productId", Toast.LENGTH_LONG);
            toast.show();
            return false;
        } else {
            mProductID = inputProductId;
        }

        String inputDevName = String.valueOf(mDevNameEditText.getText());
        if (inputDevName.equals("")) {
            Toast toast = Toast.makeText(getApplicationContext(), "请输入deviceName", Toast.LENGTH_LONG);
            toast.show();
            return false;
        } else {
            mDevName = inputDevName;
        }

        String inputDevPSK = String.valueOf(mDevPSKEditText.getText());
        if (inputDevPSK.equals("")) {
            Toast toast = Toast.makeText(getApplicationContext(), "请输入devicePsk", Toast.LENGTH_LONG);
            toast.show();
            return false;
        } else {
            mDevPSK = inputDevPSK;
        }
        return true;
    }

    private void initPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PermissionUtils.permission(PermissionConstants.STORAGE, PermissionConstants.MICROPHONE, PermissionConstants.CAMERA)
                    .request();
        }
    }

    /**
     * 实现下行消息处理的回调接口
     */
    private class SelfDownStreamCallBack extends TXDataTemplateDownStreamCallBack {
        @Override
        public void onReplyCallBack(String replyMsg) {
            //可根据自己需求进行处理属性上报以及事件的回复，根据需求填写
            Log.d(TAG, "reply received : " + replyMsg);
        }

        @Override
        public void onGetStatusReplyCallBack(JSONObject data) {
            //可根据自己需求进行处理状态和控制信息的获取结果
            Log.d(TAG, "event down stream message received : " + data);
            //do something 如果是userlist，刷新展示用户列表
            try {
                JSONObject property = data.getJSONObject("reported");
                if (property.has(PROPERTY_SYS_CALL_USERLIST)) {
                    mDatas = new ArrayList<UserEntity>();
                    String userList = property.getString(PROPERTY_SYS_CALL_USERLIST);
                    JSONArray userArrayList = new JSONArray(userList);
                    for (int i = 0; i < userArrayList.length(); i++) {
                        JSONObject userJson = (JSONObject) userArrayList.get(i);
                        UserEntity user = new UserEntity();
                        if (userJson.has(PROPERTY_SYS_CALL_USERLIST_USERID)) {
                            user.setUserid(userJson.getString(PROPERTY_SYS_CALL_USERLIST_USERID));
                        } else {//没有获取到UserID
                            user.setUserid("");
                        }
                        if (userJson.has(PROPERTY_SYS_CALL_USERLIST_NICKNAME)) {
                            user.setUserName(userJson.getString(PROPERTY_SYS_CALL_USERLIST_NICKNAME));
                        } else {//没有获取到NickName
                            user.setUserName("");
                        }
                        mDatas.add(user);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 设置适配器，刷新展示用户列表
                            mAdapter = new UserListAdapter(TRTCMainActivity.this, mDatas);
                            mRecyclerView.setAdapter(mAdapter);
                        }
                    });

                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public JSONObject onControlCallBack(JSONObject msg) {
            Log.d(TAG, "control down stream message received : " + msg);
            //do something 如果是userlist，刷新展示用户列表
            if (msg.has(PROPERTY_SYS_CALL_USERLIST)) {
                try {
                    mDatas = new ArrayList<UserEntity>();
                    String userList = msg.getString(PROPERTY_SYS_CALL_USERLIST);
                    JSONArray userArrayList = new JSONArray(userList);
                    for (int i = 0; i < userArrayList.length(); i++) {
                        JSONObject userJson = (JSONObject) userArrayList.get(i);
                        UserEntity user = new UserEntity();
                        if (userJson.has(PROPERTY_SYS_CALL_USERLIST_USERID)) {
                            user.setUserid(userJson.getString(PROPERTY_SYS_CALL_USERLIST_USERID));
                        } else {//没有获取到UserID
                            user.setUserid("");
                        }
                        if (userJson.has(PROPERTY_SYS_CALL_USERLIST_NICKNAME)) {
                            user.setUserName(userJson.getString(PROPERTY_SYS_CALL_USERLIST_NICKNAME));
                        } else {//没有获取到NickName
                            user.setUserName("");
                        }
                        mDatas.add(user);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 设置适配器，刷新展示用户列表
                            mAdapter = new UserListAdapter(TRTCMainActivity.this, mDatas);
                            mRecyclerView.setAdapter(mAdapter);
                        }
                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
            //output
            try {
                JSONObject result = new JSONObject();
                result.put("code",0);
                result.put("status", "some message wher errorsome message when error");
                return result;
            } catch (JSONException e) {
                printLogInfo(TAG, "Construct params failed!", mLogInfoText, TXLog.LEVEL_ERROR);
                return null;
            }
        }

        @Override
        public  JSONObject onActionCallBack(String actionId, JSONObject params){
            TXLog.d(TAG, "action [%s] received, input:" + params, actionId);
            //do something based action id and input
            if(actionId.equals("blink")) {
                try {
                    Iterator<String> it = params.keys();
                    while (it.hasNext()) {
                        String key = it.next();
                        TXLog.d(TAG,"Input parameter[%s]:" + params.get(key), key);
                    }
                    //construct result
                    JSONObject result = new JSONObject();
                    result.put("code",0);
                    result.put("status", "some message wher errorsome message when error");

                    // response based on output
                    JSONObject response = new JSONObject();
                    response.put("result", 0);

                    result.put("response", response);
                    return result;
                } catch (JSONException e) {
                    printLogInfo(TAG, "Construct params failed!", mLogInfoText, TXLog.LEVEL_ERROR);
                    return null;
                }
            } else if (actionId.equals("YOUR ACTION")) {
                //do your action
            }
            return null;
        }
    }

    /**
     * 实现TXMqttActionCallBack回调接口
     */
    private class SelfMqttActionCallBack extends TXMqttActionCallBack {

        @Override
        public void onConnectCompleted(Status status, boolean reconnect, Object userContext, String msg) {
            String userContextInfo = "";
            if (userContext instanceof TXMqttRequest) {
                userContextInfo = userContext.toString();
            }
            String logInfo = String.format("onConnectCompleted, status[%s], reconnect[%b], userContext[%s], msg[%s]",
                    status.name(), reconnect, userContextInfo, msg);
            printLogInfo(TAG, logInfo, mLogInfoText, TXLog.LEVEL_INFO);
            if (mDataTemplateSample != null) {
                if (!reconnect) {
                    mDataTemplateSample.subscribeTopic();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // 设置适配器，刷新展示用户列表
                            mQRCodeImgView.setImageBitmap(ZXingUtils.createQRCodeBitmap(mDataTemplateSample.generalDeviceQRCodeContent(), 200, 200,"UTF-8","H", "1", Color.BLACK, Color.WHITE));
                        }
                    });
                }
            }
        }

        @Override
        public void onConnectionLost(Throwable cause) {
            String logInfo = String.format("onConnectionLost, cause[%s]", cause.toString());
            printLogInfo(TAG, logInfo, mLogInfoText, TXLog.LEVEL_INFO);
        }

        @Override
        public void onDisconnectCompleted(Status status, Object userContext, String msg) {
            String userContextInfo = "";
            if (userContext instanceof TXMqttRequest) {
                userContextInfo = userContext.toString();
            }
            String logInfo = String.format("onDisconnectCompleted, status[%s], userContext[%s], msg[%s]", status.name(), userContextInfo, msg);
            printLogInfo(TAG, logInfo, mLogInfoText, TXLog.LEVEL_INFO);
        }

        @Override
        public void onPublishCompleted(Status status, IMqttToken token, Object userContext, String errMsg) {
            String userContextInfo = "";
            if (userContext instanceof TXMqttRequest) {
                userContextInfo = userContext.toString();
            }
            String logInfo = String.format("onPublishCompleted, status[%s], topics[%s],  userContext[%s], errMsg[%s]",
                    status.name(), Arrays.toString(token.getTopics()), userContextInfo, errMsg);
            printLogInfo(TAG, logInfo, mLogInfoText);
        }

        @Override
        public void onSubscribeCompleted(Status status, IMqttToken asyncActionToken, Object userContext, String errMsg) {
            String userContextInfo = "";
            if (userContext instanceof TXMqttRequest) {
                userContextInfo = userContext.toString();
            }
            String logInfo = String.format("onSubscribeCompleted, status[%s], topics[%s], userContext[%s], errMsg[%s]",
                    status.name(), Arrays.toString(asyncActionToken.getTopics()), userContextInfo, errMsg);
            if (Status.ERROR == status) {
                printLogInfo(TAG, logInfo, mLogInfoText, TXLog.LEVEL_ERROR);
            } else {
                printLogInfo(TAG, logInfo, mLogInfoText);
            }
            //get status
            if(Status.OK != mDataTemplateSample.propertyGetStatus("report", false)) {
                printLogInfo(TAG, "property get status failed!", mLogInfoText, TXLog.LEVEL_ERROR);
            }
        }

        @Override
        public void onUnSubscribeCompleted(Status status, IMqttToken asyncActionToken, Object userContext, String errMsg) {
            String userContextInfo = "";
            if (userContext instanceof TXMqttRequest) {
                userContextInfo = userContext.toString();
            }
            String logInfo = String.format("onUnSubscribeCompleted, status[%s], topics[%s], userContext[%s], errMsg[%s]",
                    status.name(), Arrays.toString(asyncActionToken.getTopics()), userContextInfo, errMsg);
            printLogInfo(TAG, logInfo, mLogInfoText);
        }

        @Override
        public void onMessageReceived(final String topic, final MqttMessage message) {
            String logInfo = String.format("receive command, topic[%s], message[%s]", topic, message.toString());
            printLogInfo(TAG, logInfo, mLogInfoText);
        }
    }

    private class TRTCCallBack extends TXTRTCCallBack {

        @Override
        public void onGetCallStatusCallBack(Integer callStatus, final String userid, Integer callType) {
            if (callStatus == 1) { //表示被呼叫了
                mCallMobileNumber = 0;
                mCallType = callType;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!TRTCUIManager.getInstance().callMobile && !TRTCUIManager.getInstance().isCalling) { //被呼叫了
                            TRTCUIManager.getInstance().setSessionManager(new TRTCExplorerDemoSessionManager(mDataTemplateSample));
                            if (mCallType == TRTCCalling.TYPE_AUDIO_CALL) {
                                TRTCUIManager.getInstance().isCalling = true;
                                TRTCAudioCallActivity.startBeingCall(TRTCMainActivity.this, new RoomKey(), userid);
                            } else if (mCallType == TRTCCalling.TYPE_VIDEO_CALL) {
                                TRTCUIManager.getInstance().isCalling = true;
                                TRTCVideoCallActivity.startBeingCall(TRTCMainActivity.this, new RoomKey(), userid);
                            }
                        }
                    }
                });
            } else if (callStatus == 0) { //被拒绝了
                if (mCallMobileNumber > 0) {
                    mCallMobileNumber--;
                }
                if (mCallMobileNumber == 0) {
                    TRTCUIManager.getInstance().callMobile = false;
                    if (TRTCUIManager.getInstance().isCalling) { //当前正显示音视频通话页面，finish掉
                        TimerTask task = new TimerTask(){
                            public void run(){
                                TRTCUIManager.getInstance().refuseEnterRoom();
                            }
                        };
                        Timer timer = new Timer();
                        timer.schedule(task, 500);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "对方正忙...", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }

        @Override
        public void trtcJoinRoomCallBack(final RoomKey room) {
            mCallMobileNumber = 0;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TRTCUIManager.getInstance().joinRoom(mCallType, "", room);
                    TRTCUIManager.getInstance().callMobile = false;
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (mDataTemplateSample != null) {
            mDataTemplateSample.disconnect();
        }
        mDataTemplateSample = null;
        super.onDestroy();
    }

    /**
     * 打印日志信息
     *
     * @param logInfo
     */
    protected void printLogInfo(final String tag, final String logInfo, final TextView textView, int logLevel) {
        switch (logLevel) {
            case TXLog.LEVEL_DEBUG:
                TXLog.d(tag, logInfo);
                break;

            case TXLog.LEVEL_INFO:
                TXLog.i(tag, logInfo);
                break;

            case TXLog.LEVEL_ERROR:
                TXLog.e(tag, logInfo);
                break;

            default:
                TXLog.d(tag, logInfo);
                break;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.append(logInfo + "\n");
            }
        });
    }

    /**
     * 打印日志信息
     *
     * @param logInfo
     */
    protected void printLogInfo(final String tag, final String logInfo, final TextView textView) {
        printLogInfo(tag, logInfo, textView, TXLog.LEVEL_DEBUG);
    }
}