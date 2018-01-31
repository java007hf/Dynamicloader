package com.tencent.qrom.module.xandroid;

import java.lang.reflect.Method;

import com.tencent.qrom.dynxloaderbridge.BridgeHelpers;
import com.tencent.qrom.dynxloaderbridge.Bridge_MethodHook;
import com.tencent.qrom.dynxloaderbridge.IBridgeLoadPackage;
import com.tencent.qrom.dynxloadercallbacks.Bridge_LoadPackage.LoadPackageParam;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

public class XandroidModule implements IBridgeLoadPackage {
	private static final String TAG = "AndroidModule";
	private Application mApplication;

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		// TODO Auto-generated method stub
		Log.d(TAG,"packageName = " + lpparam.packageName);
		//if (!lpparam.packageName.equals("com.tencent.helloworld")) return;
		final Class<?> activityThreadClass =
	            Class.forName("android.app.ActivityThread");
	    final Method method = activityThreadClass.getMethod("currentApplication");
	    mApplication = (Application) method.invoke(null, (Object[]) null);
	    Log.d(TAG, "mApplication = " + mApplication.getPackageName());
	    
		Class<?> classMainActivity = BridgeHelpers.findClass("com.android.server.am.ActivityManagerService", lpparam.classLoader);
		BridgeHelpers.findAndHookMethod(classMainActivity, 
					"startProcessLocked", //function
					String.class, ApplicationInfo.class, boolean.class, int.class, 
					String.class, ComponentName.class, boolean.class, boolean.class, boolean.class, //parameter
					new MyBridge_MethodHook());	
	}
	
	private class MyBridge_MethodHook extends Bridge_MethodHook {
		@Override
		protected void beforeHookedMethod(MethodHookParam param)
				throws Throwable {
	          String processName = (String)param.args[0];
	          ApplicationInfo localApplicationInfo = (ApplicationInfo)param.args[1];
	          String packageName = localApplicationInfo.packageName;
	          Log.d(TAG,"===startProcessLocked : packageName = " + packageName + ", processName = " + processName);
	          
	          Intent localIntent = new Intent("com.qrom.dynxloader.startprocess");
	          localIntent.setFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
	          localIntent.putExtra("pkgName", packageName);
	          localIntent.putExtra("processName", processName);
	          mApplication.sendBroadcast(localIntent);
		}
		
		@Override
		protected void afterHookedMethod(MethodHookParam param)
				throws Throwable {
			super.afterHookedMethod(param);
		}

		@Override
		public int compareTo(Object another) {
			// TODO Auto-generated method stub
			return 0;
		}
	}
}
