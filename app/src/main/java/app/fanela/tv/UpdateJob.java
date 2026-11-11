package app.fanela.tv;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Background refresh (Engine decides whether the network is actually needed this time). */
public class UpdateJob extends JobService {
    /** The stop flag of each running job (the periodic one and a one-off can overlap): stopping one never stops another. */
    private final Map<Integer, Engine.Stop> running = new ConcurrentHashMap<>();

    @Override public boolean onStartJob(final JobParameters p) {
        if (!HomeRows.enabled(this) || Teams.followed(this).isEmpty()) {  // rows off or not supported any more: stop for good
            Updater.schedule(this);
            return false;
        }
        final Engine.Stop stop = new Engine.Stop();
        running.put(p.getJobId(), stop);
        new Thread(new Runnable() {
            public void run() {
                Engine.background();
                try {
                    // nothing on the home screen to keep current (every row hidden): skip, until a row is shown again
                    if (HomeRows.worthRefreshing(UpdateJob.this)) Engine.refresh(UpdateJob.this, false, stop);
                } catch (Throwable ignored) { }
                running.remove(p.getJobId());
                jobFinished(p, false);
            }
        }, "fanela-job").start();
        return true;
    }

    /** The system wants the job to end: finish the current club, save, and let the job run again later. */
    @Override public boolean onStopJob(JobParameters p) {
        Engine.Stop stop = running.remove(p.getJobId());
        if (stop != null) stop.set = true;
        return true;
    }
}
