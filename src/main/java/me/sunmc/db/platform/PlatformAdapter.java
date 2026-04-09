package me.sunmc.db.platform;

/**
 * Thin abstraction so SunDB core doesn't hard-depend on Paper or Velocity.
 */
public interface PlatformAdapter {
    String platformName();
}