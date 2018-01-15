package com.cong.pcmaac;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ssp.qdriver.netty.MyNettyClient;
import com.ssp.qdriver.netty.NLog;
import com.ssp.qdriver.netty.NettyService;
import com.ssp.qdriver.netty.NettyTimer;
import com.ssp.qdriver.netty.PushCallbackListener;
import com.ssp.qdriver.netty.StringHandler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity
        implements View.OnClickListener,EncoderCallback,FrameRead,DecoderPlayCallback,PushCallbackListener,ServiceConnection{
    private Button record_start_btn,play_cache_btn,play_local_btn;
    private boolean isRecord,isPlay,isPlayLocal;
    private TextView clientTv,ipTv,receive_data_txt;
    private MyAudioEncoder myAudioEncoder;
    private AudioDecoder myAudioDecoder;
    private MyLocalAudioDecoder myLocalAudioDecoder;
    private final List<byte[]> bytesList = new ArrayList<>();
    private volatile BufferedOutputStream outputStream;
    private String fileName = "abc.aac";
    private String androidHost = "192.168.43.1";
    private String iosHost = "172.20.10.1";
//    private int port = 8000;
    private int port = 40800;
    private int receiveLength;

    static{
        MyNettyClient.TIME_OUT = true;
        NettyTimer.TIME_SIZE = 20 * 1000;
        NLog.DEBUG = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        record_start_btn = (Button)findViewById(R.id.record_start_btn);
        play_cache_btn = (Button)findViewById(R.id.play_cache_btn);
        play_local_btn = (Button)findViewById(R.id.play_local_btn);
        clientTv = (TextView) findViewById(R.id.host);
        ipTv = (TextView) findViewById(R.id.host_txt);
        receive_data_txt = (TextView) findViewById(R.id.receive_data_txt);
        record_start_btn.setOnClickListener(this);
        play_cache_btn.setOnClickListener(this);
        play_local_btn.setOnClickListener(this);
        findViewById(R.id.read_aac_btn).setOnClickListener(this);
        findViewById(R.id.hello_btn).setOnClickListener(this);
        findViewById(R.id.start_netty_btn).setOnClickListener(this);
        findViewById(R.id.server_btn).setOnClickListener(this);
        findViewById(R.id.client_btn).setOnClickListener(this);
        findViewById(R.id.clean_buffer_btn).setOnClickListener(this);
        findViewById(R.id.send_frame_btn).setOnClickListener(this);
        findViewById(R.id.android_host_btn).setOnClickListener(this);
        findViewById(R.id.ios_host_btn).setOnClickListener(this);
        MyNettyClient.getInstance().registerPushCallback("aac",this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId()==R.id.record_start_btn){//录音
            if (!requestPermission()){
                return;
            }
            if (isRecord){
                record_start_btn.setText("录音开始");
                if (myAudioEncoder!= null){
                    myAudioEncoder.stop();
                }
            }else {
                record_start_btn.setText("录音停止");
                if (myAudioEncoder==null){
                    myAudioEncoder = new MyAudioEncoder(this);
                }
                myAudioEncoder.start();
                bytesList.clear();
                count = 0;
            }
            isRecord=!isRecord;
        }else if (v.getId()==R.id.clean_buffer_btn){//清除缓存
            count = 0;
            receiveLength = 0;
            bytesList.clear();
            Toast.makeText(this,"清除ok",Toast.LENGTH_LONG).show();
            receive_data_txt.setText(null);
        } else if (v.getId()==R.id.play_cache_btn){//播放缓存
            if (isPlay){
                play_cache_btn.setText("播放缓存开始");
                if (myAudioDecoder!= null){
                    myAudioDecoder.stop();
                }
                isPlay = false;
            }else {
                if (bytesList.isEmpty()){
                    Toast.makeText(this,"没有缓冲数据",Toast.LENGTH_LONG).show();
                    return;
                }
                play_cache_btn.setText("播放缓存停止");
                if (myAudioDecoder==null){
                    myAudioDecoder = new MyAudioDecoder3(this);
                }
                count = 0;
                myAudioDecoder.start();
                isPlay = true;
            }
        }else if (v.getId()==R.id.play_local_btn){//播放本地
            if (isPlayLocal){
                play_local_btn.setText("播放本地开始");
                if (myLocalAudioDecoder!= null){
                    myLocalAudioDecoder.stop();
                }
            }else {
                play_local_btn.setText("播放本地停止");
                if (myLocalAudioDecoder==null){
                    String fielPath = Environment.getExternalStorageDirectory() + File.separator + fileName;
                    myLocalAudioDecoder = new MyLocalAudioDecoder(fielPath,this);
                }
                myLocalAudioDecoder.start();
            }
            isPlayLocal=!isPlayLocal;
        }else if (v.getId()==R.id.read_aac_btn){//读取aac本地文件
            final String fielPath = Environment.getExternalStorageDirectory() + File.separator + fileName;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AACHelper aacHelper = new AACHelper(fielPath);
                        int r;
                        do {
                            r = aacHelper.getSample(ByteBuffer.allocate(AudioCodec.BUFFFER_SIZE));
                        }while (r>-1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }else if (v.getId()==R.id.start_netty_btn){//开始netty
            if (MyNettyClient.isServer){
                MyNettyClient.getInstance().setPort(port);
            }else {
                MyNettyClient.getInstance().setHost(ipTv.getText().toString());
                MyNettyClient.getInstance().setPort(port);
            }
            bindService(new Intent(this, NettyService.class),
                    this, Context.BIND_AUTO_CREATE);
        }else if (v.getId()==R.id.hello_btn){//发送hello
            MyNettyClient.getInstance().sendData("aac0005hello");
        }else if (v.getId()==R.id.server_btn){//设定为服务端
            MyNettyClient.isServer = true;
            clientTv.setText(((Button)v).getText());
        }else if (v.getId()==R.id.client_btn){//设定为客户端
            clientTv.setText(((Button)v).getText());
            MyNettyClient.isServer = false;
        }else if (v.getId()==R.id.send_frame_btn){//发送数据
            sendFrame();
        }else if (v.getId()==R.id.android_host_btn){//设定ip
            if (MyNettyClient.isServer){
                MyNettyClient.getInstance().setPort(port);
            }else {
                MyNettyClient.getInstance().setHost(androidHost);
                MyNettyClient.getInstance().setPort(port);
            }
            ipTv.setText(androidHost);
        }else if (v.getId()==R.id.ios_host_btn){
            if (MyNettyClient.isServer){
                MyNettyClient.getInstance().setPort(port);
            }else {
                MyNettyClient.getInstance().setHost(iosHost);
                MyNettyClient.getInstance().setPort(port);
            }
            ipTv.setText(iosHost);
        }
    }
    /**
     * android 6.0 以上需要动态申请权限
     */
    private boolean requestPermission(){
        String permissions[] = {Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
        };
        ArrayList<String> toApplyList = new ArrayList<String>();
        for (String perm : permissions) {
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
                toApplyList.add(perm);
            }
        }
        String tmpList[] = new String[toApplyList.size()];
        if (!toApplyList.isEmpty()) {
            ActivityCompat.requestPermissions(this, toApplyList.toArray(tmpList), 123);
            return false;
        }
        return true;
    }

    @Override
    public void encodePrepare() {
        bytesList.clear();
    }

    /**
     * 录音获取数据的回调，可写入文件或缓存集合中。
     * @param bytes
     */
    @Override
    public void encode(byte[] bytes) {
        if (bytes!=null){
            Log.e("encode", "bytes length:"+bytes.length);
        }
        //写入文件中
        if (outputStream==null){
            File aacFile;
            aacFile = new File(Environment.getExternalStorageDirectory(),
                    fileName);
            touch(aacFile);
            try {
                outputStream = new BufferedOutputStream(new FileOutputStream(aacFile));
                Log.e("AudioEncoder", "outputStream initialized");
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        try {
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //写入缓存集合
        bytesList.add(bytes);
    }

    /**
     * 录音获取数据停止。
     */
    @Override
    public void encodeEnd() {
        try {
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建文件。
     * @param f
     */
    public void touch(File f) {
        try {
            if (!f.getParentFile().exists()) {
                f.getParentFile().mkdirs();
            }
            if (!f.exists())
                f.createNewFile();
            else {
                f.delete();
                f.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private volatile int count = 0;

    @Override
    public AACHelper.AdtsHeader readHeaderFrame() {
        if (bytesList.isEmpty())return null;
        AACHelper.AdtsHeader header = new AACHelper.AdtsHeader();
        new AACHelper().readADTSHeader(header,bytesList.get(0));
        return header;
    }

    @Override
    public byte[] readFrame() {
        if (count>=bytesList.size()){
            return null;
        }
        byte[] bytes = bytesList.get(count);
        count++;
        return bytes;
    }

    public void sendFrame(){
        if (bytesList.isEmpty()){
            Toast.makeText(this,"缓存为空",Toast.LENGTH_LONG).show();
            return;
        }
        for (byte [] array:bytesList) {
            byte[] bytes = array;
            count++;
            byte target[] = new byte[7+bytes.length];
            String lenStr = String.valueOf(bytes.length);
            for (;;) {
                if (lenStr.length()<4){
                    lenStr = "0"+lenStr;
                }else {
                    break;
                }
            }
            System.arraycopy(("aac"+lenStr).getBytes(),0,target,0,7);
            System.arraycopy(bytes,0,target,7,bytes.length);
            Log.e("readFrame",target.length+":"+ Arrays.toString(bytes));
            MyNettyClient.getInstance().sendData(target);
        }
    }

    @Override
    public void readFrameEnd(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                play_cache_btn.setText("播放缓存开始");
                isPlay = false;
                Toast.makeText(MainActivity.this,msg,Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void playEnd() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                play_local_btn.setText("播放本地开始");
                isPlayLocal = false;
            }
        });
    }

    @Override
    public void onPush(Object o) {
        if (o instanceof List) {
            List<Object> l = (List<Object>) o;
            if (l.size() > 0) {
                Object appId = l.get(0);
                Log.e("onPush",appId.toString());
                if (l.size() > 1) {
                    byte bytes[]= (byte[]) l.get(1);
                    receiveLength+=bytes.length;
                    encode(bytes);
                }
            }
        }
        //延迟刷新主线程
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                receive_data_txt.setText(String.valueOf(receiveLength));
            }
        });
    }

    public static String str2HexStr(String str) {
        char[] chars = "0123456789ABCDEF".toCharArray();
        StringBuilder sb = new StringBuilder("");
        byte[] bs = str.getBytes();
        int bit;
        for (int i = 0; i < bs.length; i++) {
            bit = (bs[i] & 0x0f0) >> 4;
            sb.append(chars[bit]);
            bit = bs[i] & 0x0f;
            sb.append(chars[bit]);
            // sb.append(' ');
        }
        return sb.toString().trim();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }
}
