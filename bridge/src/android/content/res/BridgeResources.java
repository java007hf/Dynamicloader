package android.content.res;

import static com.tencent.qrom.dynxloaderbridge.BridgeHelpers.findAndHookMethod;
import static com.tencent.qrom.dynxloaderbridge.BridgeHelpers.getIntField;
import static com.tencent.qrom.dynxloaderbridge.BridgeHelpers.getObjectField;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.WeakHashMap;

import org.xmlpull.v1.XmlPullParser;

import com.tencent.qrom.dynxloaderbridge.Bridge;
import com.tencent.qrom.dynxloaderbridge.Bridge_MethodHook;
import com.tencent.qrom.dynxloaderbridge.Bridge.CopyOnWriteSortedSet;
import com.tencent.qrom.dynxloaderbridge.Bridge_MethodHook.MethodHookParam;
import com.tencent.qrom.dynxloadercallbacks.BridgeCallback;
import com.tencent.qrom.dynxloadercallbacks.Bridge_LayoutInflated;
import com.tencent.qrom.dynxloadercallbacks.Bridge_LayoutInflated.LayoutInflatedParam;

import android.content.pm.PackageParser;
import android.graphics.Movie;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Resources class that allows replacements for selected resources
 */
public class BridgeResources extends MiuiResources {
	private static final SparseArray<HashMap<String, Object>> sReplacements = new SparseArray<HashMap<String, Object>>();
	private static final SparseArray<HashMap<String, ResourceNames>> sResourceNames
		= new SparseArray<HashMap<String, ResourceNames>>();

	private static final byte[] sSystemReplacementsCache = new byte[256]; // bitmask: 0x000700ff => 2048 bit => 256 bytes
	private byte[] mReplacementsCache; // bitmask: 0x0007007f => 1024 bit => 128 bytes
	private static final HashMap<String, byte[]> sReplacementsCacheMap = new HashMap<String, byte[]>();
	private static final SparseArray<ColorStateList> sColorStateListCache = new SparseArray<ColorStateList>(0);

	private static final SparseArray<HashMap<String, CopyOnWriteSortedSet<Bridge_LayoutInflated>>> sLayoutCallbacks
		= new SparseArray<HashMap<String, CopyOnWriteSortedSet<Bridge_LayoutInflated>>>();
	private static final WeakHashMap<XmlResourceParser, XMLInstanceDetails> sXmlInstanceDetails
		= new WeakHashMap<XmlResourceParser, XMLInstanceDetails>();

	private static final String EXTRA_XML_INSTANCE_DETAILS = "xmlInstanceDetails";
	private static final ThreadLocal<LinkedList<MethodHookParam>> sIncludedLayouts = new ThreadLocal<LinkedList<MethodHookParam>>() {
		@Override
		protected LinkedList<MethodHookParam> initialValue() {
			return new LinkedList<MethodHookParam>();
		}
	};

	private static final HashMap<String, Long> sResDirLastModified = new HashMap<String, Long>();
	private static final HashMap<String, String> sResDirPackageNames = new HashMap<String, String>();
	private static ThreadLocal<Object> sLatestResKey = null;

	private boolean mIsObjectInited;
	private String mResDir;
	private String mPackageName;

	// Dummy, will never be called (objects are transferred to this class only).
	private BridgeResources() {
		super(null, null, null);
		throw new UnsupportedOperationException();
	}

	/** Framework only, don't call this from your module! */
	public void initObject(String resDir) {
		if (mIsObjectInited)
			throw new IllegalStateException("Object has already been initialized");

		this.mResDir = resDir;
		this.mPackageName = getPackageName(resDir);

		if (resDir != null) {
			synchronized (sReplacementsCacheMap) {
				mReplacementsCache = sReplacementsCacheMap.get(resDir);
				if (mReplacementsCache == null) {
					mReplacementsCache = new byte[128];
					sReplacementsCacheMap.put(resDir, mReplacementsCache);
				}
			}
		}

		this.mIsObjectInited = true;
	}

	/** Framework only, don't call this from your module! */
	public boolean isFirstLoad() {
		synchronized (sReplacements) {
			if (mResDir == null)
				return false;

			Long lastModification = new File(mResDir).lastModified();
			Long oldModified = sResDirLastModified.get(mResDir);
			if (lastModification.equals(oldModified))
				return false;

			sResDirLastModified.put(mResDir, lastModification);

			if (oldModified == null)
				return true;

			// file was changed meanwhile => remove old replacements
			for (int i = 0; i < sReplacements.size(); i++) {
				sReplacements.valueAt(i).remove(mResDir);
			}
			Arrays.fill(mReplacementsCache, (byte) 0);
			return true;
		}
	}

	public String getResDir() {
		return mResDir;
	}

	/** Framework only, don't call this from your module! */
	public static void setPackageNameForResDir(String packageName, String resDir) {
		synchronized (sResDirPackageNames) {
			sResDirPackageNames.put(resDir, packageName);
		}
	}

	/**
	 * Returns the name of the package that these resources belong to, or "android" for system resources.
	 */
	public String getPackageName() {
		return mPackageName;
	}

	private static String getPackageName(String resDir) {
		if (resDir == null)
			return "android";

		String packageName;
		synchronized (sResDirPackageNames) {
			packageName = sResDirPackageNames.get(resDir);
		}

		if (packageName != null)
			return packageName;

		PackageParser.PackageLite pkgInfo = PackageParser.parsePackageLite(resDir, 0);
		if (pkgInfo != null && pkgInfo.packageName != null) {
			Log.w("Bridge", "Package name for " + resDir + " had to be retrieved via parser");
			packageName = pkgInfo.packageName;
			setPackageNameForResDir(packageName, resDir);
			return packageName;
		}

		throw new IllegalStateException("Could not determine package name for " + resDir);
	}

	/**
	 * For a short moment during/after the creation of a new {@code Resources} object, it isn't an
	 * instance of {@code XResources} yet. For any hooks that need information about the just created
	 * object during this particular stage, this method will return the package name.
	 *
	 * <p>Note: If you call this outside of {@code getTopLevelResources()}, it throws an
	 * {@code IllegalStateException}.
	 */
	public static String getPackageNameDuringConstruction() {
		Object key;
		if (sLatestResKey == null || (key = sLatestResKey.get()) == null)
			throw new IllegalStateException("This method can only be called during getTopLevelResources()");

		String resDir = (String) getObjectField(key, "mResDir");
		return getPackageName(resDir);
	}

	/** Framework only, don't call this from your module! */
	public static void init(ThreadLocal<Object> latestResKey) throws Exception {
		sLatestResKey = latestResKey;

		findAndHookMethod(LayoutInflater.class, "inflate", XmlPullParser.class, ViewGroup.class, boolean.class, new Bridge_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (param.hasThrowable())
					return;

				XMLInstanceDetails details;
				synchronized (sXmlInstanceDetails) {
					details = sXmlInstanceDetails.get(param.args[0]);
				}
				if (details != null) {
					LayoutInflatedParam liparam = new LayoutInflatedParam(details.callbacks);
					liparam.view = (View) param.getResult();
					liparam.resNames = details.resNames;
					liparam.variant = details.variant;
					liparam.res = details.res;
					BridgeCallback.callAll(liparam);
				}
			}
		});

		findAndHookMethod(LayoutInflater.class, "parseInclude", XmlPullParser.class, View.class, AttributeSet.class, new Bridge_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				sIncludedLayouts.get().push(param);
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				sIncludedLayouts.get().pop();

				if (param.hasThrowable())
					return;

				// filled in by our implementation of getLayout()
				XMLInstanceDetails details = (XMLInstanceDetails) param.getObjectExtra(EXTRA_XML_INSTANCE_DETAILS);
				if (details != null) {
					LayoutInflatedParam liparam = new LayoutInflatedParam(details.callbacks);
					ViewGroup group = (ViewGroup) param.args[1];
					liparam.view = group.getChildAt(group.getChildCount() - 1);
					liparam.resNames = details.resNames;
					liparam.variant = details.variant;
					liparam.res = details.res;
					BridgeCallback.callAll(liparam);
				}
			}
		});
	}

	public static class ResourceNames {
		public final int id;
		public final String pkg;
		public final String name;
		public final String type;
		public final String fullName;

		private ResourceNames(int id, String pkg, String name, String type) {
			this.id = id;
			this.pkg = pkg;
			this.name = name;
			this.type = type;
			this.fullName = pkg + ":" + type + "/" + name;
		}

		/**
		 * Returns <code>true</code> if all non-null parameters match the values of this object.
		 */
		public boolean equals(String pkg, String name, String type, int id) {
			return (pkg  == null || pkg.equals(this.pkg))
				&& (name == null || name.equals(this.name))
				&& (type == null || type.equals(this.type))
				&& (id == 0 || id == this.id);
		}
	}

	private ResourceNames getResourceNames(int id) {
		return new ResourceNames(
				id,
				getResourcePackageName(id),
				getResourceTypeName(id),
				getResourceEntryName(id));
	}

	private static ResourceNames getSystemResourceNames(int id) {
		Resources sysRes = getSystem();
		return new ResourceNames(
				id,
				sysRes.getResourcePackageName(id),
				sysRes.getResourceTypeName(id),
				sysRes.getResourceEntryName(id));
	}

	private static void putResourceNames(String resDir, ResourceNames resNames) {
		int id = resNames.id;
		synchronized (sResourceNames) {
			HashMap<String, ResourceNames> inner = sResourceNames.get(id);
			if (inner == null) {
				inner = new HashMap<String, ResourceNames>();
				sResourceNames.put(id, inner);
			}
			synchronized (inner) {
				inner.put(resDir, resNames);
			}
		}
	}

	// =======================================================
	//   DEFINING REPLACEMENTS
	// =======================================================

	public void setReplacement(int id, Object replacement) {
		setReplacement(id, replacement, this);
	}

	public void setReplacement(String fullName, Object replacement) {
		int id = getIdentifier(fullName, null, null);
		if (id == 0)
			throw new NotFoundException(fullName);
		setReplacement(id, replacement, this);
	}

	public void setReplacement(String pkg, String type, String name, Object replacement) {
		int id = getIdentifier(name, type, pkg);
		if (id == 0)
			throw new NotFoundException(pkg + ":" + type + "/" + name);
		setReplacement(id, replacement, this);
	}

	public static void setSystemWideReplacement(int id, Object replacement) {
		setReplacement(id, replacement, null);
	}

	public static void setSystemWideReplacement(String fullName, Object replacement) {
		int id = getSystem().getIdentifier(fullName, null, null);
		if (id == 0)
			throw new NotFoundException(fullName);
		setReplacement(id, replacement, null);
	}

	public static void setSystemWideReplacement(String pkg, String type, String name, Object replacement) {
		int id = getSystem().getIdentifier(name, type, pkg);
		if (id == 0)
			throw new NotFoundException(pkg + ":" + type + "/" + name);
		setReplacement(id, replacement, null);
	}

	private static void setReplacement(int id, Object replacement, BridgeResources res) {
		String resDir = (res != null) ? res.mResDir : null;
		if (id == 0)
			throw new IllegalArgumentException("id 0 is not an allowed resource identifier");
		else if (resDir == null && id >= 0x7f000000)
			throw new IllegalArgumentException("ids >= 0x7f000000 are app specific and cannot be set for the framework");

		if (replacement instanceof Drawable)
			throw new IllegalArgumentException("Drawable replacements are deprecated since Bridge 2.1. Use DrawableLoader instead.");

		// Cache that we have a replacement for this ID, false positives are accepted to save memory.
		if (id < 0x7f000000) {
			int cacheKey = (id & 0x00070000) >> 11 | (id & 0xf8) >> 3;
			synchronized (sSystemReplacementsCache) {
				sSystemReplacementsCache[cacheKey] |= 1 << (id & 7);
			}
		} else {
			int cacheKey = (id & 0x00070000) >> 12 | (id & 0x78) >> 3;
			synchronized (res.mReplacementsCache) {
				res.mReplacementsCache[cacheKey] |= 1 << (id & 7);
			}
		}

		synchronized (sReplacements) {
			HashMap<String, Object> inner = sReplacements.get(id);
			if (inner == null) {
				inner = new HashMap<String, Object>();
				sReplacements.put(id, inner);
			}
			inner.put(resDir, replacement);
		}
	}

	// =======================================================
	//   RETURNING REPLACEMENTS
	// =======================================================

	private Object getReplacement(int id) {
		if (id <= 0)
			return null;

		// Check the cache whether it's worth looking for replacements
		if (id < 0x7f000000) {
			int cacheKey = (id & 0x00070000) >> 11 | (id & 0xf8) >> 3;
			if ((sSystemReplacementsCache[cacheKey] & (1 << (id & 7))) == 0)
				return null;
		} else if (mResDir != null) {
			int cacheKey = (id & 0x00070000) >> 12 | (id & 0x78) >> 3;
			if ((mReplacementsCache[cacheKey] & (1 << (id & 7))) == 0)
				return null;
		}

		HashMap<String, Object> inner;
		synchronized (sReplacements) {
			inner = sReplacements.get(id);
		}

		if (inner == null)
			return null;

		synchronized (inner) {
			Object result = inner.get(mResDir);
			if (result != null || mResDir == null)
				return result;
			return inner.get(null);
		}
	}

	@Override
	public XmlResourceParser getAnimation(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();

			boolean loadedFromCache = isXmlCached(repRes, repId);
			XmlResourceParser result = repRes.getAnimation(repId);

			if (!loadedFromCache) {
				int parseState = getIntField(result, "mParseState");
				rewriteXmlReferencesNative(parseState, this, repRes);
			}

			return result;
		}
		return super.getAnimation(id);
	}

	@Override
	public boolean getBoolean(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof Boolean) {
			return (Boolean) replacement;
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getBoolean(repId);
		}
		return super.getBoolean(id);
	}

	@Override
	public int getColor(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof Integer) {
			return (Integer) replacement;
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getColor(repId);
		}
		return super.getColor(id);
	}

	@Override
	public ColorStateList getColorStateList(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof ColorStateList) {
			return (ColorStateList) replacement;
		} else if (replacement instanceof Integer) {
			int color = (Integer) replacement;
			synchronized (sColorStateListCache) {
				ColorStateList result = sColorStateListCache.get(color);
				if (result == null) {
					result = ColorStateList.valueOf(color);
					sColorStateListCache.put(color, result);
				}
				return result;
			}
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getColorStateList(repId);
		}
		return super.getColorStateList(id);
	}

	@Override
	public float getDimension(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof DimensionReplacement) {
			return ((DimensionReplacement) replacement).getDimension(getDisplayMetrics());
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getDimension(repId);
		}
		return super.getDimension(id);
	}

	@Override
	public int getDimensionPixelOffset(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof DimensionReplacement) {
			return ((DimensionReplacement) replacement).getDimensionPixelOffset(getDisplayMetrics());
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getDimensionPixelOffset(repId);
		}
		return super.getDimensionPixelOffset(id);
	}

	@Override
	public int getDimensionPixelSize(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof DimensionReplacement) {
			return ((DimensionReplacement) replacement).getDimensionPixelSize(getDisplayMetrics());
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getDimensionPixelSize(repId);
		}
		return super.getDimensionPixelSize(id);
	}

	@Override
	public Drawable getDrawable(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof DrawableLoader) {
			try {
				Drawable result = ((DrawableLoader) replacement).newDrawable(this, id);
				if (result != null)
					return result;
			} catch (Throwable t) { Bridge.log(t); }
		} else if (replacement instanceof Integer) {
			return new ColorDrawable((Integer) replacement);
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getDrawable(repId);
		}
		return super.getDrawable(id);
	}

	@Override
	public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof DrawableLoader) {
			try {
				Drawable result = ((DrawableLoader) replacement).newDrawableForDensity(this, id, density);
				if (result != null)
					return result;
			} catch (Throwable t) { Bridge.log(t); }
		} else if (replacement instanceof Integer) {
			return new ColorDrawable((Integer) replacement);
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getDrawableForDensity(repId, density);
		}
		return super.getDrawableForDensity(id, density);
	}

	@Override
	public float getFraction(int id, int base, int pbase) {
		Object replacement = getReplacement(id);
		if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getFraction(repId, base, pbase);
		}
		return super.getFraction(id, base, pbase);
	}

	@Override
	public int getInteger(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof Integer) {
			return (Integer) replacement;
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getInteger(repId);
		}
		return super.getInteger(id);
	}

	@Override
	public int[] getIntArray(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof int[]) {
			return (int[]) replacement;
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getIntArray(repId);
		}
		return super.getIntArray(id);
	}

	@Override
	public XmlResourceParser getLayout(int id) throws NotFoundException {
		XmlResourceParser result;
		Object replacement = getReplacement(id);
		if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();

			boolean loadedFromCache = isXmlCached(repRes, repId);
			result = repRes.getLayout(repId);

			if (!loadedFromCache) {
				int parseState = getIntField(result, "mParseState");
				rewriteXmlReferencesNative(parseState, this, repRes);
			}
		} else {
			result = super.getLayout(id);
		}

		// Check whether this layout is hooked
		HashMap<String, CopyOnWriteSortedSet<Bridge_LayoutInflated>> inner;
		synchronized (sLayoutCallbacks) {
			inner = sLayoutCallbacks.get(id);
		}
		if (inner != null) {
			CopyOnWriteSortedSet<Bridge_LayoutInflated> callbacks;
			synchronized (inner) {
				callbacks = inner.get(mResDir);
				if (callbacks == null && mResDir != null)
					callbacks = inner.get(null);
			}
			if (callbacks != null) {
				String variant = "layout";
				TypedValue value = (TypedValue) getObjectField(this, "mTmpValue");
				getValue(id, value, true);
				if (value.type == TypedValue.TYPE_STRING) {
					String[] components = value.string.toString().split("/", 3);
					if (components.length == 3)
						variant = components[1];
					else
						Bridge.log("Unexpected resource path \"" + value.string.toString()
								+ "\" for resource id 0x" + Integer.toHexString(id));
				} else {
					Bridge.log(new NotFoundException("Could not find file name for resource id 0x") + Integer.toHexString(id));
				}

				synchronized (sXmlInstanceDetails) {
					synchronized (sResourceNames) {
						HashMap<String, ResourceNames> resNamesInner = sResourceNames.get(id);
						if (resNamesInner != null) {
							synchronized (resNamesInner) {
								XMLInstanceDetails details = new XMLInstanceDetails(resNamesInner.get(mResDir), variant, callbacks);
								sXmlInstanceDetails.put(result, details);

								// if we were called inside LayoutInflater.parseInclude, store the details for it
								MethodHookParam top = sIncludedLayouts.get().peek();
								if (top != null)
									top.setObjectExtra(EXTRA_XML_INSTANCE_DETAILS, details);
							}
						}
					}
				}
			}
		}

		return result;
	}

	@Override
	public Movie getMovie(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getMovie(repId);
		}
		return super.getMovie(id);
	}

	@Override
	public CharSequence getQuantityText(int id, int quantity) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getQuantityText(repId, quantity);
		}
		return super.getQuantityText(id, quantity);
	}
	// these are handled by getQuantityText:
	// public String getQuantityString(int id, int quantity);
	// public String getQuantityString(int id, int quantity, Object... formatArgs);

	@Override
	public String[] getStringArray(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof String[]) {
			return (String[]) replacement;
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getStringArray(repId);
		}
		return super.getStringArray(id);
	}

	@Override
	public CharSequence getText(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof CharSequence) {
			return (CharSequence) replacement;
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getText(repId);
		}
		return super.getText(id);
	}
	// these are handled by getText:
	// public String getString(int id);
	// public String getString(int id, Object... formatArgs);

	@Override
	public CharSequence getText(int id, CharSequence def) {
		Object replacement = getReplacement(id);
		if (replacement instanceof CharSequence) {
			return (CharSequence) replacement;
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getText(repId, def);
		}
		return super.getText(id, def);
	}

	@Override
	public CharSequence[] getTextArray(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof CharSequence[]) {
			return (CharSequence[]) replacement;
		} else if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();
			return repRes.getTextArray(repId);
		}
		return super.getTextArray(id);
	}

	@Override
	public XmlResourceParser getXml(int id) throws NotFoundException {
		Object replacement = getReplacement(id);
		if (replacement instanceof BridgeResForwarder) {
			Resources repRes = ((BridgeResForwarder) replacement).getResources();
			int repId = ((BridgeResForwarder) replacement).getId();

			boolean loadedFromCache = isXmlCached(repRes, repId);
			XmlResourceParser result = repRes.getXml(repId);

			if (!loadedFromCache) {
				int parseState = getIntField(result, "mParseState");
				rewriteXmlReferencesNative(parseState, this, repRes);
			}

			return result;
		}
		return super.getXml(id);
	}

	private static boolean isXmlCached(Resources res, int id) {
		int[] mCachedXmlBlockIds = (int[]) getObjectField(res, "mCachedXmlBlockIds");
		synchronized (mCachedXmlBlockIds) {
			final int num = mCachedXmlBlockIds.length;
			for (int i = 0; i < num; i++) {
				if (mCachedXmlBlockIds[i] == id)
					return true;
			}
		}
		return false;
	}

	private static native void rewriteXmlReferencesNative(int parserPtr, BridgeResources origRes, Resources repRes);

	/**
	 * Used to replace reference IDs in XMLs.
	 *
	 * When resource requests are forwarded to modules, the may include references to resources with the same
	 * name as in the original resources, but the IDs generated by aapt will be different. rewriteXmlReferencesNative
	 * walks through all references and calls this function to find out the original ID, which it then writes to
	 * the compiled XML file in the memory.
	 */
	private static int translateResId(int id, BridgeResources origRes, Resources repRes) {
		try {
			String entryName = repRes.getResourceEntryName(id);
			String entryType = repRes.getResourceTypeName(id);
			String origPackage = origRes.mPackageName;
			int origResId = 0;
			try {
				// look for a resource with the same name and type in the original package
				origResId = origRes.getIdentifier(entryName, entryType, origPackage);
			} catch (NotFoundException ignored) {}

			boolean repResDefined = false;
			try {
				final TypedValue tmpValue = new TypedValue();
				repRes.getValue(id, tmpValue, false);
				// if a resource has not been defined (i.e. only a resource ID has been created), it will equal "false"
				// this means a boolean "false" value is not detected of it is directly referenced in an XML file
				repResDefined = !(tmpValue.type == TypedValue.TYPE_INT_BOOLEAN && tmpValue.data == 0);
			} catch (NotFoundException ignored) {}

			if (!repResDefined && origResId == 0 && !entryType.equals("id")) {
				Bridge.log(entryType + "/" + entryName + " is neither defined in module nor in original resources");
				return 0;
			}

			// exists only in module, so create a fake resource id
			if (origResId == 0)
				origResId = getFakeResId(repRes, id);

			// IDs will never be loaded, no need to set a replacement
			if (repResDefined && !entryType.equals("id"))
				origRes.setReplacement(origResId, new BridgeResForwarder(repRes, id));

			return origResId;
		} catch (Exception e) {
			Bridge.log(e);
			return id;
		}
	}

	public static int getFakeResId(String resName) {
		return 0x7e000000 | (resName.hashCode() & 0x00ffffff);
	}

	public static int getFakeResId(Resources res, int id) {
		return getFakeResId(res.getResourceName(id));
	}

	public int addResource(Resources res, int id) {
		int fakeId = getFakeResId(res, id);
		synchronized (sReplacements) {
			if (sReplacements.indexOfKey(fakeId) < 0)
				setReplacement(fakeId, new BridgeResForwarder(res, id));
		}
		return fakeId;
	}

	/**
	 * Similar to {@link #translateResId}, but used to determine the original ID of attribute names
	 */
	private static int translateAttrId(String attrName, BridgeResources origRes) {
		String origPackage = origRes.mPackageName;
		int origAttrId = 0;
		try {
			origAttrId = origRes.getIdentifier(attrName, "attr", origPackage);
		} catch (NotFoundException e) {
			Bridge.log("Attribute " + attrName + " not found in original resources");
		}
		return origAttrId;
	}

	// =======================================================
	//   XTypedArray class
	// =======================================================
	/**
	 * {@link TypedArray} replacement that replaces values on-the-fly.
	 * Mainly used when inflating layouts.
	 */
	public static class BridgeTypedArray extends TypedArray {
		private boolean mIsObjectInited;
		private BridgeResources mRes;

		// Dummy, will never be called (objects are transferred to this class only).
		private BridgeTypedArray() {
			super(null, null, null, 0);
			throw new UnsupportedOperationException();
		}

		public void initObject(BridgeResources res) {
			if (mIsObjectInited)
				throw new IllegalStateException("Object has already been initialized");

			this.mRes = res;
			this.mIsObjectInited = true;
		}

		@Override
		public boolean getBoolean(int index, boolean defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof Boolean) {
				return (Boolean) replacement;
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getBoolean(repId);
			}
			return super.getBoolean(index, defValue);
		}

		@Override
		public int getColor(int index, int defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof Integer) {
				return (Integer) replacement;
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getColor(repId);
			}
			return super.getColor(index, defValue);
		}

		@Override
		public ColorStateList getColorStateList(int index) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof ColorStateList) {
				return (ColorStateList) replacement;
			} else if (replacement instanceof Integer) {
				int color = (Integer) replacement;
				synchronized (sColorStateListCache) {
					ColorStateList result = sColorStateListCache.get(color);
					if (result == null) {
						result = ColorStateList.valueOf(color);
						sColorStateListCache.put(color, result);
					}
					return result;
				}
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getColorStateList(repId);
			}
			return super.getColorStateList(index);
		}

		@Override
		public float getDimension(int index, float defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getDimension(repId);
			}
			return super.getDimension(index, defValue);
		}

		@Override
		public int getDimensionPixelOffset(int index, int defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getDimensionPixelOffset(repId);
			}
			return super.getDimensionPixelOffset(index, defValue);
		}

		@Override
		public int getDimensionPixelSize(int index, int defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getDimensionPixelSize(repId);
			}
			return super.getDimensionPixelSize(index, defValue);
		}

		@Override
		public Drawable getDrawable(int index) {
			final int resId = getResourceId(index, 0);
			Object replacement = mRes.getReplacement(resId);
			if (replacement instanceof DrawableLoader) {
				try {
					Drawable result = ((DrawableLoader) replacement).newDrawable(mRes, resId);
					if (result != null)
						return result;
				} catch (Throwable t) { Bridge.log(t); }
			} else if (replacement instanceof Integer) {
				return new ColorDrawable((Integer) replacement);
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getDrawable(repId);
			}
			return super.getDrawable(index);
		}

		@Override
		public float getFloat(int index, float defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				// dimensions seem to be the only way to define floats by references
				return repRes.getDimension(repId);
			}
			return super.getFloat(index, defValue);
		}

		@Override
		public float getFraction(int index, int base, int pbase, float defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				// dimensions seem to be the only way to define floats by references
				return repRes.getFraction(repId, base, pbase);
			}
			return super.getFraction(index, base, pbase, defValue);
		}

		@Override
		public int getInt(int index, int defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof Integer) {
				return (Integer) replacement;
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getInteger(repId);
			}
			return super.getInt(index, defValue);
		}

		@Override
		public int getInteger(int index, int defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof Integer) {
				return (Integer) replacement;
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getInteger(repId);
			}
			return super.getInteger(index, defValue);
		}

		@Override
		public int getLayoutDimension(int index, int defValue) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getDimensionPixelSize(repId);
			}
			return super.getLayoutDimension(index, defValue);
		}

		@Override
		public int getLayoutDimension(int index, String name) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getDimensionPixelSize(repId);
			}
			return super.getLayoutDimension(index, name);
		}

		@Override
		public String getString(int index) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof CharSequence) {
				return replacement.toString();
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getString(repId);
			}
			return super.getString(index);
		}

		@Override
		public CharSequence getText(int index) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof CharSequence) {
				return (CharSequence) replacement;
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getText(repId);
			}
			return super.getText(index);
		}

		@Override
		public CharSequence[] getTextArray(int index) {
			Object replacement = mRes.getReplacement(getResourceId(index, 0));
			if (replacement instanceof CharSequence[]) {
				return (CharSequence[]) replacement;
			} else if (replacement instanceof BridgeResForwarder) {
				Resources repRes = ((BridgeResForwarder) replacement).getResources();
				int repId = ((BridgeResForwarder) replacement).getId();
				return repRes.getTextArray(repId);
			}
			return super.getTextArray(index);
		}
	}


	// =======================================================
	//   DrawableLoader class
	// =======================================================
	/**
	 * callback function for {@link BridgeResources#getDrawable} and {@link BridgeResources#getDrawableForDensity}
	 */
	public static abstract class DrawableLoader {
		public abstract Drawable newDrawable(BridgeResources res, int id) throws Throwable;

		public Drawable newDrawableForDensity(BridgeResources res, int id, int density) throws Throwable {
			return newDrawable(res, id);
		}
	}


	// =======================================================
	//   DimensionReplacement class
	// =======================================================
	/**
	 * callback function for {@link BridgeResources#getDimension}, {@link BridgeResources#getDimensionPixelOffset}
	 * and {@link BridgeResources#getDimensionPixelSize}
	 */
	public static class DimensionReplacement {
		private final float mValue;
		private final int mUnit;

		/**
		 * Create an object that can be use for {@link #setReplacement} to replace a dimension resource.
		 * @param value The value of the replacement, in the unit specified with the next parameter.
		 * @param unit One of the {@code COMPLEX_UNIT_*} constants in @{link TypedValue}.
		 */
		public DimensionReplacement(float value, int unit) {
			mValue = value;
			mUnit = unit;
		}

		/** @see Resources#getDimension(int) */
		public float getDimension(DisplayMetrics metrics) {
			return TypedValue.applyDimension(mUnit, mValue, metrics);
		}

		/** @see Resources#getDimensionPixelOffset(int) */
		public int getDimensionPixelOffset(DisplayMetrics metrics) {
			return (int) TypedValue.applyDimension(mUnit, mValue, metrics);
		}

		/** @see Resources#getDimensionPixelSize(int) */
		public int getDimensionPixelSize(DisplayMetrics metrics) {
			final float f = TypedValue.applyDimension(mUnit, mValue, metrics);
			final int res = (int)(f+0.5f);
			if (res != 0) return res;
			if (mValue == 0) return 0;
			if (mValue > 0) return 1;
			return -1;
		}
	}

	// =======================================================
	//   INFLATING LAYOUTS
	// =======================================================

	private class XMLInstanceDetails {
		public final ResourceNames resNames;
		public final String variant;
		public final CopyOnWriteSortedSet<Bridge_LayoutInflated> callbacks;
		public final BridgeResources res = BridgeResources.this;

		private XMLInstanceDetails(ResourceNames resNames, String variant, CopyOnWriteSortedSet<Bridge_LayoutInflated> callbacks) {
			this.resNames = resNames;
			this.variant = variant;
			this.callbacks = callbacks;
		}
	}

	/** @see #hookLayout(String, String, String, Bridge_LayoutInflated) */
	public Bridge_LayoutInflated.Unhook hookLayout(int id, Bridge_LayoutInflated callback) {
		return hookLayoutInternal(mResDir, id, getResourceNames(id), callback);
	}

	/** @see #hookLayout(String, String, String, Bridge_LayoutInflated) */
	public Bridge_LayoutInflated.Unhook hookLayout(String fullName, Bridge_LayoutInflated callback) {
		int id = getIdentifier(fullName, null, null);
		if (id == 0)
			throw new NotFoundException(fullName);
		return hookLayout(id, callback);
	}

	/**
	 * Add a function to be called once a specific layout has been inflated.
	 * @param pkg Package, e.g. <code>com.android.systemui</code>
	 * @param type Type (in this case always <code>layout</code>)
	 * @param name Name of the resource (e.g. <code>statusbar</code>)
	 * @param callback Handler to be called
	 */
	public Bridge_LayoutInflated.Unhook hookLayout(String pkg, String type, String name, Bridge_LayoutInflated callback) {
		int id = getIdentifier(name, type, pkg);
		if (id == 0)
			throw new NotFoundException(pkg + ":" + type + "/" + name);
		return hookLayout(id, callback);
	}

	/** @see #hookLayout(String, String, String, Bridge_LayoutInflated) */
	public static Bridge_LayoutInflated.Unhook hookSystemWideLayout(int id, Bridge_LayoutInflated callback) {
		if (id >= 0x7f000000)
			throw new IllegalArgumentException("ids >= 0x7f000000 are app specific and cannot be set for the framework");
		return hookLayoutInternal(null, id, getSystemResourceNames(id), callback);
	}

	/** @see #hookLayout(String, String, String, Bridge_LayoutInflated) */
	public static Bridge_LayoutInflated.Unhook hookSystemWideLayout(String fullName, Bridge_LayoutInflated callback) {
		int id = getSystem().getIdentifier(fullName, null, null);
		if (id == 0)
			throw new NotFoundException(fullName);
		return hookSystemWideLayout(id, callback);
	}

	/** @see #hookLayout(String, String, String, Bridge_LayoutInflated) */
	public static Bridge_LayoutInflated.Unhook hookSystemWideLayout(String pkg, String type, String name, Bridge_LayoutInflated callback) {
		int id = getSystem().getIdentifier(name, type, pkg);
		if (id == 0)
			throw new NotFoundException(pkg + ":" + type + "/" + name);
		return hookSystemWideLayout(id, callback);
	}

	private static Bridge_LayoutInflated.Unhook hookLayoutInternal(String resDir, int id, ResourceNames resNames, Bridge_LayoutInflated callback) {
		if (id == 0)
			throw new IllegalArgumentException("id 0 is not an allowed resource identifier");

		HashMap<String, CopyOnWriteSortedSet<Bridge_LayoutInflated>> inner;
		synchronized (sLayoutCallbacks) {
			inner = sLayoutCallbacks.get(id);
			if (inner == null) {
				inner = new HashMap<String, CopyOnWriteSortedSet<Bridge_LayoutInflated>>();
				sLayoutCallbacks.put(id, inner);
			}
		}

		CopyOnWriteSortedSet<Bridge_LayoutInflated> callbacks;
		synchronized (inner) {
			callbacks = inner.get(resDir);
			if (callbacks == null) {
				callbacks = new CopyOnWriteSortedSet<Bridge_LayoutInflated>();
				inner.put(resDir, callbacks);
			}
		}

		callbacks.add(callback);

		putResourceNames(resDir, resNames);

		return callback.new Unhook(resDir, id);
	}

	public static void unhookLayout(String resDir, int id, Bridge_LayoutInflated callback) {
		HashMap<String, CopyOnWriteSortedSet<Bridge_LayoutInflated>> inner;
		synchronized (sLayoutCallbacks) {
			inner = sLayoutCallbacks.get(id);
			if (inner == null)
				return;
		}

		CopyOnWriteSortedSet<Bridge_LayoutInflated> callbacks;
		synchronized (inner) {
			callbacks = inner.get(resDir);
			if (callbacks == null)
				return;
		}

		callbacks.remove(callback);
	}
}
