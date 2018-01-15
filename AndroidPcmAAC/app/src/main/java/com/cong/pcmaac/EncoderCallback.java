package com.cong.pcmaac;

/**
 * Created by Administrator on 2018/1/2.
 */

public interface EncoderCallback {
    public void encodePrepare();
    public void encode(byte bytes[]);
    public void encodeEnd();
}
