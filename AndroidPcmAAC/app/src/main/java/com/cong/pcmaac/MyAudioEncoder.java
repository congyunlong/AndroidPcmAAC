package com.cong.pcmaac;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG;

/**
 * 录音编码的类。
 */

public class MyAudioEncoder implements AudioCodec {
    private volatile Worker mWorker;
    private final String TAG = "AudioEncoder";
    private byte[] mFrameByte;
    private volatile EncoderCallback encoderCallback;

    public MyAudioEncoder(EncoderCallback encoderCallback) {
        this.encoderCallback = encoderCallback;
    }

    /**
     * 开始录音编码。
     */
    public void start() {
        if (mWorker == null) {
            mWorker = new Worker();
            mWorker.setRunning(true);
            mWorker.start();
        }

    }

    /**
     * 停止录音编码线程。
     */
    public void stop() {
        if (mWorker != null) {
            mWorker.setRunning(false);
            mWorker = null;
        }
    }


    private class Worker extends Thread {
        private final int mFrameSize = BUFFFER_SIZE;
        private byte[] mBuffer;
        private volatile boolean isRunning = false;
        private MediaCodec mEncoder;
        private AudioRecord mRecord;
        MediaCodec.BufferInfo mBufferInfo;

        ByteBuffer[] inputBuffers = null;
        ByteBuffer[] outputBuffers = null;

        public Worker() {

        }

        @Override
        public void run() {
            if (!prepare()) {
                Log.d(TAG, "音频编码器初始化失败");
                isRunning = false;
            }
            if (!api21()){
                inputBuffers = mEncoder.getInputBuffers();
                outputBuffers = mEncoder.getOutputBuffers();
            }

            while (isRunning) {
                int num = mRecord.read(mBuffer, 0, mFrameSize);
                Log.d(TAG, "buffer = " + mBuffer.toString() + ", num = " + num);
                encode(mBuffer);
            }
            release();
        }

        public void setRunning(boolean run) {
            Log.d(TAG, "setRunning = " + run);
            isRunning = run;
        }

        /**
         * 释放资源
         */
        private void release() {
            inputBuffers = null;
            outputBuffers = null;
            if (mEncoder != null) {
                mEncoder.stop();
                mEncoder.release();
            }
            if (mRecord != null) {
                mRecord.stop();
                mRecord.release();
                mRecord = null;
            }
            if (encoderCallback!=null)
                encoderCallback.encodeEnd();
        }

        /**
         * 连接服务端，编码器配置
         * @return true配置成功，false配置失败
         */
        private boolean prepare() {
            String codecName = null;
            for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);
                for (String type : mediaCodecInfo.getSupportedTypes()) {
                    if (TextUtils.equals(type, MIME_TYPE)
                            && mediaCodecInfo.isEncoder()) {
                        codecName = mediaCodecInfo.getName();
                        break;
                    }
                }
                if (null != codecName) {
                    break;
                }
            }
            try {
                mBufferInfo = new MediaCodec.BufferInfo();
                mEncoder = MediaCodec.createByCodecName(codecName);
                //设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050，16000，11025
                MediaFormat mediaFormat = MediaFormat.createAudioFormat(MIME_TYPE,
                        KEY_SAMPLE_RATE, KEY_CHANNEL_COUNT);

                mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);

                //比特率 声音中的比特率是指将模拟声音信号转换成数字声音信号后，单位时间内的二进制数据量，是间接衡量音频质量的一个指标
                mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, KEY_BIT_RATE);

                /**LC-AAC,HE-AAC,HE-AACv2三种主要的编码，LC-AAC就是比较传统的AAC，相对而言，主要用于中高码率(>=80Kbps)，
                  HE-AAC(相当于AAC+SBR)主要用于中低码(<=80Kbps)，
                  而新近推出的HE-AACv2(相当于AAC+SBR+PS)主要用于低码率(<=48Kbps）,
                  事实上大部分编码器设成<=48Kbps自动启用PS技术，而>48Kbps就不加PS,就相当于普通的HE-AAC。*/
                mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                        KEY_AAC_PROFILE);
                //传入的数据大小
                mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mFrameSize);

               /* byte[] data = new byte[]{(byte) 0x12, (byte) 0x12};
                ByteBuffer csd_0 = ByteBuffer.wrap(data);
                mediaFormat.setByteBuffer("csd-0", csd_0);*/

                int profile = KEY_AAC_PROFILE;  //AAC LC
                int freqIdx = FREQ_IDX;  //44.1KHz
                int chanCfg = CHAN_CFG;  //CPE
                ByteBuffer csd = ByteBuffer.allocate(2);
                csd.put(0, (byte) (profile << 3 | freqIdx >> 1));
                csd.put(1, (byte)((freqIdx & 0x01) << 7 | chanCfg << 3));

//                byte[] data = new byte[]{CSD_0, CSD_1};
//                ByteBuffer csd_0 = ByteBuffer.wrap(data);
                mediaFormat.setByteBuffer("csd-0", csd);

                /*ByteBuffer csd = ByteBuffer.allocate(2);
                csd.put(0, (byte) (KEY_AAC_PROFILE << 3 | FREQ_IDX >> 1));
                csd.put(1, (byte)((FREQ_IDX & 0x01) << 7 | CHAN_CFG << 3));
                mediaFormat.setByteBuffer("csd-0", csd);*/

                /*byte[] data = new byte[]{(byte) (KEY_AAC_PROFILE << 3 | FREQ_IDX >> 1),(byte)((FREQ_IDX & 0x01) << 7 | CHAN_CFG << 3)};
                ByteBuffer csd_0 = ByteBuffer.wrap(data);
                mediaFormat.setByteBuffer("csd-0", csd_0);*/

                mEncoder.configure(mediaFormat, null, null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE);
                mEncoder.start();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            mBuffer = new byte[mFrameSize];
            int minBufferSize = AudioRecord.getMinBufferSize(KEY_SAMPLE_RATE, CHANNEL_IN,
                    AUDIO_FORMAT);
            mRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    KEY_SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, minBufferSize * 2);
            mRecord.startRecording();
            return true;
        }

        //pts时间基数
        long presentationTimeUs = 0;
        private void encode(byte[] data) {
            //api > 21
            if (api21()) {
                //获取一个可利用的buffer下标
                int inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
                if (inputBufferIndex >= 0) {
                    //获取一个可利用的buffer
                    ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    //填充要编码的数据
                    inputBuffer.put(data);
                    inputBuffer.limit(data.length);
                    //填充到队列中
                    mEncoder.queueInputBuffer(inputBufferIndex, 0, data.length,
                            computePresentationTime(presentationTimeUs), BUFFER_FLAG_CODEC_CONFIG);
                    presentationTimeUs+=1;
                }
                //返回编码成功后buffer的下标
                int outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
                //循环取出编码后的数据
                while (outputBufferIndex >= 0) {
                    //获取指定下标的buffer
                    ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufferIndex);

                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + mBufferInfo.size);

                    //给adts头字段空出7的字节，为数据添加头
                    int length = mBufferInfo.size + 7;
                    if (mFrameByte == null || mFrameByte.length < length) {
                        mFrameByte = new byte[length];
                    }
                    addADTStoPacket(mFrameByte, length);
                    //get数据到byte数组，向后偏移了7位
                    outputBuffer.get(mFrameByte, 7, mBufferInfo.size);

                    if (encoderCallback!=null){
                        encoderCallback.encode(mFrameByte);
                        //注意：必须释放掉
                        mFrameByte = null;
                    }
                    mEncoder.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
                }
            }else {
                int inputBufferIndex = mEncoder.dequeueInputBuffer(-1);//其中需要注意的有dequeueInputBuffer（-1），参数表示需要得到的毫秒数，-1表示一直等，0表示不需要等，传0的话程序不会等待，但是有可能会丢帧。
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(data);
                    inputBuffer.limit(data.length);
                    //计算pts
                    long pts = computePresentationTime(presentationTimeUs);
                    mEncoder
                            .queueInputBuffer(inputBufferIndex, 0, data.length, pts, 0);
                    presentationTimeUs += 1;
                }else {
                    Log.e(TAG,"inputBufferIndex is 0");
                }
                int outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);

                while (outputBufferIndex >= 0) {
                    int outBitsSize = mBufferInfo.size;
                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

                    outputBuffer.position(mBufferInfo.offset);
                    outputBuffer.limit(mBufferInfo.offset + outBitsSize);

                    int length = mBufferInfo.size + 7;
                    if (mFrameByte == null || mFrameByte.length < length) {
                        mFrameByte = new byte[length];
                    }
                    addADTStoPacket(mFrameByte, length);

                    outputBuffer.get(mFrameByte, 7, outBitsSize);
                    outputBuffer.position(mBufferInfo.offset);

                    if (encoderCallback!=null){
                        encoderCallback.encode(mFrameByte);
                        mFrameByte = null;
                    }

                    mEncoder.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mEncoder.dequeueOutputBuffer(mBufferInfo, 0);
                }
            }
        }

        private long computePresentationTime(long frameIndex) {
//            return 0;
            return frameIndex * 90000 * 1024 / AudioCodec.KEY_SAMPLE_RATE;
        }

        /**
         ADTS：采样率
         0: 96000 Hz
         1: 88200 Hz
         2: 64000 Hz
         3: 48000 Hz
         4: 44100 Hz
         5: 32000 Hz
         6: 24000 Hz
         7: 22050 Hz
         8: 16000 Hz
         9: 12000 Hz
         10: 11025 Hz
         11: 8000 Hz
         12: 7350 Hz
         13: Reserved
         14: Reserved
         15: frequency is written explictly
         * 给编码出的aac裸流添加adts头字段
         *
         * @param packet 要空出前7个字节，否则会搞乱数据
         * @param packetLen
         */
        private void addADTStoPacket(byte[] packet, int packetLen) {
            int profile = KEY_AAC_PROFILE;  //AAC LC
            int freqIdx = FREQ_IDX;  //44.1KHz
            int chanCfg = CHAN_CFG;  //CPE
            packet[0] = (byte) 0xFF;
            packet[1] = (byte) 0xF9;
            packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
            packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
            packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
            packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
            packet[6] = (byte) 0xFC;
        }
    }

    private boolean api21(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
}
