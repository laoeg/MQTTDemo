package com.laoge.mqttdemo.mqtt;


public interface IMqtt {


        /**
         * 接收的推送消息
         * @param msg
         */
        void receiverMsg(String msg);

}
