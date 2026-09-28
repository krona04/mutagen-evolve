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
 * Тело через Pehkui: ширина и высота хитбокса, высота глаз, дальность рук и инерция меняются его
 * собственными типами масштаба, а синхронизацию с клиентами он делает сам. Форму модели меняет само
 * превращение, поэтому модельные масштабы Pehkui не трогаются.
 * <p>
 * Pehkui не нужен для сборки: к его API ({@code virtuoel.pehkui.api}) мод обращается через рефлексию.
 * Если API не нашлось или изменилось, {@link #create()} вернёт null и мод останется на ванильной прослойке.
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
            // WIDTH и HEIGHT в Pehkui растягивают и хитбокс, и модель. Модель уже приняла форму существа,
            // растянуть её второй раз — значит исказить, поэтому они держатся на единице, а меняется
            // только хитбокс.
            set(player, width, 1.0F);
            set(player, height, 1.0F);
            set(player, hitboxWidth, shape.widthFactor());
            set(player, hitboxHeight, heightFactor);
            // Высота глаз у Pehkui следует за высотой хитбокса; свою посадку глаз существа мод поправляет
            // сам в миксине размеров игрока, поэтому собственный множитель глаз Pehkui держится на единице.
            set(player, eyeHeight, 1.0F);
            set(player, modelWidth, shape.renderScale());
            set(player, modelHeight, shape.renderScale());
            set(player, blockReach, (BodyShape.BASE_BLOCK_REACH + shape.reach()) / BodyShape.BASE_BLOCK_REACH);
            set(player, entityReach, (BodyShape.BASE_ENTITY_REACH + shape.reach()) / BodyShape.BASE_ENTITY_REACH);
            // Большое тело шагает шире и разгоняется тяжелее: инерция идёт за ростом, но вполовину.
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
        // Масштаб не сохраняется в игроке: мод выставляет его заново каждую секунду, а без мода тело
        // должно вернуться к человеческому, а не остаться великаном навсегда.
        setPersistence.invoke(data, Boolean.FALSE);
        setScaleTickDelay.invoke(data, TICK_DELAY);
        setTargetScale.invoke(data, target);
    }

    @Override
    public boolean ownsDimensions() {
        return false;
    }
}
