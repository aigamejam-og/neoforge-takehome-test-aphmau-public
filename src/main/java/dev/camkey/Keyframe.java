package dev.camkey;

/** One camera pose. Yaw/pitch in degrees (Minecraft convention). */
public record Keyframe(double x, double y, double z, float yaw, float pitch) {}
