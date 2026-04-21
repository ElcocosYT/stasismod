package com.supper.stasis.client.render;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class AfterimageRenderState {
	private static final ThreadLocal<AfterimageFrame> CURRENT_FRAME = new ThreadLocal<>();

	private AfterimageRenderState() {
	}

	public static void push(
			float alpha,
			EntityPose pose,
			boolean sneaking,
			ItemStack[] equippedStacks,
			boolean usingItem,
			Hand activeHand,
			int itemUseTimeLeft,
			boolean renderOnFire
	) {
		int color = ((int) (Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f) << 24)
				| (6 << 16)
				| (244 << 8)
				| 255;
		CURRENT_FRAME.set(new AfterimageFrame(color, pose, sneaking, equippedStacks, usingItem, activeHand, itemUseTimeLeft, renderOnFire));
	}

	public static void pop() {
		CURRENT_FRAME.remove();
	}

	public static boolean isActive() {
		return CURRENT_FRAME.get() != null;
	}

	public static int getColor() {
		AfterimageFrame frame = CURRENT_FRAME.get();
		return frame != null ? frame.color() : 0xFFFFFFFF;
	}

	public static float getRedFloat() {
		return ((getColor() >> 16) & 0xFF) / 255.0f;
	}

	public static float getGreenFloat() {
		return ((getColor() >> 8) & 0xFF) / 255.0f;
	}

	public static float getBlueFloat() {
		return (getColor() & 0xFF) / 255.0f;
	}

	public static float getAlphaFloat() {
		return ((getColor() >> 24) & 0xFF) / 255.0f;
	}

	public static EntityPose getPoseOverride() {
		AfterimageFrame frame = CURRENT_FRAME.get();
		return frame != null ? frame.pose() : null;
	}

	public static Boolean getSneakingOverride() {
		AfterimageFrame frame = CURRENT_FRAME.get();
		return frame != null ? frame.sneaking() : null;
	}

	public static Boolean getSneakingPoseOverride() {
		AfterimageFrame frame = CURRENT_FRAME.get();
		return frame != null ? frame.pose() == EntityPose.CROUCHING : null;
	}

	public static ItemStack getEquippedStack(EquipmentSlot slot) {
		AfterimageFrame frame = CURRENT_FRAME.get();
		if (frame == null) {
			return ItemStack.EMPTY;
		}

		ItemStack[] equippedStacks = frame.equippedStacks();
		if (equippedStacks == null || slot.ordinal() >= equippedStacks.length) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = equippedStacks[slot.ordinal()];
		return stack != null ? stack : ItemStack.EMPTY;
	}

	public static Boolean getUsingItemOverride() {
		AfterimageFrame frame = CURRENT_FRAME.get();
		return frame != null ? frame.usingItem() : null;
	}

	public static Hand getActiveHandOverride() {
		AfterimageFrame frame = CURRENT_FRAME.get();
		return frame != null ? frame.activeHand() : null;
	}

	public static Integer getItemUseTimeLeftOverride() {
		AfterimageFrame frame = CURRENT_FRAME.get();
		return frame != null ? frame.itemUseTimeLeft() : null;
	}

	public static Boolean getRenderOnFireOverride() {
		AfterimageFrame frame = CURRENT_FRAME.get();
		return frame != null ? frame.renderOnFire() : null;
	}

	private record AfterimageFrame(
			int color,
			EntityPose pose,
			boolean sneaking,
			ItemStack[] equippedStacks,
			boolean usingItem,
			Hand activeHand,
			int itemUseTimeLeft,
			boolean renderOnFire
	) {
	}
}
