package com.tencent.qrom.dynxloaderbridge;

/**
 * Hook the initialization of Zygote (the central part of the "Android OS")
 */
public interface IBridgeZygoteInit extends IBridgeMod {
	/**
	 * Called very early during startup of Zygote
	 * @throws Throwable everything is caught, but will prevent further initialization of the module
	 */
	public void initZygote(StartupParam startupParam) throws Throwable;

	public static class StartupParam {
		public String modulePath;
	}
}
