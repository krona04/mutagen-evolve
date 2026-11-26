package net.krona.mutagen.client.body;

/** Tick-based animation state. It never advances merely because another render pass was requested. */
public final class CreatureKinematics {
    private CreatureKinematics() {}

    public static boolean nonHuman(String strain) {
        return switch (strain) {
            case "wolf", "spider", "cave_spider", "creeper", "guardian", "bee", "phantom", "blaze" -> true;
            default -> false;
        };
    }

    public static float turn(float from, float to, float limit) {
        float difference = (to - from) % 360.0F;
        if (difference >= 180.0F) difference -= 360.0F;
        if (difference < -180.0F) difference += 360.0F;
        return from + Math.max(-limit, Math.min(limit, difference));
    }

    public static final class State {
        public int tick = Integer.MIN_VALUE;
        public float bodyYaw, bodyYawOld, pitch, pitchOld;
        public float walkPosition, walkSpeed, walkSpeedOld;
        public float tail, tailOld, spikes = 1, spikesOld = 1;
        public float airborne, airborneOld;
        private float tailSpeed = 0.125F;
        private String form;

        public State(float yaw) { bodyYaw = bodyYawOld = yaw; }

        public void advance(String strain, int now, double dx, double dy, double dz,
                            boolean water, boolean ground, float lookYaw, float lookPitch) {
            if (now == tick && strain.equals(form)) return;
            boolean reset = !strain.equals(form) || tick == Integer.MIN_VALUE || now < tick || now - (long)tick > 5
                    || dx * dx + dy * dy + dz * dz > 16;
            form = strain;
            tick = now;
            bodyYawOld = bodyYaw;
            pitchOld = pitch;
            walkSpeedOld = walkSpeed;
            tailOld = tail;
            spikesOld = spikes;
            airborneOld = airborne;
            float flight = ground || water ? 0 : 1;
            if (reset) airborne = airborneOld = flight;
            else airborne += (flight - airborne) * .35F;
            if (airborne < .001F) airborne = 0;
            if (airborne > .999F) airborne = 1;
            if (reset) dx = dy = dz = 0;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean directional = nonHuman(strain) && !strain.equals("creeper") && !strain.equals("blaze");
            float desired = directional && horizontal > 0.005
                    ? (float)Math.toDegrees(Math.atan2(-dx, dz)) : lookYaw;
            bodyYaw = turn(bodyYaw, desired, strain.equals("wolf") ? 10 : 7);
            if (!directional) bodyYaw = lookYaw;

            boolean flyingBody = strain.equals("phantom") || strain.equals("bee");
            float targetPitch = (strain.equals("guardian") && water || flyingBody && !ground && !water)
                    && speed > 0.01
                    ? (float)Math.max(-45, Math.min(45, -Math.toDegrees(Math.atan2(dy, horizontal)))) : 0;
            // PhantomRenderer rotates the whole creature before the model's inverted Y axis.
            // Its pitch convention is opposite to GuardianModel's local head rotation.
            if (strain.equals("phantom")) targetPitch = -targetPitch;
            if (!nonHuman(strain)) targetPitch = lookPitch;
            pitch += (targetPitch - pitch) * 0.25F;

            float targetWalk = ground && !water ? (float)Math.min(1, horizontal * 4) : 0;
            walkSpeed += (targetWalk - walkSpeed) * 0.4F;
            float stride = switch (strain) {
                case "wolf" -> 1.3F;
                case "spider", "cave_spider" -> 1.15F;
                case "creeper" -> 1.2F;
                default -> 1;
            };
            if (strain.equals("guardian") || strain.equals("phantom") || strain.equals("blaze")) walkSpeed = 0;
            walkPosition += walkSpeed * stride;

            // Vanilla guardian rhythm: slow tail at rest, faster swimming, rapid movement on land.
            float desiredTail = water ? (speed > 0.01 ? 0.5F : 0.125F) : 2.0F;
            tailSpeed += (desiredTail - tailSpeed) * 0.2F;
            tail += tailSpeed;
            float desiredSpikes = 1;
            spikes += (desiredSpikes - spikes) * (desiredSpikes == 0 ? 0.25F : 0.06F);
        }
    }
}
