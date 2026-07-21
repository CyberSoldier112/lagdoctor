package com.craftpilot.lagdoctor;

import com.craftpilot.lagdoctor.command.LagDoctorCommand;
import com.craftpilot.lagdoctor.sampler.TickSampler;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LagDoctorPlugin extends JavaPlugin {

    private TickSampler sampler;
    private Messages messages;
    private ScanService scanService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        messages.load(getConfig().getString("language", "tr"));

        sampler = new TickSampler(
                getConfig().getInt("sampler.history-ticks", 6000),
                getConfig().getDouble("sampler.spike-threshold-ms", 100.0));
        getServer().getPluginManager().registerEvents(sampler, this);

        scanService = new ScanService(this, sampler, messages);

        PluginCommand command = getCommand("lagdoctor");
        if (command != null) {
            LagDoctorCommand executor = new LagDoctorCommand(this, sampler, messages, scanService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("LagDoctor enabled (language: "
                + getConfig().getString("language", "tr") + ")");
    }

    @Override
    public void onDisable() {
        if (scanService != null) {
            scanService.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.load(getConfig().getString("language", "tr"));
        sampler.configure(
                getConfig().getInt("sampler.history-ticks", 6000),
                getConfig().getDouble("sampler.spike-threshold-ms", 100.0));
    }

    public Messages messages() {
        return messages;
    }

    public ScanService scanService() {
        return scanService;
    }
}
