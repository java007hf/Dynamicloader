package com.tencent.qrom.dynxloaderbridge;

import com.tencent.qrom.dynxloadercallbacks.Bridge_InitPackageResources;
import com.tencent.qrom.dynxloadercallbacks.Bridge_InitPackageResources.InitPackageResourcesParam;

/**
 * Use the module class as a handler for {@link Bridge_InitPackageResources#handleInitPackageResources}
 */
public interface IBridgeInitPackageResources extends IBridgeMod {
	/** @see Bridge_InitPackageResources#handleInitPackageResources */
	public abstract void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable;

	public static class Wrapper extends Bridge_InitPackageResources {
		private final IBridgeInitPackageResources instance;
		public Wrapper(IBridgeInitPackageResources instance) {
			this.instance = instance;
		}
		@Override
		public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
			instance.handleInitPackageResources(resparam);
		}
	}
}
