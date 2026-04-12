package com.supper.stasis.client;

/**
 * Marks when {@link net.minecraft.client.render.WorldRenderer#renderWeather} is on the stack so
 * {@code ClientWorld#getTime()} can be adjusted for precipitation without affecting the rest of the client.
 */
public final class WeatherRenderFrame {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private WeatherRenderFrame() {
    }

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int next = DEPTH.get() - 1;
        DEPTH.set(Math.max(0, next));
    }

    public static boolean isInWeatherPass() {
        return DEPTH.get() > 0;
    }
}
