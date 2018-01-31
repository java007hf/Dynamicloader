package com.tencent.qrom.dynxloaderbridge;

import com.tencent.qrom.dynxloadercallbacks.Bridge_LoadPackage;
import com.tencent.qrom.dynxloadercallbacks.Bridge_LoadPackage.LoadPackageParam;

/**
 * Use the module class as a handler for {@link Bridge_LoadPackage#handleLoadPackage}
 */
public interface IBridgeLoadPackage extends IBridgeMod {
	/** @see Bridge_LoadPackage#handleLoadPackage */
	public abstract void handleLoadPackage(LoadPackageParam lpparam) throws Throwable;

	public static class Wrapper extends Bridge_LoadPackage {
		private final IBridgeLoadPackage instance;
		public Wrapper(IBridgeLoadPackage instance) {
			this.instance = instance;
		}
		@Override
		public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
			instance.handleLoadPackage(lpparam);
		}
	}
}
