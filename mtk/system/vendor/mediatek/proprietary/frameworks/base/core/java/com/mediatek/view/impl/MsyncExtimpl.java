package com.mediatek.view.impl;

import android.content.pm.IPackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;

import com.mediatek.view.MsyncExt;
import com.mediatek.appresolutiontuner.OpenMsyncAppList;


public class MsyncExtimpl extends MsyncExt {
    private static final String TAG = "MsyncExt";
    private String mPackageName;
    private boolean mIsContainPackageName = false;

    public MsyncExtimpl() {
       if(!OpenMsyncAppList.getInstance().isRead()){
          openNewTread();
       }
    }

    private void openNewTread(){
       new Thread() {  
            public void run() {  
                OpenMsyncAppList.getInstance().loadOpenMsyncAppList();
            }
         }.start();
     }


    @Override
    public boolean isOpenMsyncPackage(String mPackageName) {
        mIsContainPackageName =  OpenMsyncAppList.getInstance().isScaledBySurfaceView(mPackageName);
        return mIsContainPackageName;
    }

}
