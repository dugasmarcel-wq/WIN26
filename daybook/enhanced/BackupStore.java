package com.marcel.daybook;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Rotating private backups, with optional user-authorized external daily copies. */
final class BackupStore {
    private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> queued;
    private static final Set<String> MUTATIONS=new HashSet<>(Arrays.asList("methodLog","methodStart","methodPause","methodResume","methodStop","targetEdit","removeGoal","goalEdit","reflect","color","place","rename","removeNote","event","removeEvent","goal","mark","pin","folder","save","alarm"));
    static boolean isMutation(String op){return MUTATIONS.contains(op);}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("automatic-backups",Context.MODE_PRIVATE);}
    static File directory(Context c)throws IOException{
        File dir=new File(c.getFilesDir(),"daybook-backups");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create backup directory.");
        return dir;
    }
    static synchronized void request(Context c){
        Context app=c.getApplicationContext();
        if(queued!=null)queued.cancel(false);
        queued=worker.schedule(()->run(app,false),4,TimeUnit.SECONDS);
    }
    static void now(Context c){
        Context app=c.getApplicationContext();worker.execute(()->run(app,true));
    }
    static void run(Context c,boolean forceExternal){
        try{write(c,forceExternal);}catch(Exception e){prefs(c).edit().putString("error",String.valueOf(e.getMessage())).apply();}
    }
    static void folder(Context c,Uri uri){
        prefs(c).edit().putString("tree",uri.toString()).remove("externalDay").remove("error").apply();
    }
    static void schedule(Context c){
        JobScheduler s=c.getSystemService(JobScheduler.class);
        if(s.getPendingJob(6074)==null)s.schedule(new JobInfo.Builder(6074,new ComponentName(c,BackupJob.class)).setPersisted(true).setPeriodic(24L*60*60*1000).build());
    }
    static synchronized void write(Context c,boolean forceExternal)throws Exception{
        String today=LocalDate.now().toString();
        byte[] bytes=Journal.get(c).backup().toString().getBytes(StandardCharsets.UTF_8);
        File dir=directory(c);writeAtomic(new File(dir,"Daybook-"+today+".json"),bytes);
        File[] files=dir.listFiles((d,n)->n.matches("Daybook-\\d{4}-\\d{2}-\\d{2}\\.json"));
        if(files!=null){Arrays.sort(files,Comparator.comparing(File::getName));for(int i=0;i<files.length-14;i++)files[i].delete();}
        SharedPreferences p=prefs(c);p.edit().putLong("lastLocal",System.currentTimeMillis()).apply();
        String tree=p.getString("tree","");
        if(!tree.isEmpty()&&(forceExternal||!today.equals(p.getString("externalDay","")))){
            Uri base=Uri.parse(tree),parent=DocumentsContract.buildDocumentUriUsingTree(base,DocumentsContract.getTreeDocumentId(base));
            String name="Daybook-Enhanced-"+today+"-"+System.currentTimeMillis()+".json";
            Uri file=DocumentsContract.createDocument(c.getContentResolver(),parent,"application/json",name);
            if(file==null)throw new IOException("Cannot create external backup.");
            try{
                try(OutputStream out=c.getContentResolver().openOutputStream(file,"w")){if(out==null)throw new IOException("Cannot open external backup.");out.write(bytes);out.flush();}
                p.edit().putString("externalDay",today).putLong("lastExternal",System.currentTimeMillis()).apply();
            }catch(Exception e){try{DocumentsContract.deleteDocument(c.getContentResolver(),file);}catch(Exception ignored){}throw e;}
        }
        p.edit().remove("error").apply();
    }
    private static void writeAtomic(File dest,byte[] bytes)throws IOException{
        AtomicFile f=new AtomicFile(dest);FileOutputStream out=null;
        try{out=f.startWrite();out.write(bytes);f.finishWrite(out);}
        catch(IOException e){if(out!=null)f.failWrite(out);throw e;}
    }
    static synchronized void beforeRestore(Context c)throws Exception{
        byte[] bytes=Journal.get(c).backup().toString().getBytes(StandardCharsets.UTF_8);
        writeAtomic(new File(directory(c),"Before-restore.json"),bytes);
    }
    static synchronized JSONObject latest(Context c)throws Exception{
        File[] files=directory(c).listFiles((d,n)->n.matches("Daybook-\\d{4}-\\d{2}-\\d{2}\\.json"));
        if(files==null||files.length==0)throw new IOException("No automatic backup exists yet.");
        Arrays.sort(files,Comparator.comparing(File::getName));
        AtomicFile f=new AtomicFile(files[files.length-1]);return new JSONObject(new String(f.readFully(),StandardCharsets.UTF_8));
    }
    static JSONObject status(Context c)throws Exception{
        SharedPreferences p=prefs(c);return new JSONObject().put("lastLocal",p.getLong("lastLocal",0)).put("lastExternal",p.getLong("lastExternal",0)).put("external",!p.getString("tree","").isEmpty()).put("error",p.getString("error",""));
    }
}
