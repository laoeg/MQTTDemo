package com.laoge.mqttdemo.mqtt;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.internal.MemoryPersistence;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class MqttScanManager {

    private int currentConnectCount = 0; //默认链接次数为0

    public static final int CONNET_SUCCESS = 3; //链接成功

    public static final int CONNET_FAIL = 4;//链接失败

    private MqttClient client;//Mqtt链接实例

    private MqttConnectOptions options;//设置链接的参数

    private ScheduledExecutorService scheduler;

    private MqttTopic mMqttTopic; //与客户端链接的唯一标识 会议

    private MqttMessage mqttMessage; //发送推送消息的实例

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONNET_SUCCESS:
                    currentConnectCount = 0;
                    try {
                        client.subscribe(mTopic, 1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case CONNET_FAIL:
                    //链接失败
                    if (NetUtils.isConnected(mContext)) {
                        currentConnectCount++;
                        if (currentConnectCount <= 3) {
                            excConnect();
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };

    private String mHost;
    private String mTopic;
    private IMqtt mIMqtt; //实现异常通知消息接收与消息发送的接口
    private Context mContext;

    public MqttScanManager init(Context context) {
        this.mContext = context;
        return this;
    }

    public MqttScanManager setHost(String host) {
        this.mHost = host;
        return this;
    }

    public MqttScanManager setIMqtt(IMqtt IMqtt) {
        this.mIMqtt = IMqtt;
        return this;
    }

    public MqttScanManager setTopic(String topic) {
        this.mTopic = topic;
        return this;
    }

    /**
     * 异常 执行 发送消息
     */
    public boolean excSendMsg(String contentMsg) {
        boolean isComplete;
        try {
            //发送的消息设置
            mqttMessage = new MqttMessage();
            mqttMessage.setQos(2);
            mqttMessage.setRetained(true);//保留
            mqttMessage.setPayload(contentMsg.getBytes());//发送通知的消息
            //发送推送消息
            MqttDeliveryToken token = mMqttTopic.publish(mqttMessage);
            token.waitForCompletion(); //等待完成
            isComplete = token.isComplete();
            if (isComplete) { //如果完成，就清空消息
                mqttMessage.clearPayload();
            }
            return isComplete;
        } catch (Exception e) {
            isComplete = false;
            e.printStackTrace();
        }
        return isComplete;
    }

    /**
     * 建议：在Service或Activity中的onCreate方法中调用此方法
     */
    public void excConnect() {
        try {
            //host为主机名，CustomIdentify为clientid即连接MQTT的客户端ID，一般以客户端唯一标识符表示，MemoryPersistence设置clientid的保存形式，默认为以内存保存
            /**
             * 想要让各个客户端进行通信，则一定要让clientId为不一样【注意*****】
             * System.currentTimeMillis() + Math.random() + ""
             */
            client = new MqttClient(mHost, System.currentTimeMillis() + Math.random() + "", new MemoryPersistence());
            //MQTT的连接设置
            options = new MqttConnectOptions();
            //设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，这里设置为true表示每次连接到服务器都以新的身份连接
            options.setCleanSession(true);
            //设置连接的用户名
            options.setUserName("");
            //设置连接的密码
            options.setPassword("".toCharArray());
            // 设置超时时间 单位为秒
            options.setConnectionTimeout(10);
            // 设置会话心跳时间 单位为秒 服务器会每隔1.5*20秒的时间向客户端发送个消息判断客户端是否在线，但这个方法并没有重连的机制
            options.setKeepAliveInterval(20);
            //设置回调
            client.setCallback(new MqttCallback() {

                @Override
                public void connectionLost(Throwable cause) {
                    //连接丢失后，一般在这里面进行重连
                    currentConnectCount++;
                    if (NetUtils.isConnected(mContext)) {
                        if (currentConnectCount <= 3) {
                            excConnect();
                        }
                    }
                }

                @Override
                public void messageArrived(MqttTopic mqttTopic, MqttMessage mqttMessage) throws Exception {
                    //subscribe后得到的消息会执行到这里面
                    if (!TextUtils.isEmpty(mqttMessage.toString())) {
                        mIMqtt.receiverMsg(mqttMessage.toString());
                    }
                }

                @Override
                public void deliveryComplete(MqttDeliveryToken mqttDeliveryToken) {
                    //publish后会执行到这里
                    //sendExceptionMsgComplete = mqttDeliveryToken.isComplete();
                }

            });
            //与客户端链接的唯一标识
            mMqttTopic = client.getTopic(mTopic);
            //开始链接
            startExceptionReconnect(options);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startExceptionReconnect(final MqttConnectOptions options) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                if (!client.isConnected()) {
                    connectException(options);
                }
            }
        }, 0 * 1000, 10 * 1000, TimeUnit.MILLISECONDS);
    }


    private void connectException(final MqttConnectOptions options) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Message msg = Message.obtain();
                    client.connect(options);
                    msg.what = CONNET_SUCCESS;
                    mHandler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = Message.obtain();
                    msg.what = CONNET_FAIL;
                    mHandler.sendMessage(msg);
                }
            }
        }).start();
    }


    /**
     * 在Service或Activity中的onDestroy方法中调用此方法
     */
    public void onDestroy() {
        try {
            scheduler.shutdown();
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

}
