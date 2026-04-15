package com.supper.stasis.client.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IrisShaderpackCompat {
	private static final Logger LOGGER = LoggerFactory.getLogger("stasis/iris_trail_compat");
	private static final String IRIS_MOD_ID = "iris";
	private static boolean initialized = false;
	private static Method irisApiGetInstanceMethod;
	private static Method irisApiIsShaderPackInUseMethod;

	private IrisShaderpackCompat() {
	}

	public static boolean isShaderPackInUse() {
		if (!ensureInitialized()) {
			return false;
		}

		try {
			Object irisApi = irisApiGetInstanceMethod.invoke(null);
			if (irisApi == null) {
				return false;
			}
			Object result = irisApiIsShaderPackInUseMethod.invoke(irisApi);
			return result instanceof Boolean && (Boolean) result;
		} catch (Throwable throwable) {
			LOGGER.debug("Iris shaderpack state query failed: {}", throwable.toString());
			return false;
		}
	}

	private static boolean ensureInitialized() {
		if (initialized) {
			return irisApiGetInstanceMethod != null && irisApiIsShaderPackInUseMethod != null;
		}

		initialized = true;
		if (!FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID)) {
			return false;
		}

		try {
			Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			irisApiGetInstanceMethod = irisApiClass.getMethod("getInstance");
			irisApiIsShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
			return true;
		} catch (Throwable throwable) {
			LOGGER.debug("Could not bind Iris shaderpack compat: {}", throwable.toString());
			irisApiGetInstanceMethod = null;
			irisApiIsShaderPackInUseMethod = null;
			return false;
		}
	}
}
