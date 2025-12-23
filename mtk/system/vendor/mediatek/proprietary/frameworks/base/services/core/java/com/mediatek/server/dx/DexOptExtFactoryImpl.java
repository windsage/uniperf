package com.mediatek.server.dx;

import com.mediatek.dx.DexOptExt;
import com.mediatek.dx.DexOptExtFactory;

public class DexOptExtFactoryImpl extends DexOptExtFactory {
    @Override
    public DexOptExt makeDexOpExt() {
        return DexOptExtImpl.getInstance();
    }
}