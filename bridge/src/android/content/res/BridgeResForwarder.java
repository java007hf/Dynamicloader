package android.content.res;

/**
 * Instances of this class are possible values for {@link BridgeResources#setReplacement}.
 * They forward the resource request to a different {@link Resources} instances with
 * a possibly different ID.
 */
public class BridgeResForwarder {
	private final Resources res;
	private final int id;

	public BridgeResForwarder(Resources res, int id) {
		this.res = res;
		this.id = id;
	}

	public Resources getResources() {
		return res;
	}

	public int getId() {
		return id;
	}
}
