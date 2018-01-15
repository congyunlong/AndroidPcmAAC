package com.cong.pcmaac;

import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.R.attr.duration;
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

public class MyAudioDecoder2 {
    private static final String TAG = "AudioDecoder";
    private Worker mWorker;
    private byte[] mPcmData;
    private FrameRead frameRead;
    private MediaDataSource mediaDataSource;
    private String path;

    public MyAudioDecoder2(FrameRead frameRead) {
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

        MediaExtractor extractor;
        String mime = null;
        int sampleRate = 0, channels = 0, bitrate = 0;
        long presentationTimeUs = 0, duration = 0;
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
            extractor = new MediaExtractor();
            if (path==null){
                throw new RuntimeException("path is null");
            }
            try {
                extractor.setDataSource(path);
                MediaFormat format = null;
                try {
                    format = extractor.getTrackFormat(0);
                    mime = format.getString(MediaFormat.KEY_MIME);
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    // if duration is 0, we are probably playing a live stream
                    duration = format.getLong(MediaFormat.KEY_DURATION);
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                } catch (Exception e) {
                    Log.e("", "Reading format parameters exception:"+e.getLocalizedMessage());
                    // don't exit, tolerate this error, we'll fail later if this is critical
                }
                Log.d("", "Track info: mime:" + mime + " sampleRate:" + sampleRate + " channels:" + channels + " bitrate:" + bitrate + " duration:" + duration);

                // check we have audio content we know
                if (format == null || !mime.startsWith("audio/")) {
                    return false;
                }
                mDecoder = MediaCodec.createDecoderByType(mime);
                /*int profile = KEY_AAC_PROFILE;  //AAC LC
                int freqIdx = FREQ_IDX;  //44.1KHz
                int chanCfg = CHAN_CFG;  //CPE
                ByteBuffer csd = ByteBuffer.allocate(2);
                csd.put(0, (byte) (profile << 3 | freqIdx >> 1));
                csd.put(1, (byte)((freqIdx & 0x01) << 7 | chanCfg << 3));
                format.setByteBuffer("csd-0", csd);*/

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

        private boolean stop = false;
        /**
         * aac解码+播放
         */
        public void decode() {
            // start decoding
            final long kTimeOutUs = 1000;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            int noOutputCounter = 0;
            int noOutputCounterLimit = 10;

            while (!sawOutputEOS && noOutputCounter < noOutputCounterLimit && !stop) {

                noOutputCounter++;
                // read a buffer before feeding it to the decoder
                if (!sawInputEOS) {
                    int inputBufIndex = mDecoder.dequeueInputBuffer(kTimeOutUs);
                    if (inputBufIndex >= 0) {
                        ByteBuffer dstBuf = inputBuffers[inputBufIndex];
                        int sampleSize = extractor.readSampleData(dstBuf, 0);
                        if (sampleSize < 0) {
                            Log.d("", "saw input EOS. Stopping playback");
                            sawInputEOS = true;
                            sampleSize = 0;
                        } else {
                            presentationTimeUs = extractor.getSampleTime();
                            final int percent =  (duration == 0)? 0 : (int) (100 * presentationTimeUs / duration);
                        }

                        mDecoder.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                        if (!sawInputEOS) extractor.advance();

                    } else {
                        Log.e("", "inputBufIndex " +inputBufIndex);
                    }
                } // !sawInputEOS

                // decode to PCM and push it to the AudioTrack player
                int res = mDecoder.dequeueOutputBuffer(info, kTimeOutUs);

                if (res >= 0) {
                    if (info.size > 0)  noOutputCounter = 0;

                    int outputBufIndex = res;
                    ByteBuffer buf = outputBuffers[outputBufIndex];

                    final byte[] chunk = new byte[info.size];
                    buf.get(chunk);
                    buf.clear();
                    if(chunk.length > 0){
                        mPlayer.write(chunk,0,chunk.length);
                	/*if(this.state.get() != PlayerStates.PLAYING) {
                		if (events != null) handler.post(new Runnable() { @Override public void run() { events.onPlay();  } });
            			state.set(PlayerStates.PLAYING);
                	}*/

                    }
                    mDecoder.releaseOutputBuffer(outputBufIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d("", "saw output EOS.");
                        sawOutputEOS = true;
                    }
                } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = mDecoder.getOutputBuffers();
                    Log.d("", "output buffers have changed.");
                } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat oformat = mDecoder.getOutputFormat();
                    Log.d("", "output format has changed to " + oformat);
                } else {
                    Log.d("", "dequeueOutputBuffer returned " + res);
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
