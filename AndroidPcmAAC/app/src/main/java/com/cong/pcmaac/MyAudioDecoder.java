package com.cong.pcmaac;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.cong.pcmaac.AudioCodec.AUDIO_FORMAT;
import static com.cong.pcmaac.AudioCodec.BUFFFER_SIZE;
import static com.cong.pcmaac.AudioCodec.CHANNEL_OUT;
import static com.cong.pcmaac.AudioCodec.CHAN_CFG;
import static com.cong.pcmaac.AudioCodec.FREQ_IDX;
import static com.cong.pcmaac.AudioCodec.KEY_AAC_PROFILE;
import static com.cong.pcmaac.AudioCodec.KEY_BIT_RATE;
import static com.cong.pcmaac.AudioCodec.KEY_CHANNEL_COUNT;
import static com.cong.pcmaac.AudioCodec.KEY_SAMPLE_RATE;
import static com.cong.pcmaac.AudioCodec.MIME_TYPE;
import static com.cong.pcmaac.AudioCodec.WAIT_TIME;

/**
 * 语音解码并播放的类。
 */

public class MyAudioDecoder implements AudioDecoder{
    private static final String TAG = "AudioDecoder";
    private Worker mWorker;
    private byte[] mPcmData;
    private FrameRead frameRead;

    public MyAudioDecoder(FrameRead frameRead) {
        this.frameRead = frameRead;
    }

    /**
     * 解码并播放开始
     */
    public void start() {
        if (mWorker == null) {
            mWorker = new Worker();
            mWorker.setRunning(true);
            mWorker.start();
        }
    }

    /**
     * 停止解码播放线程。
     */
    public void stop() {
        if (mWorker != null) {
            mWorker.setRunning(false);
            mWorker = null;
        }
    }

    private class Worker extends Thread {
        private boolean isRunning = false;
        private AudioTrack mPlayer;
        private MediaCodec mDecoder;
        MediaCodec.BufferInfo mBufferInfo;
        ByteBuffer[] inputBuffers = null;
        ByteBuffer[] outputBuffers = null;

        public void setRunning(boolean run) {
            isRunning = run;
        }

        @Override
        public void run() {
            super.run();
            if (!prepare()) {
                isRunning = false;
                Log.d(TAG, "音频解码器初始化失败");
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                inputBuffers = mDecoder.getInputBuffers();
                outputBuffers = mDecoder.getOutputBuffers();
            }
            while (isRunning) {
                decode();
            }
            release();
        }

        /**
         * 等待客户端连接，初始化解码器
         *
         * @return 初始化失败返回false，成功返回true
         */
        public boolean prepare() {
            mBufferInfo = new MediaCodec.BufferInfo();
            //开始播放
            mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, KEY_SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT, BUFFFER_SIZE, AudioTrack.MODE_STREAM);
            mPlayer.play();
            try {
                mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
                MediaFormat format = new MediaFormat();
                //解码配置
                format.setString(MediaFormat.KEY_MIME, MIME_TYPE);
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, KEY_CHANNEL_COUNT);
                format.setInteger(MediaFormat.KEY_SAMPLE_RATE, KEY_SAMPLE_RATE);
                format.setInteger(MediaFormat.KEY_BIT_RATE, KEY_BIT_RATE);
                format.setInteger(MediaFormat.KEY_IS_ADTS, 1);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, KEY_AAC_PROFILE);

                int profile = KEY_AAC_PROFILE;  //AAC LC
                int freqIdx = FREQ_IDX;  //44.1KHz
                int chanCfg = CHAN_CFG;  //CPE
                ByteBuffer csd = ByteBuffer.allocate(2);
                csd.put(0, (byte) (profile << 3 | freqIdx >> 1));
                csd.put(1, (byte)((freqIdx & 0x01) << 7 | chanCfg << 3));
                format.setByteBuffer("csd-0", csd);

                mDecoder.configure(format, null, null, 0);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            if (mDecoder == null) {
                Log.e(TAG, "create mediaDecode failed");
                return false;
            }
            mDecoder.start();
            return true;
        }

        /**
         * aac解码+播放
         */
        public void decode() {
            boolean isEOF = false;
            //api > 21
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                while (!isEOF) {
                    //获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
                    int inputIndex = mDecoder.dequeueInputBuffer(-1);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) {
                            return;
                        }
                        inputBuffer.clear();
                        //读取每帧数据
                        byte[] frame = frameRead.readFrame();
                        if (frame == null) {
                            mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOF = true;
                            isRunning = false;
                            //服务已经断开，释放服务端
                        } else {
                            inputBuffer.put(frame, 0, frame.length);
                            mDecoder.queueInputBuffer(inputIndex, 0, frame.length, 0, 0);
                        }
                    } else {
                        isEOF = true;
                    }
                    int outputIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, WAIT_TIME);
                    Log.d(TAG, "audio decoding .....");
                    ByteBuffer outputBuffer;
                    //每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
                    while (outputIndex >= 0) {
                        outputBuffer = mDecoder.getOutputBuffer(outputIndex);
                        if (mPcmData == null || mPcmData.length < mBufferInfo.size) {
                            mPcmData = new byte[mBufferInfo.size];
                        }
                        //提取数据到mPcmData
                        outputBuffer.get(mPcmData, 0, mBufferInfo.size);
                        outputBuffer.clear();//数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数据
                        //播放音乐
                        mPlayer.write(mPcmData, 0, mBufferInfo.size);
                        mDecoder.releaseOutputBuffer(outputIndex, false);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据
                        outputIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, WAIT_TIME);//再次获取数据，如果没有数据输出则outputIndex=-1 循环结束
                    }
                }
            } else {
                while (!isEOF) {
                    //获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
                    int inputIndex = mDecoder.dequeueInputBuffer(-1);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = inputBuffers[inputIndex];
                        if (inputBuffer == null) {
                            return;
                        }
                        inputBuffer.clear();
                        byte[] frame = frameRead.readFrame();
                        if (frame == null) {
                            mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOF = true;
                            isRunning = false;
                            //服务已经断开，释放服务端
                        } else {
                            inputBuffer.put(frame, 0, frame.length);
                            mDecoder.queueInputBuffer(inputIndex, 0, frame.length, 0, 0);
                        }
                    } else {
                        isEOF = true;
                    }
                    int outputIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, WAIT_TIME);
                    Log.d(TAG, "audio decoding .....");
                    ByteBuffer outputBuffer;
                    //每次解码完成的数据不一定能一次吐出 所以用while循环，保证解码器吐出所有数据
                    while (outputIndex >= 0) {
                        outputBuffer = outputBuffers[outputIndex];
                        if (mPcmData == null || mPcmData.length < mBufferInfo.size) {
                            mPcmData = new byte[mBufferInfo.size];
                        }
                        outputBuffer.get(mPcmData, 0, mBufferInfo.size);
                        outputBuffer.clear();//数据取出后一定记得清空此Buffer MediaCodec是循环使用这些Buffer的，不清空下次会得到同样的数据
                        //播放音乐
                        mPlayer.write(mPcmData, 0, mBufferInfo.size);
                        mDecoder.releaseOutputBuffer(outputIndex, false);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据
                        outputIndex = mDecoder.dequeueOutputBuffer(mBufferInfo, WAIT_TIME);//再次获取数据，如果没有数据输出则outputIndex=-1 循环结束
                    }
                }
            }
        }

        /**
         * 释放资源
         */
        private void release() {
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
            }
            if (mPlayer != null) {
                mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
            }
            inputBuffers = null;
            outputBuffers = null;
        }
    }
}
