package dev.camkey;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Client-side playback. A detached, never-spawned Marker entity becomes the render camera,
 * so the real player (and server state) is untouched. Pose is recomputed every render frame
 * from wall-clock time, so motion is smooth regardless of tick rate.
 */
public final class Playback {
    private static final long ABORT_GRACE_NS = 250_000_000L;

    private static List<Keyframe> frames = List.of();
    private static String seqName = "";
    private static long startNs;
    private static long durationNs;
    private static boolean smooth;

    private static CamEntity cam;
    private static Entity prevCamera;
    private static CameraType prevType;
    private static boolean prevHideGui;

    private Playback() {}

    public static boolean active() {
        return cam != null;
    }

    public static void start(String name, List<Keyframe> kfs, double seconds, boolean smoothEase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        stop(false);

        frames = List.copyOf(kfs);
        seqName = name;
        smooth = smoothEase;
        durationNs = (long) (seconds * 1_000_000_000L);
        startNs = System.nanoTime();

        cam = new CamEntity(mc.level);
        prevCamera = mc.getCameraEntity();
        prevType = mc.options.getCameraType();
        prevHideGui = mc.options.hideGui;

        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = true;
        apply(0.0);
        mc.setCameraEntity(cam);
    }

    public static void stop(boolean announce) {
        if (cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        Entity restore = prevCamera != null ? prevCamera : mc.player;
        if (restore != null) mc.setCameraEntity(restore);
        mc.options.setCameraType(prevType);
        mc.options.hideGui = prevHideGui;
        cam = null;
        prevCamera = null;
        if (announce && mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[camkey] stopped '" + seqName + "'"), false);
        }
    }

    /** Called once per render frame. */
    public static void onFrame() {
        if (cam == null) return;
        double t = Mth.clamp((double) (System.nanoTime() - startNs) / durationNs, 0.0, 1.0);
        apply(t);
        if (t >= 1.0) {
            Minecraft mc = Minecraft.getInstance();
            String done = seqName;
            stop(false);
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[camkey] finished '" + done + "'"), false);
            }
        }
    }

    /** Called once per client tick: abort on Esc/chat/any screen, or on world change. */
    public static void tick() {
        if (cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || cam.level() != mc.level) {
            stop(false);
        } else if (mc.screen != null && System.nanoTime() - startNs > ABORT_GRACE_NS) {
            stop(true);
        }
    }

    private static void apply(double t) {
        int segs = frames.size() - 1;
        double p = t * segs;
        int i = Math.min((int) Math.floor(p), segs - 1);
        double f = p - i;
        if (smooth) f = f * f * (3.0 - 2.0 * f); // smoothstep each keyframe transition
        Keyframe a = frames.get(i);
        Keyframe b = frames.get(i + 1);

        double x = Mth.lerp(f, a.x(), b.x());
        double y = Mth.lerp(f, a.y(), b.y());
        double z = Mth.lerp(f, a.z(), b.z());
        float yaw = a.yaw() + (float) (Mth.wrapDegrees(b.yaw() - a.yaw()) * f); // shortest arc
        float pitch = (float) Mth.lerp(f, a.pitch(), b.pitch());

        cam.setPos(x, y - CamEntity.EYE, z);
        cam.xo = x;
        cam.yo = y - CamEntity.EYE;
        cam.zo = z;
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.yRotO = yaw;
        cam.xRotO = pitch;
    }

    /** Invisible camera carrier; constant eye height so the camera sits exactly at the keyframe. */
    private static final class CamEntity extends Marker {
        static final float EYE = 1.62F;
        private static final EntityDimensions DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F).withEyeHeight(EYE);

        CamEntity(Level level) {
            super(EntityType.MARKER, level);
            refreshDimensions(); // Entity constructor cached the 0-height MARKER type dimensions
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return DIMENSIONS;
        }
    }
}
