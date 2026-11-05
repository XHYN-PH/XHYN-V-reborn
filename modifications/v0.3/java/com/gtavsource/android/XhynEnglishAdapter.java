package com.gtavsource.android;
import android.content.Context;import android.view.*;import android.widget.*;import java.util.*;
public final class XhynEnglishAdapter extends ArrayAdapter<Object> {
 @SuppressWarnings("unchecked") public XhynEnglishAdapter(Context c,int r,List<?> a){super(c,r,(List<Object>)a);}
 public XhynEnglishAdapter(Context c,int r,Object[] a){super(c,r,a);}
 private View english(View v){if(v instanceof TextView){TextView t=(TextView)v;XhynEnglishUi.text(t,t.getText());}return v;}
 @Override public View getView(int p,View v,ViewGroup g){return english(super.getView(p,v,g));}
 @Override public View getDropDownView(int p,View v,ViewGroup g){return english(super.getDropDownView(p,v,g));}
}
