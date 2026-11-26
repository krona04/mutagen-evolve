import net.krona.mutagen.mutation.AquaticAdaptation;

public final class AquaticAdaptationCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int air = 300, damage = 0;
        for (int tick = 1; tick <= 400; tick++) {
            var result = AquaticAdaptation.breathe(air, 300, false);
            air = result.air();
            if (result.damage()) damage++;
            if (tick <= 300) check(!result.damage(), "Normal air reserve lasts fifteen seconds");
            if (tick == 300) check(air == 0, "Land bubble bar runs out after 300 ticks");
            if (tick == 320) check(result.damage(), "Suffocation follows ordinary twenty-tick grace");
            check(air >= -19 && air <= 300, "Air stays in native HUD range");
        }
        check(damage == 5, "Damage repeats once per second after air runs out");
        var water = AquaticAdaptation.breathe(air, 300, true);
        check(water.air() == 300 && !water.damage(), "Entering water immediately restores gill breathing");
        check(AquaticAdaptation.breathe(-19, 300, true).air() == 300, "Water rescues a suffocating form");
        check(AquaticAdaptation.breathe(600, 600, false).air() == 599, "Custom maximum air supply remains supported");
        check(AquaticAdaptation.breathe(900, 300, false).air() == 299, "Air cannot exceed current maximum");
        float acceleration = AquaticAdaptation.swimAcceleration(100, .8F);
        double speed = 0;
        for (int tick = 0; tick < 100; tick++) speed = (speed + acceleration) * .85;
        double blocksPerSecond = speed * 20;
        check(blocksPerSecond > 5 && blocksPerSecond < 8, "Full guardian cruises faster than surface walking");
        check(AquaticAdaptation.swimAcceleration(100, .4F) < acceleration, "Slowness still reduces swimming thrust");
        check(AquaticAdaptation.swimAcceleration(100, 1.04F) > acceleration, "Sprinting still improves swimming thrust");
        check(AquaticAdaptation.swimAcceleration(41, 1) < AquaticAdaptation.swimAcceleration(100, 1),
                "Aquatic movement develops with the body");
        for (int tick = 0; tick < 100; tick++) speed *= .85;
        check(speed < .00001, "Idle water movement settles instead of drifting forever");
        System.out.println("Aquatic adaptation checks passed: " + checks);
    }
}
