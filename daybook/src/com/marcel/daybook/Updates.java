package com.marcel.daybook;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.net.ssl.HttpsURLConnection;

final class Updates {
    static final String BASE="https://github.com/dugasmarcel-wq/WIN26/releases/download/win26-update-100141/";
    private final Main a;
    private boolean busy,awaitPermission;
    Updates(Main a){this.a=a;}
    void onResume(){
        if(awaitPermission){awaitPermission=false;if(a.getPackageManager().canRequestPackageInstalls())install();else a.tell("Update saved. Allow app installs to continue.");return;}
        if(System.currentTimeMillis()-a.getPreferences(0).getLong("lastCheck",0)>86400000L) check(false);
    }
    static void fetch(String url,OutputStream out,int limit)throws Exception{
        HttpsURLConnection conn=(HttpsURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(25000);conn.setRequestProperty("User-Agent","Daybook-Android");
        try(InputStream in=conn.getInputStream()){
            byte[] buf=new byte[16384];int n,total=0;
            while((n=in.read(buf))!=-1){total+=n;if(total>limit)throw new IOException("Download exceeds size limit.");out.write(buf,0,n);}
        }finally{conn.disconnect();}
    }
    void check(boolean manual){
        if(busy){if(manual)a.tell("Checking for updates.");return;}busy=true;
        new Thread(()->{
            try{
                ByteArrayOutputStream out=new ByteArrayOutputStream();fetch(BASE+"daybook-update.json",out,16384);
                JSONObject m=new JSONObject(out.toString("UTF-8"));long version=m.getLong("versionCode");
                long installed=a.getPackageManager().getPackageInfo(a.getPackageName(),0).getLongVersionCode();
                a.getPreferences(0).edit().putLong("lastCheck",System.currentTimeMillis()).apply();
                a.runOnUiThread(()->{busy=false;if(a.isFinishing()||a.isDestroyed())return;
                    if(version<=installed){if(manual)a.tell("Daybook is up to date.");return;}
                    new AlertDialog.Builder(a).setTitle("Update Daybook to "+m.optString("versionName")+"?")
                    .setMessage("Your journal stays on this phone.").setNegativeButton("Later",null)
                    .setPositiveButton("Download update",(d,w)->download(m,version)).show();});
            }catch(Exception e){a.runOnUiThread(()->{busy=false;if(manual)a.tell("Could not check for updates. Try again when online.");});}
        }).start();
    }
    void download(JSONObject m,long version){
        busy=true;a.tell("Downloading Daybook update.");
        new Thread(()->{
            File tmp=new File(a.getCacheDir(),"daybook-download.apk");
            try{
                String url=m.getString("apkUrl");
                if(!url.equals(BASE+"Daybook-"+version+".apk"))throw new IOException("Unexpected update address.");
                MessageDigest sha=MessageDigest.getInstance("SHA-256");
                try(OutputStream out=new java.security.DigestOutputStream(new FileOutputStream(tmp),sha)){fetch(url,out,64*1024*1024);}
                StringBuilder hex=new StringBuilder();for(byte b:sha.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(!hex.toString().equalsIgnoreCase(m.getString("sha256")))throw new IOException("Download checksum mismatch.");
                PackageManager pm=a.getPackageManager();
                PackageInfo apk=pm.getPackageArchiveInfo(tmp.getPath(),PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo own=pm.getPackageInfo(a.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
                if(apk==null||!a.getPackageName().equals(apk.packageName)||apk.getLongVersionCode()!=version||
                  version<=own.getLongVersionCode()||!Arrays.equals(own.signingInfo.getApkContentsSigners(),apk.signingInfo.getApkContentsSigners()))
                    throw new IOException("The package or signing certificate does not match Daybook.");
                java.nio.file.Files.move(tmp.toPath(),InstallerFile.file(a).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                a.runOnUiThread(()->{busy=false;if(a.isDestroyed()||a.isFinishing())return;
                    if(pm.canRequestPackageInstalls())install();else new AlertDialog.Builder(a).setTitle("Allow Daybook updates")
                        .setMessage("Enable Allow from this source, then return to Daybook to install the update.").setNegativeButton("Later",null)
                        .setPositiveButton("Open settings",(d,w)->{awaitPermission=true;a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}).show();});
            }catch(Exception e){tmp.delete();a.runOnUiThread(()->{busy=false;a.tell("Update failed: "+e.getMessage());});}
        }).start();
    }
    private void install(){try{a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(InstallerFile.URI,"application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(ActivityNotFoundException e){a.tell("Android's package installer is unavailable.");}}
}
