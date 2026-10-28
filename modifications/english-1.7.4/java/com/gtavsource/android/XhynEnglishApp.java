package com.gtavsource.android;
import android.app.Application;import java.io.*;
public final class XhynEnglishApp extends Application {
 @Override public void onCreate(){super.onCreate();try(InputStream in=getAssets().open("xhyn-english.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);XhynEnglish.load(new String(out.toByteArray(),"UTF-8"));}catch(Exception e){throw new IllegalStateException("Unable to load the XHYN V English dictionary",e);}}
}
