"""作者 long：从同一请求端源码构建普通UID客户端，排除instrumentation身份；不自动安装或发送请求。"""
from pathlib import Path
import subprocess,os,zipfile,hashlib,json,argparse
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description='仅构建独立普通UID网络请求端，不安装/启停任何应用')
p.add_argument('--output',type=Path,required=True);args=p.parse_args()
B=args.output.resolve();B.mkdir(parents=True,exist_ok=False)
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk'))); TOOLS=SDK/'build-tools/36.0.0'; JAR=SDK/'platforms/android-36/android.jar'
JDK=Path(subprocess.check_output(['/usr/libexec/java_home','-v','17'],text=True).strip())
source=(ROOT/'android/app/src/androidTest/java/com/lonnnnnng/flclash/NetworkSwitchProbeActivity.java').read_text().replace('com.lonnnnnng.flclash.motion.test','com.lonnnnnng.flclash.networkprobe')
src=B/'NetworkSwitchProbeActivity.java';src.write_text(source);classes=B/'classes';classes.mkdir();dex=B/'dex';dex.mkdir()
manifest=B/'AndroidManifest.xml';manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.lonnnnnng.flclash.networkprobe" android:versionCode="1" android:versionName="1"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/><uses-permission android:name="android.permission.INTERNET"/><uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/><application android:label="KClash网络验收" android:allowBackup="false" android:debuggable="false"><activity android:name="com.lonnnnnng.flclash.NetworkSwitchProbeActivity" android:exported="true" android:permission="android.permission.DUMP" android:theme="@android:style/Theme.Material.Light.NoActionBar"/></application></manifest>''')
def run(args):subprocess.run([str(x) for x in args],check=True,env=os.environ|{'JAVA_HOME':str(JDK)})
run([JDK/'bin/javac','-source','8','-target','8','-classpath',JAR,'-d',classes,src])
run([TOOLS/'d8','--min-api','24','--lib',JAR,'--output',dex,*classes.rglob('*.class')])
unsigned=B/'probe-unsigned.apk';aligned=B/'probe-aligned.apk';apk=B/'probe.apk'
run([TOOLS/'aapt2','link','-I',JAR,'--manifest',manifest,'-o',unsigned])
with zipfile.ZipFile(unsigned,'a') as z:
 for p in dex.glob('*.dex'):z.write(p,p.name)
run([TOOLS/'zipalign','-f','4',unsigned,aligned])
run([TOOLS/'apksigner','sign','--ks',str(Path.home()/'.android/debug.keystore'),'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned])
run([TOOLS/'apksigner','verify',apk]);print('standalone regular app built',hashlib.sha256(apk.read_bytes()).hexdigest())
