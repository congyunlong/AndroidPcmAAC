package com.cong.pcmaac;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Administrator on 2018/1/2.
 */

public class AACHelper {
    // 采样频率对照表
    private static Map<Integer, Integer> samplingFrequencyIndexMap = new HashMap<>();

    static {
        samplingFrequencyIndexMap.put(96000, 0);
        samplingFrequencyIndexMap.put(88200, 1);
        samplingFrequencyIndexMap.put(64000, 2);
        samplingFrequencyIndexMap.put(48000, 3);
        samplingFrequencyIndexMap.put(44100, 4);
        samplingFrequencyIndexMap.put(32000, 5);
        samplingFrequencyIndexMap.put(24000, 6);
        samplingFrequencyIndexMap.put(22050, 7);
        samplingFrequencyIndexMap.put(16000, 8);
        samplingFrequencyIndexMap.put(12000, 9);
        samplingFrequencyIndexMap.put(11025, 10);
        samplingFrequencyIndexMap.put(8000, 11);
        samplingFrequencyIndexMap.put(0x0, 96000);
        samplingFrequencyIndexMap.put(0x1, 88200);
        samplingFrequencyIndexMap.put(0x2, 64000);
        samplingFrequencyIndexMap.put(0x3, 48000);
        samplingFrequencyIndexMap.put(0x4, 44100);
        samplingFrequencyIndexMap.put(0x5, 32000);
        samplingFrequencyIndexMap.put(0x6, 24000);
        samplingFrequencyIndexMap.put(0x7, 22050);
        samplingFrequencyIndexMap.put(0x8, 16000);
        samplingFrequencyIndexMap.put(0x9, 12000);
        samplingFrequencyIndexMap.put(0xa, 11025);
        samplingFrequencyIndexMap.put(0xb, 8000);
    }

    private AdtsHeader mAdtsHeader = new AdtsHeader();
    private BitReader mHeaderBitReader = new BitReader(new byte[7]);
    private byte[] mSkipTwoBytes = new byte[2];
    private FileInputStream mFileInputStream;
    private byte[] mBytes = new byte[1024];

    /**
     * 构造函数，通过传递进来的文件路径创建输入流
     *
     * @param aacFilePath AAC文件路径
     * @throws FileNotFoundException
     */
    public AACHelper(String aacFilePath) throws FileNotFoundException {
        mFileInputStream = new FileInputStream(aacFilePath);
    }

    public AACHelper() {
    }

    /**
     * 获取下一Sample数据
     *
     * @param byteBuffer 存放Sample数据的ByteBuffer
     * @return 当前Sample的byte[]大小，如果为空返回-1
     * @throws IOException
     */
    public int getSample(ByteBuffer byteBuffer) throws IOException {
        //读取adts头
        if (readADTSHeader(mAdtsHeader, mFileInputStream)) {
            Log.e("getSample",mAdtsHeader.toString());
            //读取音频每帧的内容长度
            int length = mFileInputStream.read(mBytes, 0, mAdtsHeader.frameLength - mAdtsHeader.getSize());
            byteBuffer.clear();
            byteBuffer.put(mBytes, 0, length);
            byteBuffer.position(0);
            byteBuffer.limit(length);
            //返回每个音频帧的长度
            return length;
        }
        Log.e("getSample","getSample end");
        return -1;
    }

    /**
     * 从AAC文件流中读取ADTS头部
     *
     * @param adtsHeader      ADTS头部
     * @param fileInputStream AAC文件流
     * @return 是否读取成功
     * @throws IOException
     */
    private boolean readADTSHeader(AdtsHeader adtsHeader, FileInputStream fileInputStream) throws IOException {
        //读取7位的头到buffer数组
        if (fileInputStream.read(mHeaderBitReader.buffer) < 7) {
            return false;
        }

        mHeaderBitReader.position = 0;

        int syncWord = mHeaderBitReader.readBits(12); // A
        if (syncWord != 0xfff) {
            throw new IOException("Expected Start Word 0xfff");
        }
        /**adts_fixed_head*/
        adtsHeader.mpegVersion = mHeaderBitReader.readBits(1); // B
        adtsHeader.layer = mHeaderBitReader.readBits(2); // C
        adtsHeader.protectionAbsent = mHeaderBitReader.readBits(1); // D
        adtsHeader.profile = mHeaderBitReader.readBits(2) + 1;  // E
        adtsHeader.sampleFrequencyIndex = mHeaderBitReader.readBits(4);
        adtsHeader.sampleRate = samplingFrequencyIndexMap.get(adtsHeader.sampleFrequencyIndex); // F
        mHeaderBitReader.readBits(1); // G priavte_bit
        adtsHeader.channelconfig = mHeaderBitReader.readBits(3); // H
        adtsHeader.original = mHeaderBitReader.readBits(1); // I original copy
        adtsHeader.home = mHeaderBitReader.readBits(1); // J
        /**adts_variable_head*/
        adtsHeader.copyrightedStream = mHeaderBitReader.readBits(1); // K copyright_identification_bit
        adtsHeader.copyrightStart = mHeaderBitReader.readBits(1); // L copyright_identification_start
        adtsHeader.frameLength = mHeaderBitReader.readBits(13); // M aac_frame_length
        adtsHeader.bufferFullness = mHeaderBitReader.readBits(11); // 54 adts_buffer_fullness
        adtsHeader.numAacFramesPerAdtsFrame = mHeaderBitReader.readBits(2) + 1; // 56 number_of_raw_data_blocks_in_frame
        if (adtsHeader.numAacFramesPerAdtsFrame != 1) {
            throw new IOException("This muxer can only work with 1 AAC frame per ADTS frame");
        }
        //注：ADTS Header的长度可能为7字节或9字节，protectionAbsent=0时9字节，=1时，7字节
        if (adtsHeader.protectionAbsent == 0) {
            fileInputStream.read(mSkipTwoBytes);
        }
        return true;
    }

    public boolean readADTSHeader(AdtsHeader adtsHeader,byte bytes[]) {
        if (bytes.length<7)return false;
        mHeaderBitReader.position = 0;
        mHeaderBitReader.buffer = bytes;
        int syncWord = mHeaderBitReader.readBits(12); // A
        if (syncWord != 0xfff) {
//            throw new IOException("Expected Start Word 0xfff");
            return false;
        }
        /**adts_fixed_head*/
        adtsHeader.mpegVersion = mHeaderBitReader.readBits(1); // B
        adtsHeader.layer = mHeaderBitReader.readBits(2); // C
        adtsHeader.protectionAbsent = mHeaderBitReader.readBits(1); // D
        adtsHeader.profile = mHeaderBitReader.readBits(2) + 1;  // E
        adtsHeader.sampleFrequencyIndex = mHeaderBitReader.readBits(4);//7
        adtsHeader.sampleRate = samplingFrequencyIndexMap.get(adtsHeader.sampleFrequencyIndex); // F
        mHeaderBitReader.readBits(1); // G priavte_bit
        adtsHeader.channelconfig = mHeaderBitReader.readBits(3); // H 1
        adtsHeader.original = mHeaderBitReader.readBits(1); // I original copy
        adtsHeader.home = mHeaderBitReader.readBits(1); // J
        /**adts_variable_head*/
        adtsHeader.copyrightedStream = mHeaderBitReader.readBits(1); // K copyright_identification_bit
        adtsHeader.copyrightStart = mHeaderBitReader.readBits(1); // L copyright_identification_start
        adtsHeader.frameLength = mHeaderBitReader.readBits(13); // M aac_frame_length
        adtsHeader.bufferFullness = mHeaderBitReader.readBits(11); // 54 adts_buffer_fullness
        adtsHeader.numAacFramesPerAdtsFrame = mHeaderBitReader.readBits(2) + 1; // 56 number_of_raw_data_blocks_in_frame
        if (adtsHeader.numAacFramesPerAdtsFrame != 1) { //1
//            throw new IOException("This muxer can only work with 1 AAC frame per ADTS frame");
            return false;
        }
        //注：ADTS Header的长度可能为7字节或9字节，protectionAbsent=0时9字节，=1时，7字节
        if (adtsHeader.protectionAbsent == 0) {//1
//            fileInputStream.read(mSkipTwoBytes);
        }
        return true;
    }



    /**
     * 释放资源
     *
     * @throws IOException
     */
    public void release() throws IOException {
        mFileInputStream.close();
    }

    /**
     * ADTS头部
     */
    public static class AdtsHeader {
        int getSize() {
            return 7 + (protectionAbsent == 0 ? 2 : 0);
        }

        int sampleFrequencyIndex;

        int mpegVersion;
        int layer;
        int protectionAbsent;
        int profile;
        int sampleRate;

        int channelconfig;
        int original;
        int home;
        int copyrightedStream;
        int copyrightStart;
        int frameLength;
        int bufferFullness;
        int numAacFramesPerAdtsFrame;

        @Override
        public String toString() {
            return "AdtsHeader{" +
                    "sampleFrequencyIndex=" + sampleFrequencyIndex +
                    ", mpegVersion=" + mpegVersion +
                    ", layer=" + layer +
                    ", protectionAbsent=" + protectionAbsent +
                    ", profile=" + profile +
                    ", sampleRate=" + sampleRate +
                    ", channelconfig=" + channelconfig +
                    ", original=" + original +
                    ", home=" + home +
                    ", copyrightedStream=" + copyrightedStream +
                    ", copyrightStart=" + copyrightStart +
                    ", frameLength=" + frameLength +
                    ", bufferFullness=" + bufferFullness +
                    ", numAacFramesPerAdtsFrame=" + numAacFramesPerAdtsFrame +
                    '}';
        }
    }

//    作者：郑旭泽
//    链接：https://www.jianshu.com/p/839b11e0638b
//    來源：简书
//    著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
}
