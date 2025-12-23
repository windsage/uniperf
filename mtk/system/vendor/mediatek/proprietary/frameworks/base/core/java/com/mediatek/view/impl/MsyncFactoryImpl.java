package com.mediatek.view.impl;

import com.mediatek.view.MsyncExt;
import com.mediatek.view.MsyncFactory;

public class MsyncFactoryImpl extends MsyncFactory {
    private static MsyncExt mMsyncExt = null;

    @Override
    public MsyncExt getMsyncExt() {
        if (mMsyncExt == null) {
            mMsyncExt = new MsyncExtimpl();
        }
        return mMsyncExt;
    }
}
