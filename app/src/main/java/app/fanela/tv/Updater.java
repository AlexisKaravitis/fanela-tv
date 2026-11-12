package app.fanela.tv;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Background schedule. Only needed for the home-screen rows: a 15-minute job keeps them current (the engine decides
 * per club whether anything is due, so most runs do nothing). Without rows the app updates when it is opened, and
 * nothing runs in the background at all.
 */
final class Updater {
    static final int JOB_PERIODIC = 1, JOB_NOW = 2;
    static final int JOB_VERSION = 1;  // bump when the job definition changes so updates replace the old one

    static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        SharedPreferences sp = c.getSharedPreferences("engine", Context.MODE_PRIVATE);
        if (!HomeRows.enabled(c) || Sports.nothingFollowed(c)) {
            js.cancel(JOB_PERIODIC);
            sp.edit().remove("jobVersion").apply();
            return;
        }
        ComponentName cn = new ComponentName(c, UpdateJob.class);
        if (js.getPendingJob(JOB_PERIODIC) == null || sp.getInt("jobVersion", 0) != JOB_VERSION) {
            js.cancel(JOB_PERIODIC);
            // No network requirement on purpose: offline runs redraw from the cache (stale warning, day labels).
            js.schedule(new JobInfo.Builder(JOB_PERIODIC, cn).setPeriodic(15 * 60000L).setPersisted(true).build());
            sp.edit().putInt("jobVersion", JOB_VERSION).apply();
        }
    }

    /** One refresh soon, in the system's job queue (after install, boot, or a settings change outside the app). */
    static void soon(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || Sports.nothingFollowed(c)) return;
        js.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(c, UpdateJob.class)).setOverrideDeadline(3000).build());
    }
}
