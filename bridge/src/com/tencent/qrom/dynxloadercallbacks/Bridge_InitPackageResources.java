package com.tencent.qrom.dynxloadercallbacks;

import com.tencent.qrom.dynxloaderbridge.Bridge;
import com.tencent.qrom.dynxloaderbridge.Bridge.CopyOnWriteSortedSet;

import android.content.res.BridgeResources;

public abstract class Bridge_InitPackageResources extends BridgeCallback {
	public Bridge_InitPackageResources() {
		super();
	}
	public Bridge_InitPackageResources(int priority) {
		super(priority);
	}

	public static class InitPackageResourcesParam extends BridgeCallback.Param {
		public InitPackageResourcesParam(CopyOnWriteSortedSet<Bridge_InitPackageResources> callbacks) {
			super(callbacks);
		}
		/** The name of the package for which resources are being loaded */
		public String packageName;
		/** Reference to the resources that can be used for calls to {@link BridgeResources#setReplacement} */
		public BridgeResources res;
	}

	@Override
	protected void call(Param param) throws Throwable {
		if (param instanceof InitPackageResourcesParam)
			handleInitPackageResources((InitPackageResourcesParam) param);
	}

	public abstract void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable;

	public class Unhook implements IBridgeUnhook {
		public Bridge_InitPackageResources getCallback() {
			return Bridge_InitPackageResources.this;
		}

		@Override
		public void unhook() {
			Bridge.unhookInitPackageResources(Bridge_InitPackageResources.this);
		}
	}
}
