package com.cong.pcmaac;

import android.media.AudioFormat;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

/**
 * Created by Administrator on 2018/1/2.
 */

public interface AudioCodec {
    String MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;

    /**可以正常播放的，与ios通信正常*/
//    int KEY_CHANNEL_COUNT = 2;
//    int KEY_BIT_RATE = 64*1024;
//    int KEY_SAMPLE_RATE = 44100;
//    int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO;
//    int CHANNEL_IN = AudioFormat.CHANNEL_IN_STEREO;
//    int KEY_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
//    int WAIT_TIME = 10000;
//    int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
//    int BUFFFER_SIZE = 2048;
//    int FREQ_IDX = 4;
//    int CHAN_CFG = 2;
    byte CSD_0 = 0x12;
    byte CSD_1 = (byte) 0x12;
    /**可以正常播放的，与ios通信正常*/
    /*int KEY_CHANNEL_COUNT = 1;
    int KEY_BIT_RATE = 64 * 1024;
    int KEY_SAMPLE_RATE = 22050;
    int CHANNEL_OUT = KEY_CHANNEL_COUNT==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO;
    int CHANNEL_IN = KEY_CHANNEL_COUNT==1?AudioFormat.CHANNEL_IN_MONO:AudioFormat.CHANNEL_IN_STEREO;
    int KEY_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
    int WAIT_TIME = 10000;
    int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    int BUFFFER_SIZE = 1024;
    int FREQ_IDX = 7;
    int CHAN_CFG = KEY_CHANNEL_COUNT;*/

//    int KEY_CHANNEL_COUNT = 1;
//    int KEY_BIT_RATE = 64 * 1024;
//    int KEY_SAMPLE_RATE = 44100;
//    int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
//    int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
//    int KEY_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectHE;
//    int WAIT_TIME = 10000;
//    int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
//    int BUFFFER_SIZE = 2048;
//    int FREQ_IDX = 4;
//    int CHAN_CFG = 1;

    int KEY_CHANNEL_COUNT = 1;
    int KEY_SAMPLE_RATE = 8000;
    int KEY_BIT_RATE = 16000;
    int CHANNEL_OUT = KEY_CHANNEL_COUNT==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO;
    int CHANNEL_IN = KEY_CHANNEL_COUNT==1?AudioFormat.CHANNEL_IN_MONO:AudioFormat.CHANNEL_IN_STEREO;
    int KEY_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
    int WAIT_TIME = 10000;
    int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    int BUFFFER_SIZE = 2048;
    int FREQ_IDX = 11;
    int CHAN_CFG = KEY_CHANNEL_COUNT;
    //    byte CSD_0 = 0x15;
//    byte CSD_1 = (byte) 0x88;
}
