package dev.halloween;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.Vec3d;

/** Replays the real server packets in the isolated naval screenshot world. */
final class BlastVisualChecks {
    private static int age;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("blast-visual");

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!NavalVisualChecks.ready || client.world == null || client.player == null) return;
            client.options.pauseOnLostFocus = false;
            client.options.hudHidden = true;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            var center = NavalVisualChecks.ORIGIN.add(0, 5, 1);
            var delta = center.subtract(client.player.getEyePos());
            client.player.setYaw((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            client.player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalLength())));
            switch (++age) {
                case 25 -> capture(client, "baseline");
                case 34 -> send(client, BlastEffects.Kind.HULL);
                case 42 -> capture(client, "hull-fire");
                case 64 -> capture(client, "hull-smoke");
                case 145 -> send(client, BlastEffects.Kind.IMPACT);
                case 152 -> capture(client, "impact");
                case 250 -> send(client, BlastEffects.Kind.FIREARM);
                case 254 -> capture(client, "firearm");
                case 290 -> send(client, BlastEffects.Kind.ROBBERY);
                case 294 -> capture(client, "robbery");
                case 345 -> send(client, BlastEffects.Kind.HULL, 52);
                case 352 -> capture(client, "distant-fire");
                case 375 -> capture(client, "distant-smoke");
                case 390 -> client.scheduleStop();
            }
        });
    }

    private static void send(MinecraftClient client, BlastEffects.Kind kind) {
        boolean small = kind == BlastEffects.Kind.FIREARM || kind == BlastEffects.Kind.ROBBERY;
        send(client, kind, small ? 5 : 16);
    }

    private static void send(MinecraftClient client, BlastEffects.Kind kind, double distance) {
        var server = client.getServer();
        if (server == null) return;
        server.execute(() -> {
            var world = server.getOverworld();
            var player = world.getPlayers().getFirst();
            if (!ServerPlayNetworking.canSend(player, BlastEffects.Burst.ID)) {
                LOG.error("FAIL client did not advertise blast packets");
                client.scheduleStop();
                return;
            }
            var center = NavalVisualChecks.ORIGIN.add(0, 5, 1);
            var toCamera = player.getEyePos().subtract(center).normalize();
            var origin = player.getEyePos().subtract(toCamera.multiply(distance)).add(0, -.5, 0);
            if (distance > 32) {
                var sideways = new Vec3d(toCamera.z, 0, -toCamera.x).normalize();
                origin = origin.add(sideways.multiply(15)).add(0, 4, 0);
            }
            LOG.info("Sending {} at {} from observer {}", kind, origin, player.getEyePos());
            if (kind == BlastEffects.Kind.FIREARM) BlastEffects.firearm(world, origin, new Vec3d(1, 0, 0));
            else BlastEffects.burst(world, origin, kind);
        });
    }

    private static void capture(MinecraftClient client, String view) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "blast-" + view + ".png", client.getFramebuffer(), 1,
                message -> LOG.info("Captured {}: {}", view, message.getString()));
    }

    private BlastVisualChecks() {}
}
