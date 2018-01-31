package android.content.res;

import com.tencent.qrom.dynxloaderbridge.Bridge;

import android.app.AndroidAppHelper;
import android.util.DisplayMetrics;

/**
 * Resources that can be created for an Bridge module.
 */
public class BridgeModuleResources extends Resources {
	private BridgeModuleResources(AssetManager assets, DisplayMetrics metrics, Configuration config) {
		super(assets, metrics, config);
	}

	/**
	 * Usually called with the automatically injected {@code MODULE_PATH} constant of the first parameter
	 * and the resources received in the callback for {@link Bridge#hookInitPackageResources} (or
	 * {@code null} for system-wide replacements.
	 */
	public static BridgeModuleResources createInstance(String modulePath, BridgeResources origRes) {
		if (modulePath == null)
			throw new IllegalArgumentException("modulePath must not be null");

		AssetManager assets = new AssetManager();
		assets.addAssetPath(modulePath);

		BridgeModuleResources res;
		if (origRes != null)
			res = new BridgeModuleResources(assets, origRes.getDisplayMetrics(),	origRes.getConfiguration());
		else
			res = new BridgeModuleResources(assets, null, null);

		AndroidAppHelper.addActiveResource(modulePath, res.hashCode(), false, res);
		return res;
	}

	/**
	 * Create an {@link BridgeResForwarder} instances that forwards requests to {@code id} in this resource.
	 */
	public BridgeResForwarder fwd(int id) {
		return new BridgeResForwarder(this, id);
	}
}
