package com.cong.pcmaac;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.CHANNEL_OUT_STEREO;
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

public class MyAudioDecoder3 implements AudioDecoder{
    private static final String TAG = "AudioDecoder";
    private Worker mWorker;
    private byte[] mPcmData;
    private FrameRead frameRead;

    public MyAudioDecoder3(FrameRead frameRead) {
        this.frameRead = frameRead;
    }

    /**
     * 解码并播放开始
     */
    public void start() {
        mWorker = new Worker();
        mWorker.start();
    }

    /**
     * 停止解码播放线程。
     */
    public void stop() {
        if (mWorker != null) {
            mWorker.interrupt();
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
            isRunning = true;
            if (!prepare()) {
                isRunning = false;
                Log.d(TAG, "音频解码器初始化失败");
                if (frameRead!=null){
                    frameRead.readFrameEnd("音频解码器初始化失败");
                }
                return;
            }
//            if (!api21()) {
                inputBuffers = mDecoder.getInputBuffers();
                outputBuffers = mDecoder.getOutputBuffers();
//            }
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
            AACHelper.AdtsHeader header = frameRead.readHeaderFrame();
            if (header==null){
                Log.d(TAG, "audio decoding header .....");
                return true;
            }
            int sample_rate = header.sampleRate;
            int channel_out = header.channelconfig==2?CHANNEL_OUT_STEREO:CHANNEL_OUT_MONO;
            int buffer_fullness = 1024;
            int channel_count = header.channelconfig;
            int bit_rate = 0;
            int profile = header.profile;
            mBufferInfo = new MediaCodec.BufferInfo();
            //开始播放
            mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, sample_rate, channel_out, AUDIO_FORMAT, buffer_fullness, AudioTrack.MODE_STREAM);
            mPlayer.play();
            try {
                mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
                MediaFormat format = new MediaFormat();
                //解码配置
                format.setString(MediaFormat.KEY_MIME, MIME_TYPE);
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channel_count);
                format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sample_rate);
                format.setInteger(MediaFormat.KEY_BIT_RATE, bit_rate);
                format.setInteger(MediaFormat.KEY_IS_ADTS, 1);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);

//                int profile = KEY_AAC_PROFILE;  //AAC LC
                int freqIdx = header.sampleFrequencyIndex;  //44.1KHz
                int chanCfg = header.channelconfig;  //CPE
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
//            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            if (api21()) {
                while (!isEOF) {
                    if (isInterrupted()){
                        return;
                    }
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
                    if (isInterrupted()){
                        return;
                    }
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
                        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d("", "saw output EOS.");
                            isEOF = true;
                        }
                    }
                    if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outputBuffers = mDecoder.getOutputBuffers();
                        Log.d("", "output buffers have changed.");
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat oformat = mDecoder.getOutputFormat();
                        Log.d("", "output format has changed to " + oformat);
                    } else {
                        Log.d("", "dequeueOutputBuffer returned " + outputIndex);
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
            if (frameRead!=null)
            frameRead.readFrameEnd("播放完成");
        }

        private boolean api21(){
//            return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
            return false;
        }
    }
}
