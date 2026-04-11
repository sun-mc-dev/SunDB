package me.sunmc.db.platform;

import me.sunmc.db.config.MavenLibrary;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PaperAdapter implements PlatformAdapter {

    private final JavaPlugin plugin;

    public PaperAdapter(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    @Contract(pure = true)
    @Override
    public @NotNull String platformName() {
        return "Paper/Folia";
    }

    /**
     * Paper 1.21+ enforces strict Java module encapsulation, preventing reflective
     * URLClassLoader injection. Instead of crashing, we now defer entirely to Bukkit's
     * native 'libraries:' block defined in plugin.yml.
     */
    @Override
    public void loadLibraries(@NotNull List<MavenLibrary> libraries) {
        // Ignored. All drivers are already native on the classpath via plugin.yml
    }
}
