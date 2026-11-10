package app.fanela.tv;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Background refresh (Engine decides whether the network is actually needed this time). */
public class UpdateJob extends JobService {
    @Override public boolean onStartJob(final JobParameters p) {
        if (!HomeRows.enabled(this) || Teams.followed(this).isEmpty()) {  // rows off or not supported any more: stop for good
            Updater.schedule(this);
            return false;
        }
        new Thread(new Runnable() {
            public void run() {
                Engine.background();
                try { Engine.refresh(UpdateJob.this, false); } catch (Throwable ignored) { }
                jobFinished(p, false);
            }
        }, "fanela-job").start();
        return true;
    }

    /** The system wants the job to end: finish the current club, save, and let the job run again later. */
    @Override public boolean onStopJob(JobParameters p) {
        Engine.stop = true;
        return true;
    }
}
