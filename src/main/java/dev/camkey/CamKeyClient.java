package dev.camkey;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.List;

@EventBusSubscriber(modid = CamKeyMod.MOD_ID, value = Dist.CLIENT)
public final class CamKeyClient {
    private static final SuggestionProvider<CommandSourceStack> NAMES =
            (ctx, b) -> SharedSuggestionProvider.suggest(SequenceStore.names(), b);

    private CamKeyClient() {}

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent e) {
        var name = Commands.argument("name", StringArgumentType.word()).suggests(NAMES);

        var seconds = Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1, 3600))
                .executes(c -> play(c, true))
                .then(Commands.literal("linear").executes(c -> play(c, false)))
                .then(Commands.literal("smooth").executes(c -> play(c, true)))
                .then(Commands.literal("seconds") // allows: /camkey play intro 10 seconds
                        .executes(c -> play(c, true))
                        .then(Commands.literal("linear").executes(c -> play(c, false)))
                        .then(Commands.literal("smooth").executes(c -> play(c, true))));

        e.getDispatcher().register(Commands.literal("camkey")
                .then(Commands.literal("add").then(
                        Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                                .executes(c -> add(StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("pop").then(
                        Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                                .executes(c -> pop(StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("delete").then(
                        Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                                .executes(c -> delete(StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("list")
                        .executes(c -> list(null))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(NAMES)
                                .executes(c -> list(StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("play").then(name.then(seconds)))
                .then(Commands.literal("stop").executes(c -> {
                    if (Playback.active()) Playback.stop(true);
                    else msg("Nothing is playing.");
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre e) {
        Playback.onFrame();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Playback.tick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        Playback.stop(false);
    }

    // ---- command handlers -------------------------------------------------

    private static int add(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 p = cam.getPosition();
        int n = SequenceStore.add(name, new Keyframe(p.x, p.y, p.z, Mth.wrapDegrees(cam.getYRot()), cam.getXRot()));
        msg(String.format("'%s' keyframe #%d @ %.2f %.2f %.2f  yaw %.1f pitch %.1f",
                name, n, p.x, p.y, p.z, cam.getYRot(), cam.getXRot()));
        return n;
    }

    private static int pop(String name) {
        if (SequenceStore.pop(name)) msg("Removed last keyframe from '" + name + "'.");
        else msg("No such sequence: " + name);
        return 1;
    }

    private static int delete(String name) {
        if (SequenceStore.delete(name)) msg("Deleted '" + name + "'.");
        else msg("No such sequence: " + name);
        return 1;
    }

    private static int list(String name) {
        if (name == null) {
            if (SequenceStore.all().isEmpty()) msg("No sequences saved.");
            SequenceStore.all().forEach((k, v) -> msg(k + ": " + v.size() + " keyframe(s)"));
            return 1;
        }
        List<Keyframe> kfs = SequenceStore.get(name);
        if (kfs == null) {
            msg("No such sequence: " + name);
            return 0;
        }
        for (int i = 0; i < kfs.size(); i++) {
            Keyframe k = kfs.get(i);
            msg(String.format("#%d  %.2f %.2f %.2f  yaw %.1f pitch %.1f", i + 1, k.x(), k.y(), k.z(), k.yaw(), k.pitch()));
        }
        return kfs.size();
    }

    private static int play(CommandContext<CommandSourceStack> c, boolean smooth) {
        String name = StringArgumentType.getString(c, "name");
        double seconds = DoubleArgumentType.getDouble(c, "seconds");
        List<Keyframe> kfs = SequenceStore.get(name);
        if (kfs == null) {
            msg("No such sequence: " + name);
            return 0;
        }
        if (kfs.size() < 2) {
            msg("'" + name + "' needs at least 2 keyframes.");
            return 0;
        }
        Playback.start(name, kfs, seconds, smooth);
        msg(String.format("Playing '%s' over %.1fs (%s). Press Esc to abort.", name, seconds, smooth ? "smooth" : "linear"));
        return 1;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("[camkey] " + s), false);
    }
}
