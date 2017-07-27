package xyz.upperlevel.uppercore.task;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.scheduler.BukkitRunnable;
import xyz.upperlevel.uppercore.Uppercore;

@Getter
public class UpdaterTask extends BukkitRunnable {

    private final Runnable task;

    @Setter
    private int interval;
    private boolean started;

    public UpdaterTask(Runnable task) {
        this.task = task;
    }

    public UpdaterTask(int interval, Runnable task) {
        this.interval = interval;
        this.task = task;
    }

    public void start() {
        start(true);
    }

    public void start(boolean now) {
        runTaskTimer(Uppercore.get(), now ? 0 : interval, interval);
        started = true;
    }

    public void stop() {
        cancel();
        started = false;
    }

    @Override
    public void run() {
        task.run();
    }
}