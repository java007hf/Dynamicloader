package com.tencent.qrom.dynxloadercallbacks;

import com.tencent.qrom.dynxloaderbridge.Bridge;
import com.tencent.qrom.dynxloaderbridge.Bridge.CopyOnWriteSortedSet;

import android.content.pm.ApplicationInfo;

public abstract class Bridge_LoadPackage extends BridgeCallback {
	public Bridge_LoadPackage() {
		super();
	}
	public Bridge_LoadPackage(int priority) {
		super(priority);
	}

	public static class LoadPackageParam extends BridgeCallback.Param {
		public LoadPackageParam(CopyOnWriteSortedSet<Bridge_LoadPackage> callbacks) {
			super(callbacks);
		}
		/** The name of the package being loaded */
		public String packageName;
		/** The process in which the package is executed */
		public String processName;
		/** The ClassLoader used for this package */
		public ClassLoader classLoader;
		/** More information about the application to be loaded */
		public ApplicationInfo appInfo;
		/** Set to true if this is the first (and main) application for this process */
		public boolean isFirstApplication;
	}

	@Override
	protected void call(Param param) throws Throwable {
		if (param instanceof LoadPackageParam)
			handleLoadPackage((LoadPackageParam) param);
	}

	public abstract void handleLoadPackage(LoadPackageParam lpparam) throws Throwable;

	public class Unhook implements IBridgeUnhook {
		public Bridge_LoadPackage getCallback() {
			return Bridge_LoadPackage.this;
		}

		@Override
		public void unhook() {
			Bridge.unhookLoadPackage(Bridge_LoadPackage.this);
		}
	}
}
