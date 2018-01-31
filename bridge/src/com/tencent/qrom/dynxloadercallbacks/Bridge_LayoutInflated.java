package com.tencent.qrom.dynxloadercallbacks;

import com.tencent.qrom.dynxloaderbridge.Bridge.CopyOnWriteSortedSet;

import android.content.res.BridgeResources;
import android.content.res.BridgeResources.ResourceNames;
import android.view.View;

public abstract class Bridge_LayoutInflated extends BridgeCallback {
	public Bridge_LayoutInflated() {
		super();
	}
	public Bridge_LayoutInflated(int priority) {
		super(priority);
	}

	public static class LayoutInflatedParam extends BridgeCallback.Param {
		public LayoutInflatedParam(CopyOnWriteSortedSet<Bridge_LayoutInflated> callbacks) {
			super(callbacks);
		}
		/** The view that has been created from the layout */
		public View view;
		/** Container with the id and name of the underlying resource */
		public ResourceNames resNames;
		/** Directory from which the layout was actually loaded (e.g. "layout-sw600dp") */
		public String variant;
		/** Resources containing the layout */
		public BridgeResources res;
	}

	@Override
	protected void call(Param param) throws Throwable {
		if (param instanceof LayoutInflatedParam)
			handleLayoutInflated((LayoutInflatedParam) param);
	}

	public abstract void handleLayoutInflated(LayoutInflatedParam liparam) throws Throwable;

	public class Unhook implements IBridgeUnhook {
		private final String resDir;
		private final int id;

		public Unhook(String resDir, int id) {
			this.resDir = resDir;
			this.id = id;
		}

		public String getResDir() {
			return resDir;
		}

		public int getId() {
			return id;
		}

		public Bridge_LayoutInflated getCallback() {
			return Bridge_LayoutInflated.this;
		}

		@Override
		public void unhook() {
			BridgeResources.unhookLayout(resDir, id, Bridge_LayoutInflated.this);
		}

	}
}
