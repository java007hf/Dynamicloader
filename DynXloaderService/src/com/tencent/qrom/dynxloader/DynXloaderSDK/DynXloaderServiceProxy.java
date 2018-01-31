
package com.tencent.qrom.dynxloader.DynXloaderSDK;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class DynXloaderServiceProxy {

	static final String TAG = "DynXloaderServiceProxy";
	static final String SERVICE_ACTION = "com.tencent.qrom.dynxloader.ProxyStart";
	Context mContext = null;
	IDynXloaderService mDynXloaderService = null;
	
	public DynXloaderServiceProxy(int appId, Context context) {
	
		mContext = context;
		//mRegActInfos = regActInfos;
		connect();
	}

	public void release() {
		if(mContext != null && mConn != null) {
			Log.d(TAG, "release Service");
			mContext.unbindService(mConn);
			Intent i = new Intent(SERVICE_ACTION);
			mContext.stopService(i);
		}
	}
	
	void connect() {
		//myLog("enter connect.");
		Intent i = new Intent(SERVICE_ACTION);

		mContext.startService(i);
		//bindServcie
		mContext.bindService(i, mConn, Context.BIND_AUTO_CREATE);
	}

    ServiceConnection mConn = new ServiceConnection() {
    	
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d(TAG, "enter onServiceConnected.");
			DynXloaderServiceProxy.this.mDynXloaderService = IDynXloaderService.Stub.asInterface(service);
		}

		public void onServiceDisconnected(ComponentName name){
			Log.d(TAG, "enter onServiceDisconnected.");
			DynXloaderServiceProxy.this.mDynXloaderService = null;
		}
    };
    
	//
	public int addModule(String pkgName, String processName, String modulePath){
		int ret = -1;
		try {
        	if(mDynXloaderService == null) {
        		//Retry connect service;
        		new RetryThread(this, pkgName, processName, modulePath, 1).start();
        		ret = 2;
        	} else {
        		ret = mDynXloaderService.addModule(pkgName, processName, modulePath);
        	}
    	} catch(RemoteException e) {
    		//e.printStackTrace();
    	}
		
		return ret;
	}
	
	//
	public int removeModule(String pkgName, String processName) {
		int ret = -1; 
		try {
			if(mDynXloaderService == null) {
        		//Retry connect service;
        		new RetryThread(this, pkgName, processName, null, -1).start();
        		ret = 2;
        	} else {
        		ret = mDynXloaderService.removeModule(pkgName, processName);
        	}
    	} catch(RemoteException e) {
    		e.printStackTrace();
    	}
		
		return ret;
	}
	
	public int checkModuleExist(String pkgName, String processName, String modulePath) {
		int ret =-1;
		
		try {
			
			if(mDynXloaderService == null) {
        		//Retry connect service;
        		new RetryThread(this, pkgName, processName, modulePath, 0).start();
        		ret = 2;
        	} else {
        		ret = mDynXloaderService.removeModule(pkgName, processName);
        	}
    	} catch(RemoteException e) {
    		e.printStackTrace();
    	}
		
		return ret;
		
	}
	//extraceToLocal(getApplicationContext(), XBRIDGE_DEX_NAME, cacheDir.getAbsolutePath() + File.separator + XBRIDGE_DEX_NAME)
	public boolean extractAssetsToLocal(Context context, String srcName, String outPath) {
		boolean ret = false;
        InputStream is = null;  
        OutputStream os = null;
		try {
			is = context.getAssets().open(srcName);
			os = new FileOutputStream(outPath);
	        byte[] buffer = new byte[1024];  
	        int len;
	        while((len = is.read(buffer)) != -1)
	        {
	            os.write(buffer, 0, len); 
	        }
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
	        if(os != null) {
	        	try {
	        		os.flush();
	        		os.close();
	        		File f = new File(outPath);
	    			if(f.exists())
	    				f.setReadable(true, false);
	        		ret = true;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        }
	        if(is != null) {
	        	try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}  
	        }
		}
		return ret;
    }
	
	static final int MAX_RETRY_COUNT = 10;
	class RetryThread extends Thread {
		DynXloaderServiceProxy mParent = null;
		String mPkgName, mProcessName, mModulePath;
    	int mOperid = 0; //1-----add, -1 ----- remove, 0 ------ check
    	RetryThread(DynXloaderServiceProxy proxy, String pkgName, String processName, String modulePath, int operid) {
    		mParent = proxy;
    		mPkgName = pkgName;
    		mProcessName = processName;
    		mModulePath = modulePath;
    		mOperid = operid;
    	}
    	
    	public void run() {
    		try {
        		for(int i = 0; i < DynXloaderServiceProxy.MAX_RETRY_COUNT; i ++) {
        			mParent.connect();
        			Thread.sleep(500);
        			if(mParent.mDynXloaderService != null) {
        				try {
        					if (mOperid == 1) {
        						mParent.mDynXloaderService.addModule(mPkgName, mProcessName, mModulePath); 
            				} else if (mOperid == -1) {
            					mParent.mDynXloaderService.removeModule(mPkgName, mProcessName); 
            				} else if (mOperid == 0) {
            					mParent.mDynXloaderService.checkModuleExist(mPkgName, mProcessName, mModulePath); 
            				}
            				return;
        				} catch(RemoteException e) {
        					e.printStackTrace();
        				}
        			}
        		}
        		//Log.e(CloudBackupServiceProxy.TAG, "error max retry count when registering CloudBackupService");
    		}
    		catch(InterruptedException e) {
    			e.printStackTrace();
    		}
    		catch(Exception e2) {
    			e2.printStackTrace();
    		}
    	
    	}
    }
}
