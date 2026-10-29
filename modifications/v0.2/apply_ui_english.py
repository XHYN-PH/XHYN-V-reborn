from pathlib import Path
import re,json,shutil,collections,xml.etree.ElementTree as ET,hashlib,os
R=Path(os.environ.get('XHYN_WORK_DIR',Path(__file__).resolve().parent));D=R/'decoded';assert not (D/'assets/xhyn-english.json').exists(),'Start from a fresh decode';m=json.loads((R/'translations.json').read_text());m.update({'GTA V':'XHYN V','GTA V · Android':'XHYN V · Android','GTAV Master（修复共存版）':'XHYN V'})
(R/'translations.json').write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n');(D/'assets/xhyn-english.json').write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n')
B='Landroid/app/AlertDialog$Builder;';CS='Ljava/lang/CharSequence;';L='Landroid/content/DialogInterface$OnClickListener;';H='Lcom/gtavsource/android/XhynEnglishUi;'
hooks={}
def add(old,name,sig):hooks[old]=H+'->'+name+sig
ui=json.loads((R/'reports/ui-calls.json').read_text());canvas=json.loads((R/'reports/canvas-calls.json').read_text())
for old in list(ui)+list(canvas):
 owner,method=old.split('->');owner+=''
 if method=='setText('+CS+')V':add(old,'text','(Landroid/widget/TextView;'+CS+')V')
 elif method=='setHint('+CS+')V':add(old,'hint','(Landroid/widget/TextView;'+CS+')V')
 elif method=='setContentDescription('+CS+')V':add(old,'description','(Landroid/view/View;'+CS+')V')
 elif method=='makeText(Landroid/content/Context;'+CS+'I)Landroid/widget/Toast;':add(old,'toast','(Landroid/content/Context;'+CS+'I)Landroid/widget/Toast;')
 elif method=='setTitle('+CS+')'+B:add(old,'title','('+B+CS+')'+B)
 elif method=='setMessage('+CS+')'+B:add(old,'message','('+B+CS+')'+B)
 elif method in ['set'+x+'Button('+CS+L+')'+B for x in ['Positive','Negative','Neutral']]:
  name=method[3:method.index('Button')].lower();add(old,name,'('+B+CS+L+')'+B)
 elif method=='setItems(['+CS+L+')'+B:add(old,'items','('+B+'['+CS+L+')'+B)
 elif method=='setSingleChoiceItems(['+CS+'I'+L+')'+B:add(old,'single','('+B+'['+CS+'I'+L+')'+B)
 elif method=='setMultiChoiceItems(['+CS+'[ZLandroid/content/DialogInterface$OnMultiChoiceClickListener;)'+B:add(old,'multi','('+B+'['+CS+'[ZLandroid/content/DialogInterface$OnMultiChoiceClickListener;)'+B)
 elif old=='Landroid/app/ProgressDialog;->setMessage('+CS+')V':add(old,'progress','(Landroid/app/ProgressDialog;'+CS+')V')
 elif old=='Landroid/app/Activity;->setTitle('+CS+')V':add(old,'activityTitle','(Landroid/app/Activity;'+CS+')V')
 elif old=='Landroid/app/AlertDialog;->setTitle('+CS+')V':add(old,'dialogTitle','(Landroid/app/AlertDialog;'+CS+')V')
 elif method=='setContentTitle('+CS+')Landroid/app/Notification$Builder;':add(old,'notificationTitle','(Landroid/app/Notification$Builder;'+CS+')Landroid/app/Notification$Builder;')
 elif method=='setContentText('+CS+')Landroid/app/Notification$Builder;':add(old,'notificationText','(Landroid/app/Notification$Builder;'+CS+')Landroid/app/Notification$Builder;')
 elif old=='Landroid/graphics/Canvas;->drawText(Ljava/lang/String;FFLandroid/graphics/Paint;)V':add(old,'draw','(Landroid/graphics/Canvas;Ljava/lang/String;FFLandroid/graphics/Paint;)V')
 elif old in ['Landroid/graphics/Paint;->measureText(Ljava/lang/String;)F','Landroid/text/TextPaint;->measureText(Ljava/lang/String;)F']:add(old,'measure','(Landroid/graphics/Paint;Ljava/lang/String;)F')
counts=collections.Counter();original=[]
for base in D.glob('smali*'):
 for p in base.rglob('*.smali'):
  s=p.read_text();original.append(p)
  if not any(x in str(p) for x in ['com/gtavsource/android','org/libsdl','com/deepseek/quarkdownload']):continue
  def rewrite(x):
   op,regs,target=x.groups()
   if target not in hooks:return x.group()
   counts[target]+=1
   return 'invoke-static'+('/range' if op.endswith('/range') else '')+' {'+regs+'}, '+hooks[target]
  s=re.sub(r'invoke-(\S+) \{([^}]+)\}, (\S+)',rewrite,s)
  if 'Landroid/widget/ArrayAdapter;-><init>' in s:
   s=s.replace(', Landroid/widget/ArrayAdapter;\n',', Lcom/gtavsource/android/XhynEnglishAdapter;\n').replace('Landroid/widget/ArrayAdapter;-><init>','Lcom/gtavsource/android/XhynEnglishAdapter;-><init>');counts['English array adapters']+=1
  if p.name=='TrainerBridge$CatalogItem.smali':
   marker='invoke-direct {p0}, Ljava/lang/Object;-><init>()V';assert s.count(marker)==1
   s=s.replace(marker,marker+'\n\n    invoke-static/range {p1 .. p1}, Lcom/gtavsource/android/XhynEnglish;->catalog(Lorg/json/JSONObject;)V',1);counts['trainer catalog boundary']+=1
  s=s.replace('"zh-CN,zh;q=0.9,en;q=0.5"','"en-US,en;q=0.9"').replace('"zh-CN,zh;q=0.9"','"en-US,en;q=0.9"').replace('Ljava/util/Locale;->CHINA:','Ljava/util/Locale;->US:')
  s=s.replace('"com.gtavsource.android.lsfgv2026"','"com.xhynph.xhynv.reborn174"')
  if s!=p.read_text():p.write_text(s)
# Translate only asset string values; never dictionary keys, IDs or configuration names.
for p in (D/'assets/touch-layouts').rglob('*.json'):
 obj=json.loads(p.read_text())
 def walk(v):
  if isinstance(v,str):return m.get(v,v)
  if isinstance(v,list):return [walk(x) for x in v]
  if isinstance(v,dict):return {k:walk(x) for k,x in v.items()}
  return v
 translated=walk(obj)
 if translated!=obj:p.write_text(json.dumps(translated,ensure_ascii=False,indent=2)+'\n');counts['translated layout assets']+=1
# Reuse the repository logo without altering the artwork.
logo=Path(os.environ.get('XHYN_REPO_ROOT',Path(__file__).resolve().parents[2]))/'assets/xhyn-v.png';icons=[]
for p in (D/'res').rglob('*.png'):
 if p.name in ['ic_launcher.png','ic_launcher_foreground.png','ui_logo.png']:
  shutil.copyfile(logo,p);icons.append(str(p.relative_to(D)))
assert len(icons)==11,len(icons)
# Adaptive icons inset the complete repo mark into the safe central area.
for p in (D/'res').rglob('ic_launcher.xml'):
 if '<adaptive-icon' in p.read_text():p.write_text('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@android:color/transparent"/><foreground><inset android:inset="16%" android:drawable="@mipmap/ic_launcher_foreground"/></foreground></adaptive-icon>\n')
a='{http://schemas.android.com/apk/res/android}';ET.register_namespace('android',a[1:-1]);tree=ET.parse(D/'AndroidManifest.xml');root=tree.getroot();root.set('package','com.xhynph.xhynv.reborn174');app=root.find('application');assert a+'name' not in app.attrib;app.set(a+'name','com.gtavsource.android.XhynEnglishApp');app.set(a+'label','XHYN V');tree.write(D/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
for activity in app.findall('activity'):
 if a+'label' in activity.attrib:activity.set(a+'label',m.get(activity.get(a+'label'),activity.get(a+'label')))
tree.write(D/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
(D/'apktool.yml').write_text((D/'apktool.yml').read_text().replace('versionName: 1.7.26','versionName: "0.2"').replace('versionCode: 109','versionCode: 200'))
report={'hooks':dict(counts),'total_presentation_hooks':sum(n for k,n in counts.items() if k in hooks),'dictionary_entries':len(m),'logo_resources':icons,'logo_sha256':hashlib.sha256(logo.read_bytes()).hexdigest(),'package':'com.xhynph.xhynv.reborn174','app_label':'XHYN V','source_methods_and_protocols_preserved':True}
(R/'reports/ui-patch.json').write_text(json.dumps(report,indent=2)+'\n');(R/'reports/hook-targets.json').write_text(json.dumps(hooks,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='hooks'},indent=2))
