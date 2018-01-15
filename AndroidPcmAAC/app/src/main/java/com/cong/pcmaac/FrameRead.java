package com.cong.pcmaac;

/**
 * Created by Administrator on 2018/1/3.
 */

public interface FrameRead {
    public byte[] readFrame();
    public AACHelper.AdtsHeader readHeaderFrame();
    public void readFrameEnd(String msg);
}
