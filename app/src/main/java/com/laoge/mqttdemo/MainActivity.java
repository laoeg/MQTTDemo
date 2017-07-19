package com.laoge.mqttdemo;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.laoge.mqttdemo.mqtt.MqttManager;

public class MainActivity extends AppCompatActivity {


    private String host = "tcp://192.168.208.2:4176";
    private String userName = "admin";
    private String passWord = "password";
    private String myTopic = "test/topic";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MqttManager mMqttManager = new MqttManager()
                .init(this)
                .setUserName(userName)
                .setmPassword(passWord)
                .setHost(host)
                .setTopic(myTopic);
        mMqttManager.excConnect();
        boolean isSuccess = mMqttManager.excSendMsg("message");
    }
}
