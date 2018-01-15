package com.cong.pcmaac;

/**
 * Created by Administrator on 2018/1/2.
 */

public class BitReader {
    public int position;
    public byte[] buffer;

    public BitReader(byte[] buffer) {
        this.buffer = buffer;
    }

    /**
     * @param i 读取的位数
     * @return
     */
    public int readBits(int i) {//12 0/1 1
        byte b = buffer[position / 8];
        //把byte -128 变成了 +128
        int v = b < 0 ? b + 256 : b;
        int left = 8 - position % 8;
        int rc;
        if (i <= left) {
            rc = (v << (position % 8) & 0xFF) >> ((position % 8) + (left - i));
            position += i;
        } else {
            int now = left;//1
            int then = i - left;//12-1 = 11
            rc = readBits(now);
            rc = rc << then;
            rc += readBits(then);
        }
        return rc;
    }
}
