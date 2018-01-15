package com.cong.pcmaac;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.cong.pcmaac.AudioCodec.BUFFFER_SIZE;

/**
 * 本地文件编码播放类。
 */

public class MyLocalAudioDecoder {
    private static final String TAG = "AudioDecoder";
    private Worker mWorker;
    private String path;//aac文件的路径。
    private DecoderPlayCallback decoderPlayCallback;

    public MyLocalAudioDecoder(String filename,DecoderPlayCallback decoderPlayCallback) {
        this.path = filename;
        this.decoderPlayCallback = decoderPlayCallback;
    }

    public void start() {
        mWorker = new Worker();
        mWorker.setRunning(true);
        mWorker.start();
    }

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
        private MediaExtractor extractor;

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
            // 等待客户端
            mPlayer = new AudioTrack(AudioManager.STREAM_MUSIC, AudioCodec.KEY_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT, BUFFFER_SIZE, AudioTrack.MODE_STREAM);//
            mPlayer.play();
            try {
                String codecName = null;
                for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                    MediaCodecInfo mediaCodecInfo = MediaCodecList.getCodecInfoAt(i);
                    for (String type : mediaCodecInfo.getSupportedTypes()) {
                        if (TextUtils.equals(type, MediaFormat.MIMETYPE_AUDIO_AAC)
                                && mediaCodecInfo.isEncoder()) {
                            codecName = mediaCodecInfo.getName();
                            break;
                        }
                    }
                    if (null != codecName) {
                        break;
                    }
                }

                final String encodeFile = path;
                extractor = new MediaExtractor();
                extractor.setDataSource(encodeFile);

                MediaFormat mediaFormat = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("audio/")) {
                        extractor.selectTrack(i);
                        mediaFormat = format;
                        break;
                    }
                }

                mDecoder = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));

                mDecoder.configure(mediaFormat, null, null, 0);
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
            //api > 21
            long kTimeOutUs = 5000;
            boolean sawInputEOS = false;
            boolean sawOutputEOS = false;
            int totalRawSize = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                try {
                    while (!sawOutputEOS) {
//                    LG.e("while sawOutputEOS");
                        if (!sawInputEOS) {
                            //获取可用的buffer下标
                            int inputBufIndex = mDecoder.dequeueInputBuffer(kTimeOutUs);
                            if (inputBufIndex >= 0) {
                                //获取可用的buffer
                                ByteBuffer dstBuf = mDecoder.getInputBuffer(inputBufIndex);
                                //获取buffer的采样率
                                int sampleSize = 0;
                                try {
                                    sampleSize = extractor.readSampleData(dstBuf, 0);
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                                Log.e(TAG,"while sawOutputEOS:"+sampleSize);
                                if (sampleSize <= 0) {
                                    Log.i("TAG", "saw input EOS.");
                                    sawInputEOS = true;
                                    //归还buffer
                                    mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                } else {
                                    //获取表述时间
                                    long presentationTimeUs = extractor.getSampleTime();
                                    //归还buffer
                                    mDecoder.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0);
                                    //下一个音轨
                                    extractor.advance();
                                }
                            }else {
                                Log.e(TAG,"while sawOutputEOS inputBufIndex < 0");
                            }
                        }else {
//                        LG.e("while sawOutputEOS");
                        }
                        //获取可用的输出下标
                        int res = mDecoder.dequeueOutputBuffer(info, kTimeOutUs);
                        if (res >= 0) {

                            int outputBufIndex = res;
                            // Simply ignore codec config buffers.
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                Log.i("TAG", "audio encoder: codec config buffer");
                                mDecoder.releaseOutputBuffer(outputBufIndex, false);
                                continue;
                            }
                            if (info.size != 0) {
                                ByteBuffer outBuf = mDecoder.getOutputBuffer(outputBufIndex);
                                outBuf.position(info.offset);
                                outBuf.limit(info.offset + info.size);
                                byte[] data = new byte[info.size];
                                outBuf.get(data);
                                totalRawSize += data.length;
                                // 播放音乐
                                mPlayer.write(data, 0, info.size);
                            }
                            mDecoder.releaseOutputBuffer(outputBufIndex, false);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i("TAG", "saw output EOS.");
                                sawOutputEOS = true;
                            }
                        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            Log.i("TAG", "output buffers have changed.");
                        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat oformat = mDecoder.getOutputFormat();
                            Log.i("TAG", "output format has changed to " + oformat);
                        }
                    }
                } finally {
                    if (extractor!=null)
                        extractor.release();
                }
            }else {
                ByteBuffer[] codecInputBuffers = mDecoder.getInputBuffers();
                ByteBuffer[] codecOutputBuffers = mDecoder.getOutputBuffers();
                try {
                    while (!sawOutputEOS) {
//                    LG.e("while sawOutputEOS");
                        if (!sawInputEOS) {
                            //获取可用的buffer下标
                            int inputBufIndex = mDecoder.dequeueInputBuffer(kTimeOutUs);
                            if (inputBufIndex >= 0) {
                                //获取可用的buffer
                                ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                                //获取buffer的采样率
                                int sampleSize = 0;
                                try {
                                    sampleSize = extractor.readSampleData(dstBuf, 0);
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                                Log.e(TAG,"while sawOutputEOS:"+sampleSize);
                                if (sampleSize <= 0) {
                                    Log.i("TAG", "saw input EOS.");
                                    sawInputEOS = true;
                                    //归还buffer
                                    mDecoder.queueInputBuffer(inputBufIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                } else {
                                    //获取表述时间
                                    long presentationTimeUs = extractor.getSampleTime();
                                    //归还buffer
                                    mDecoder.queueInputBuffer(inputBufIndex, 0, sampleSize, presentationTimeUs, 0);
                                    //下一个音轨
                                    extractor.advance();
                                }
                            }else {
                                Log.e(TAG,"while sawOutputEOS inputBufIndex < 0");
                            }
                        }else {
//                        LG.e("while sawOutputEOS");
                        }
                        //获取可用的输出下标
                        int res = mDecoder.dequeueOutputBuffer(info, kTimeOutUs);
                        if (res >= 0) {

                            int outputBufIndex = res;
                            // Simply ignore codec config buffers.
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                Log.i("TAG", "audio encoder: codec config buffer");
                                mDecoder.releaseOutputBuffer(outputBufIndex, false);
                                continue;
                            }
                            if (info.size != 0) {
                                ByteBuffer outBuf = codecOutputBuffers[outputBufIndex];
                                outBuf.position(info.offset);
                                outBuf.limit(info.offset + info.size);
                                byte[] data = new byte[info.size];
                                outBuf.get(data);
                                totalRawSize += data.length;
                                // 播放音乐
                                mPlayer.write(data, 0, info.size);
                            }
                            mDecoder.releaseOutputBuffer(outputBufIndex, false);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i("TAG", "saw output EOS.");
                                sawOutputEOS = true;
                            }
                        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            codecOutputBuffers = mDecoder.getOutputBuffers();
                            Log.i("TAG", "output buffers have changed.");
                        } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat oformat = mDecoder.getOutputFormat();
                            Log.i("TAG", "output format has changed to " + oformat);
                        }
                    }
                } finally {
                    if (extractor!=null) {
                        extractor.release();
                    }
                }
            }
            if (decoderPlayCallback!=null){
                decoderPlayCallback.playEnd();
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
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }
}
