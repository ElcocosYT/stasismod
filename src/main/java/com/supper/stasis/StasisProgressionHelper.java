package com.supper.stasis;

import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;

public final class StasisProgressionHelper {
	private static final String CLIENT_STATE_CLASS = "com.supper.stasis.client.StasisClientState";
	private static final String CLIENT_FREEZE_METHOD_NAME = "shouldFreezeVolatileProgress";
	private static final String CLIENT_ADVANCE_METHOD_NAME = "shouldAdvanceVolatileProgress";
	private static final String CLIENT_MOVEMENT_METHOD_NAME = "getMovementMultiplier";
	private static Method clientFreezeMethod;
	private static Method clientAdvanceMethod;
	private static Method clientMovementMethod;
	private static boolean lookedUpClientMethods = false;

	private StasisProgressionHelper() {
	}

	public static boolean shouldFreezeVolatileProgress(Entity entity) {
		if (entity == null) {
			return false;
		}

		if (!entity.getEntityWorld().isClient()) {
			return StasisManager.getInstance().shouldFreezeVolatileProgress(entity);
		}

		return shouldFreezeClientVolatileProgress(entity);
	}

	public static boolean shouldAdvanceVolatileProgress(Entity entity) {
		if (entity == null) {
			return true;
		}

		if (!entity.getEntityWorld().isClient()) {
			return StasisManager.getInstance().shouldAdvanceVolatileProgress(entity);
		}

		Method method = getClientAdvanceMethod();
		if (method == null) {
			return true;
		}

		try {
			return Boolean.TRUE.equals(method.invoke(null, entity));
		} catch (ReflectiveOperationException ignored) {
			return true;
		}
	}

	public static float getMovementMultiplier(Entity entity) {
		if (entity == null) {
			return 1.0f;
		}

		if (!entity.getEntityWorld().isClient()) {
			return StasisManager.getInstance().getMovementMultiplier(entity.getEntityWorld());
		}

		Method method = getClientMovementMethod();
		if (method == null) {
			return 1.0f;
		}

		try {
			Object result = method.invoke(null, entity);
			return result instanceof Float value ? value : 1.0f;
		} catch (ReflectiveOperationException ignored) {
			return 1.0f;
		}
	}

	private static boolean shouldFreezeClientVolatileProgress(Entity entity) {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
			return false;
		}

		Method method = getClientFreezeMethod();
		if (method == null) {
			return false;
		}

		try {
			return Boolean.TRUE.equals(method.invoke(null, entity));
		} catch (ReflectiveOperationException ignored) {
			return false;
		}
	}

	private static Method getClientFreezeMethod() {
		lookupClientMethods();
		return clientFreezeMethod;
	}

	private static Method getClientAdvanceMethod() {
		lookupClientMethods();
		return clientAdvanceMethod;
	}

	private static Method getClientMovementMethod() {
		lookupClientMethods();
		return clientMovementMethod;
	}

	private static void lookupClientMethods() {
		if (lookedUpClientMethods) {
			return;
		}

		synchronized (StasisProgressionHelper.class) {
			if (lookedUpClientMethods) {
				return;
			}

			try {
				Class<?> clientStateClass = Class.forName(CLIENT_STATE_CLASS);
				clientFreezeMethod = clientStateClass.getMethod(CLIENT_FREEZE_METHOD_NAME, Entity.class);
				clientAdvanceMethod = clientStateClass.getMethod(CLIENT_ADVANCE_METHOD_NAME, Entity.class);
				clientMovementMethod = clientStateClass.getMethod(CLIENT_MOVEMENT_METHOD_NAME, Entity.class);
			} catch (ReflectiveOperationException ignored) {
				clientFreezeMethod = null;
				clientAdvanceMethod = null;
				clientMovementMethod = null;
			}

			lookedUpClientMethods = true;
		}
	}
}
