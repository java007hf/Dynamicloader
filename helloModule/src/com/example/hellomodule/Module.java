package com.example.hellomodule;

import com.tencent.qrom.dynxloaderbridge.BridgeHelpers;
import com.tencent.qrom.dynxloaderbridge.Bridge_MethodHook;
import com.tencent.qrom.dynxloaderbridge.IBridgeLoadPackage;
import com.tencent.qrom.dynxloadercallbacks.Bridge_LoadPackage.LoadPackageParam;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

/*this is a example to load kingroot */

public class Module implements IBridgeLoadPackage {
	private static final String TAG = "helloModule";
	String packageNameStr = "";
	String permissionStr = "";
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		Log.i(TAG, " packageName = " + lpparam.packageName);
		
		//if (lpparam.packageName.equals("com.kingroot.master")) {
			Class<?> textClass = BridgeHelpers.findClass("android.widget.TextView", null);
			BridgeHelpers.findAndHookMethod(textClass, 
					"onTextChanged", 
					CharSequence.class, int.class, int.class, int.class, 
					new MyBridge_MethodHook());
		//}
	}
	
	private class MyBridge_MethodHook extends Bridge_MethodHook {
		@Override
		protected void beforeHookedMethod(MethodHookParam param)
				throws Throwable {
			String strObj = param.thisObject.toString();
			CharSequence charSequence = (CharSequence)param.args[0];
			Log.d(TAG, "-^^-<onTextChanged>--ObjectInfo: " + strObj);
			Log.d(TAG, "-^^-<onTextChanged>--CharSequence : " + charSequence);
			Log.d(TAG, "-^^-<onTextChanged>--ProcessId : " +  + android.os.Process.myPid());
			
			if (strObj.contains("#7f090070")) {
				packageNameStr = charSequence + "";
			}
			if (strObj.contains("#7f090071") && !charSequence.equals("")){
				permissionStr = KingRootUtils.getPermission(charSequence+"");
				Log.d(TAG, "-^^-<onTextChanged>--Get PermissionInfo: packageName : " + packageNameStr + ", permission : " + permissionStr);
			}
		}
		
		@Override
		protected void afterHookedMethod(MethodHookParam param)
				throws Throwable {
			super.afterHookedMethod(param);
		}

		@Override
		public int compareTo(Object another) {
			return 0;
		}
	}
/*
	private class MyBridge_MethodHook extends Bridge_MethodHook {
		@Override
		protected void beforeHookedMethod(MethodHookParam param)
				throws Throwable {
			param.args[0] = 0xff0000ff;
			param.args[1] = "xposed have hooked!";
			
			super.beforeHookedMethod(param);
		}
		
		@Override
		protected void afterHookedMethod(MethodHookParam param)
				throws Throwable {
			super.afterHookedMethod(param);
		}		
	}
*/
/*	
	static String getCurProcessName(Context context) {
		int pid = android.os.Process.myPid();
		ActivityManager mActivityManager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningAppProcessInfo appProcess : mActivityManager
				.getRunningAppProcesses()) {
			if (appProcess.pid == pid) {
				return appProcess.processName;
			}
		}
		return null;
	}
*/
}
