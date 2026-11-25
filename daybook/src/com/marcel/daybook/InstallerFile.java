package com.marcel.daybook;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public final class InstallerFile extends ContentProvider {
    static final Uri URI=Uri.parse("content://com.marcel.daybook.installer/Daybook.apk");
    static File file(Context c){return new File(c.getCacheDir(),"Daybook-update.apk");}
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri u){return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{
        if(!URI.equals(u)||!"r".equals(mode))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri u,String[] cols,String selection,String[] args,String sort){
        if(!URI.equals(u))throw new IllegalArgumentException();
        if(cols==null)cols=new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];
        for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]="Daybook.apk";if(OpenableColumns.SIZE.equals(cols[i]))row[i]=file(getContext()).length();}
        c.addRow(row);return c;
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
