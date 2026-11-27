package com.marcel.daybook;
import android.app.job.JobParameters;
import android.app.job.JobService;
public final class BackupJob extends JobService {
    @Override public boolean onStartJob(JobParameters params){
        new Thread(()->{BackupStore.run(getApplicationContext(),false);jobFinished(params,false);}).start();return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
}
