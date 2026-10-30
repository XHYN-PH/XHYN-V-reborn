package com.gtavsource.android;
import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import android.graphics.*;
public final class XhynEnglishUi {
 public static android.view.View adapterView(android.view.View view){if(view instanceof android.widget.TextView){android.widget.TextView text=(android.widget.TextView)view;text(text,text.getText());}return view;}
 static CharSequence cs(CharSequence s){if(s==null)return null;String t=XhynEnglish.t(s.toString());return t.equals(s.toString())?s:t;}
 static CharSequence[] array(CharSequence[] a){if(a==null)return null;CharSequence[] b=a.clone();for(int i=0;i<a.length;i++)b[i]=cs(a[i]);return b;}
 public static void text(TextView v,CharSequence s){v.setText(v instanceof EditText?s:cs(s));}
 public static void hint(TextView v,CharSequence s){v.setHint(cs(s));}
 public static void description(View v,CharSequence s){v.setContentDescription(cs(s));}
 public static Toast toast(Context c,CharSequence s,int n){return Toast.makeText(c,cs(s),n);}
 public static AlertDialog.Builder title(AlertDialog.Builder b,CharSequence s){return b.setTitle(cs(s));}
 public static AlertDialog.Builder message(AlertDialog.Builder b,CharSequence s){return b.setMessage(cs(s));}
 public static AlertDialog.Builder positive(AlertDialog.Builder b,CharSequence s,DialogInterface.OnClickListener l){return b.setPositiveButton(cs(s),l);}
 public static AlertDialog.Builder negative(AlertDialog.Builder b,CharSequence s,DialogInterface.OnClickListener l){return b.setNegativeButton(cs(s),l);}
 public static AlertDialog.Builder neutral(AlertDialog.Builder b,CharSequence s,DialogInterface.OnClickListener l){return b.setNeutralButton(cs(s),l);}
 public static AlertDialog.Builder items(AlertDialog.Builder b,CharSequence[] s,DialogInterface.OnClickListener l){return b.setItems(array(s),l);}
 public static AlertDialog.Builder single(AlertDialog.Builder b,CharSequence[] s,int n,DialogInterface.OnClickListener l){return b.setSingleChoiceItems(array(s),n,l);}
 public static AlertDialog.Builder multi(AlertDialog.Builder b,CharSequence[] s,boolean[] n,DialogInterface.OnMultiChoiceClickListener l){return b.setMultiChoiceItems(array(s),n,l);}
 public static void progress(ProgressDialog b,CharSequence s){b.setMessage(cs(s));}
 public static void activityTitle(Activity a,CharSequence s){a.setTitle(cs(s));}
 public static void dialogTitle(AlertDialog a,CharSequence s){a.setTitle(cs(s));}
 public static Notification.Builder notificationTitle(Notification.Builder b,CharSequence s){return b.setContentTitle(cs(s));}
 public static Notification.Builder notificationText(Notification.Builder b,CharSequence s){return b.setContentText(cs(s));}
 public static void draw(Canvas c,String s,float x,float y,Paint p){c.drawText(XhynEnglish.t(s),x,y,p);}
 public static float measure(Paint p,String s){return p.measureText(XhynEnglish.t(s));}
 private XhynEnglishUi(){}
}
