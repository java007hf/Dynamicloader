package com.tencent.qrom.dynxloaderbridge;


public abstract class Bridge_MethodReplacement extends Bridge_MethodHook {
	public Bridge_MethodReplacement() {
		super();
	}
	public Bridge_MethodReplacement(int priority) {
		super(priority);
	}

	@Override
	protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
		try {
			Object result = replaceHookedMethod(param);
			param.setResult(result);
		} catch (Throwable t) {
			param.setThrowable(t);
		}
	}

	protected final void afterHookedMethod(MethodHookParam param) throws Throwable {}

	/**
	 * Shortcut for replacing a method completely. Whatever is returned/thrown here is taken
	 * instead of the result of the original method (which will not be called).
	 */
	protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

	public static final Bridge_MethodReplacement DO_NOTHING = new Bridge_MethodReplacement(PRIORITY_HIGHEST*2) {
		@Override
		protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
			return null;
		};
	};

	/**
	 * Creates a callback which always returns a specific value
	 */
	public static Bridge_MethodReplacement returnConstant(final Object result) {
		return returnConstant(PRIORITY_DEFAULT, result);
	}

	/**
	 * @see #returnConstant(Object)
	 */
	public static Bridge_MethodReplacement returnConstant(int priority, final Object result) {
		return new Bridge_MethodReplacement(priority) {
			@Override
			protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
				return result;
			}
		};
	}

}
