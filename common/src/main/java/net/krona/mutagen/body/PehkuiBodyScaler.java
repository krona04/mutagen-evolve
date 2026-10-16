package net.krona.mutagen.body;

import com.mojang.logging.LogUtils;
import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.Trait;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Body via Pehkui: hitbox width and height, eye height, reach and inertia are changed by its
 * own scale types, and it handles syncing with clients itself. The model shape is changed by the
 * transformation itself, so Pehkui's model scales are left alone.
 * <p>
 * Pehkui is not needed to build: the mod accesses its API ({@code virtuoel.pehkui.api}) via reflection.
 * If the API is missing or has changed, {@link #create()} returns null and the mod stays on the vanilla layer.
 */
public final class PehkuiBodyScaler implements BodyScaler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TICK_DELAY = 20;
    private static final float EPSILON = 1.0E-3F;

    private final Method getScaleData;
    private final Method setTargetScale;
    private final Method getTargetScale;
    private final Method setScaleTickDelay;
    private final Method setPersistence;
    private final Object width;
    private final Object height;
    private final Object hitboxWidth;
    private final Object hitboxHeight;
    private final Object modelWidth;
    private final Object modelHeight;
    private final Object eyeHeight;
    private final Object blockReach;
    private final Object entityReach;
    private final Object motion;
    private boolean broken;

    private PehkuiBodyScaler() throws ReflectiveOperationException {
        Class<?> types = Class.forName("virtuoel.pehkui.api.ScaleTypes");
        Class<?> type = Class.forName("virtuoel.pehkui.api.ScaleType");
        Class<?> data = Class.forName("virtuoel.pehkui.api.ScaleData");
        getScaleData = type.getMethod("getScaleData", Entity.class);
        setTargetScale = data.getMethod("setTargetScale", float.class);
        getTargetScale = data.getMethod("getTargetScale");
        setScaleTickDelay = data.getMethod("setScaleTickDelay", int.class);
        setPersistence = data.getMethod("setPersistence", Boolean.class);
        width = types.getField("WIDTH").get(null);
        height = types.getField("HEIGHT").get(null);
        hitboxWidth = types.getField("HITBOX_WIDTH").get(null);
        hitboxHeight = types.getField("HITBOX_HEIGHT").get(null);
        modelWidth = types.getField("MODEL_WIDTH").get(null);
        modelHeight = types.getField("MODEL_HEIGHT").get(null);
        eyeHeight = types.getField("EYE_HEIGHT").get(null);
        blockReach = types.getField("BLOCK_REACH").get(null);
        entityReach = types.getField("ENTITY_REACH").get(null);
        motion = types.getField("MOTION").get(null);
    }

    @Nullable
    public static PehkuiBodyScaler create() {
        try {
            return new PehkuiBodyScaler();
        } catch (Throwable e) {
            LOGGER.warn("[Mutagen] Pehkui is installed, but its API was not recognised; using vanilla scaling", e);
            return null;
        }
    }

    @Override
    public String name() {
        return "pehkui";
    }

    @Override
    public void apply(ServerPlayer player, BodyShape shape, Trait.AttributeSink sink) {
        if (broken) {
            return;
        }
        float heightFactor = shape.heightFactor();
        try {
            // In Pehkui WIDTH and HEIGHT stretch both the hitbox and the model. The model has already taken the creature's shape,
            // and stretching it a second time would distort it, so they are held at one and only
            // the hitbox changes.
            set(player, width, 1.0F);
            set(player, height, 1.0F);
            set(player, hitboxWidth, shape.widthFactor());
            set(player, hitboxHeight, heightFactor);
            // Pehkui's eye height follows the hitbox height; the mod adjusts the creature's own eye placement
            // itself in the player dimensions mixin, so Pehkui's own eye multiplier is held at one.
            set(player, eyeHeight, 1.0F);
            set(player, modelWidth, shape.renderScale());
            set(player, modelHeight, shape.renderScale());
            set(player, blockReach, (BodyShape.BASE_BLOCK_REACH + shape.reach()) / BodyShape.BASE_BLOCK_REACH);
            set(player, entityReach, (BodyShape.BASE_ENTITY_REACH + shape.reach()) / BodyShape.BASE_ENTITY_REACH);
            // A big body takes wider steps and accelerates more heavily: inertia follows height, but at half the rate.
            set(player, motion, 1.0F + (heightFactor - 1.0F) * 0.5F);
        } catch (Throwable e) {
            broken = true;
            LOGGER.error("[Mutagen] Pehkui call failed; body scaling stops until restart", e);
        }
    }

    private void set(ServerPlayer player, Object type, float target) throws ReflectiveOperationException {
        Object data = getScaleData.invoke(type, player);
        float current = (float) getTargetScale.invoke(data);
        if (Math.abs(current - target) < EPSILON) {
            return;
        }
        // The scale is not saved on the player: the mod sets it again every second, and without the mod the body
        // must return to human, not stay a giant forever.
        setPersistence.invoke(data, Boolean.FALSE);
        setScaleTickDelay.invoke(data, TICK_DELAY);
        setTargetScale.invoke(data, target);
    }

    @Override
    public boolean ownsDimensions() {
        return false;
    }
}
