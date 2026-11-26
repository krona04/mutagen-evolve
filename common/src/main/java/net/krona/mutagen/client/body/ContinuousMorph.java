package net.krona.mutagen.client.body;

import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Основной метод превращения: шесть костей игрока непрерывно деформируются в части модели существа,
 * а кожа меняется по пикселям. Работает для любого штамма, в том числе из датапака.
 * <p>
 * У встроенных штаммов поверх этого — авторские графики, позы и разбиение тела ({@link #builtIn}).
 * Штамм из датапака получает общий вариант: кости меняются в порядке {@code spread}, кость без пары
 * в модели существа вливается в корпус, а несколько костей на одной части делят её объём, не налезая
 * друг на друга. Старый путь (части поверх игрока) остаётся только для моделей, где нет корпуса, и для паука.
 */
public final class ContinuousMorph {
    private static final Map<Strain, Boolean> GENERIC = new WeakHashMap<>();

    private ContinuousMorph() {}

    public static boolean supports(Strain strain) {
        if (builtIn(strain)) return true;
        if (SpiderMorph.supports(strain)) return false;
        return GENERIC.computeIfAbsent(strain, ContinuousMorph::usable);
    }

    /** Встроенные штаммы с авторской хореографией превращения. */
    public static boolean builtIn(Strain strain) {
        if (!strain.id().getNamespace().equals("mutagen")) return false;
        return switch(strain.id().getPath()) {
            case "wolf", "zombie", "drowned", "skeleton", "piglin", "enderman", "creeper",
                    "bee", "guardian", "phantom", "blaze" -> true;
            default -> false;
        };
    }

    /** Имя профиля для таблиц графиков: встроенный путь или {@code "custom"}. */
    public static String profile(Strain strain) {
        return builtIn(strain) ? strain.id().getPath() : "custom";
    }

    /** Общему методу нужен корпус и хотя бы один куб у каждой части, во что превращаются кости. */
    private static boolean usable(Strain strain) {
        try {
            MorphPlan plan = MorphPlan.of(strain);
            if (plan.target(Strain.Bone.BODY) == null) return false;
            for (Strain.Bone bone : Strain.Bone.values()) {
                MorphPlan.Target target = target(strain, plan, bone);
                if (target == null) return false;
                MorphCuboid.core(target.model());
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static WolfMorph.Pose reshape(Strain strain, Strain.Bone bone, ModelPart part,
                                          MorphCuboid.Core core, Matrix4f rootGoal, float progress) {
        if(!merged(strain,bone)) {
            if(builtIn(strain)) return reshape(strain.id().getPath(),bone,part,core,rootGoal,progress);
            return reshapeBox(part,core,rootGoal,core.box(),customPhase(strain,bone,progress),progress);
        }
        float t=builtIn(strain)
                ? net.krona.mutagen.strain.TransformationProgress.dimensions(strain.id().getPath(),progress)
                : customPhase(strain,bone,progress);
        return reshapeBox(part,core,rootGoal,surface(strain,bone,core).box(),t,progress);
    }

    /**
     * График кости у штамма из датапака: каждая следующая кость в порядке {@code spread} начинает чуть
     * позже, но все заканчивают к полной форме вместе.
     */
    public static float customPhase(Strain strain, Strain.Bone bone, float progress) {
        return MorphSequence.window(Infection.overall(progress), 0.04F + 0.06F * strain.spreadIndex(bone), 1);
    }

    public static WolfMorph.Pose reshape(String strain, Strain.Bone bone, ModelPart part,
                                          MorphCuboid.Core core, Matrix4f rootGoal, float progress) {
        if (strain.equals("wolf")) return WolfMorph.reshape(bone,part,core,rootGoal,progress);
        float t=MorphSequence.bone(strain,bone,Infection.overall(progress));
        return reshapeBox(part,core,rootGoal,core.box(),t,progress);
    }

    public static WolfMorph.Pose reshapeBox(ModelPart part, MorphCuboid.Core core, Matrix4f rootGoal,
                                              Box destination, float t, float progress) {
        Matrix4f goal=new Matrix4f(rootGoal).mul(core.relative());
        Vector3f pivot=new Vector3f(part.x,part.y,part.z).lerp(Poses.pivot(goal),t);
        Poses.setRotation(part,Poses.rotation(part).slerp(Poses.rotation(goal),t));
        Box original=Box.of(part);
        Box shape=original.lerp(destination.times(Poses.scale(goal)),t);
        Box.Fit fit=original.fit(shape);
        part.xScale=fit.scale().x; part.yScale=fit.scale().y; part.zScale=fit.scale().z;
        Vector3f position=BodyMorph.rotate(part,fit.offset()).add(pivot);
        part.setPos(position.x,position.y,position.z);
        return new WolfMorph.Pose(pivot,shape,MorphSequence.window(Infection.overall(progress),0.10F,1));
    }

    public static MorphPlan.Target target(Strain strain, MorphPlan plan, Strain.Bone bone) {
        MorphPlan.Target target=plan.target(bone);
        if(target!=null) return target;
        if(bone!=Strain.Bone.BODY && strain.body().absorb().contains(bone)) return plan.target(Strain.Bone.BODY);
        // Кость, которой у существа нет пары, вливается в корпус, а не висит человеческой.
        if(bone!=Strain.Bone.BODY && !builtIn(strain)) return plan.target(Strain.Bone.BODY);
        if(bone==Strain.Bone.BODY && strain.id().getPath().equals("blaze"))
            return plan.grown().stream().filter(t -> t.part().part().equals("part0")).findFirst().orElse(null);
        return null;
    }

    public static boolean merged(Strain strain, Strain.Bone bone) {
        if(!builtIn(strain)) return sharers(strain,bone).size()>1;
        return strain.body().absorb().contains(bone)
                || bone==Strain.Bone.BODY && !strain.body().absorb().isEmpty();
    }

    /** Кости, которые превращаются в ту же часть существа, что и эта, в порядке костей. */
    public static List<Strain.Bone> sharers(Strain strain, Strain.Bone bone) {
        MorphPlan plan=MorphPlan.of(strain);
        MorphPlan.Target own=target(strain,plan,bone);
        List<Strain.Bone> result=new ArrayList<>();
        if(own==null) return result;
        for(Strain.Bone other:Strain.Bone.values()) {
            MorphPlan.Target target=target(strain,plan,other);
            if(target!=null && target.model()==own.model()) result.add(other);
        }
        return result;
    }

    /**
     * Кость, которая несёт на себе мелкие детали общей части (нос, уши, складки) — одна на группу,
     * иначе они рисовались бы по разу на каждую кость.
     */
    public static boolean carrier(Strain strain, Strain.Bone bone) {
        if(builtIn(strain)) return bone==Strain.Bone.BODY;
        List<Strain.Bone> group=sharers(strain,bone);
        return group.contains(Strain.Bone.BODY) ? bone==Strain.Bone.BODY : !group.isEmpty() && group.getFirst()==bone;
    }

    public static MorphCuboid.Surface surface(Strain strain, Strain.Bone bone, MorphCuboid.Core core) {
        if(!builtIn(strain)) {
            List<Strain.Bone> group=sharers(strain,bone);
            return MorphCuboid.surface(core.cube(),group.size()>1?share(core.box(),bone,group):core.box());
        }
        MorphCuboid.Surface surface = MorphCuboid.surface(core.cube(),partition(core.box(),bone,strain.body().absorb().size()));
        if (!strain.id().getPath().equals("phantom") || bone!=Strain.Bone.RIGHT_ARM && bone!=Strain.Bone.LEFT_ARM) return surface;
        return MorphCuboid.rotate(surface,CompactMorph.alignment(strain.id().getPath(),bone,core.box()).conjugate());
    }

    public static Box partition(Box whole, Strain.Bone bone, int absorbed) {
        if(absorbed!=5 && absorbed!=2) return whole;
        if(bone!=Strain.Bone.BODY && bone!=Strain.Bone.HEAD
                && bone!=Strain.Bone.RIGHT_ARM && bone!=Strain.Bone.LEFT_ARM
                && bone!=Strain.Bone.RIGHT_LEG && bone!=Strain.Bone.LEFT_LEG) return whole;
        // The six human solids become six adjacent solids of the creature, rather than shrinking away.
        int length=whole.size().y>=whole.size().z?1:2, height=length==1?2:1;
        float low=length==1?whole.minY():whole.minZ(), high=length==1?whole.maxY():whole.maxZ();
        if(absorbed==2) {
            if(bone!=Strain.Bone.BODY && bone!=Strain.Bone.RIGHT_LEG && bone!=Strain.Bone.LEFT_LEG) return whole;
            float split=low+(high-low)*0.70F;
            if(bone==Strain.Bone.BODY) return slice(whole,length,low,split);
            return half(slice(whole,length,split,high),bone==Strain.Bone.RIGHT_LEG);
        }
        float front=low+(high-low)*0.25F;
        if(bone==Strain.Bone.HEAD) return slice(whole,length,low,front);
        Box back=slice(whole,length,front,high);
        float bottom=height==1?whole.minY():whole.minZ(), top=height==1?whole.maxY():whole.maxZ();
        float middle=(bottom+top)*0.5F;
        if(bone==Strain.Bone.BODY) return slice(back,height,bottom,middle);
        Box underside=slice(back,height,middle,top);
        float rear=(front+high)*0.5F;
        boolean arm=bone==Strain.Bone.RIGHT_ARM||bone==Strain.Bone.LEFT_ARM;
        return half(slice(underside,length,arm?front:rear,arm?rear:high),
                bone==Strain.Bone.RIGHT_ARM||bone==Strain.Bone.RIGHT_LEG);
    }

    /**
     * Общее разбиение для датапаков: часть, в которую превращаются несколько костей, делится между ними
     * по длинной оси — голова спереди или сверху, затем руки, корпус и ноги; парные кости делят свой
     * отрезок на левую и правую половины. Так две кости никогда не ложатся в один объём.
     */
    public static Box share(Box whole, Strain.Bone bone, List<Strain.Bone> group) {
        List<List<Strain.Bone>> slots=new ArrayList<>();
        addSlot(slots,group,Strain.Bone.HEAD);
        addSlot(slots,group,Strain.Bone.RIGHT_ARM,Strain.Bone.LEFT_ARM);
        addSlot(slots,group,Strain.Bone.BODY);
        addSlot(slots,group,Strain.Bone.RIGHT_LEG,Strain.Bone.LEFT_LEG);
        int axis=whole.size().y>=whole.size().z?1:2;
        float low=axis==1?whole.minY():whole.minZ(), high=axis==1?whole.maxY():whole.maxZ();
        for(int i=0;i<slots.size();i++) {
            List<Strain.Bone> slot=slots.get(i);
            if(!slot.contains(bone)) continue;
            Box piece=slice(whole,axis,low+(high-low)*i/slots.size(),low+(high-low)*(i+1)/slots.size());
            if(slot.size()<2) return piece;
            return half(piece,bone==Strain.Bone.RIGHT_ARM||bone==Strain.Bone.RIGHT_LEG);
        }
        return whole;
    }

    private static void addSlot(List<List<Strain.Bone>> slots, List<Strain.Bone> group, Strain.Bone... bones) {
        List<Strain.Bone> slot=new ArrayList<>();
        for(Strain.Bone bone:bones) if(group.contains(bone)) slot.add(bone);
        if(!slot.isEmpty()) slots.add(slot);
    }

    private static Box half(Box b,boolean right) {
        float middle=(b.minX()+b.maxX())*0.5F;
        return new Box(right?b.minX():middle,b.minY(),b.minZ(),right?middle:b.maxX(),b.maxY(),b.maxZ());
    }
    private static Box slice(Box b,int axis,float from,float to) {
        return axis==1?new Box(b.minX(),from,b.minZ(),b.maxX(),to,b.maxZ())
                :new Box(b.minX(),b.minY(),from,b.maxX(),b.maxY(),to);
    }
}
