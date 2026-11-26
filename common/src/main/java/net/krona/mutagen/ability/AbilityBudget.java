package net.krona.mutagen.ability;

/** Resource accounting does not depend on combat armour/resistance or client-side predictions. */
public final class AbilityBudget {
    private AbilityBudget() {}
    public record FoodPayment(int food,float saturation) {}
    public static FoodPayment food(int food,float saturation,float cost) {
        if(!Float.isFinite(cost)||cost<0||food+saturation<cost) return null;
        float fromSaturation=Math.min(saturation,cost);
        int fromFood=(int)Math.ceil(cost-fromSaturation);
        if(fromFood>food) return null;
        return new FoodPayment(food-fromFood,saturation-fromSaturation);
    }
    public static boolean healthAvailable(float health,float cost) {
        return Float.isFinite(cost)&&cost>=0&&(cost==0||health>cost);
    }
    public static float healthAfter(float health,float cost) {
        return health<=0?health:Math.max(1,health-cost);
    }
}
