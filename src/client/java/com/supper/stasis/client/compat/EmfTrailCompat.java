package com.supper.stasis.client.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional integration with Entity Model Features (EMF). Fresh Animations and similar packs drive
 * player limbs through EMF's CEM animation graph, which ignores the vanilla {@code LimbAnimator} fields
 * we snapshot for trails. EMF exposes {@code lockEntityToVanillaModel} so a one-off render uses the
 * vanilla humanoid pose (our frozen limb/pose state) instead of live CEM animation.
 */
public final class EmfTrailCompat {
	private static final Logger LOGGER = LoggerFactory.getLogger("stasis/emf_trail_compat");
	private static final String EMF_MOD_ID = "entity_model_features";

	private static boolean checked;
	private static Method emfEntityOf;
	private static Method lockVanilla;
	private static Method unlockVanilla;

	private EmfTrailCompat() {
	}

	public static boolean isAvailable() {
		init();
		return emfEntityOf != null && lockVanilla != null && unlockVanilla != null;
	}

	private static void init() {
		if (checked) {
			return;
		}
		checked = true;
		if (!FabricLoader.getInstance().isModLoaded(EMF_MOD_ID)) {
			return;
		}
		try {
			Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
			Class<?> emfEntity = Class.forName("traben.entity_model_features.utils.EMFEntity");
			emfEntityOf = api.getMethod("emfEntityOf", Entity.class);
			lockVanilla = api.getMethod("lockEntityToVanillaModel", emfEntity);
			unlockVanilla = api.getMethod("unlockEntityToVanillaModel", emfEntity);
		} catch (Throwable t) {
			LOGGER.debug("Could not bind EMF trail compat: {}", t.toString());
			emfEntityOf = null;
			lockVanilla = null;
			unlockVanilla = null;
		}
	}

	public static void runWithVanillaPlayerModel(Entity entity, Runnable action) {
		if (!isAvailable()) {
			action.run();
			return;
		}
		Object wrapped = null;
		boolean locked = false;
		try {
			wrapped = emfEntityOf.invoke(null, entity);
			if (wrapped != null) {
				Object result = lockVanilla.invoke(null, wrapped);
				locked = result instanceof Boolean && (Boolean) result;
			}
			action.run();
		} catch (Throwable t) {
			LOGGER.debug("EMF trail render failed: {}", t.toString());
		} finally {
			if (locked && wrapped != null) {
				try {
					unlockVanilla.invoke(null, wrapped);
				} catch (Throwable t) {
					LOGGER.debug("EMF trail unlock failed: {}", t.toString());
				}
			}
		}
	}
}
