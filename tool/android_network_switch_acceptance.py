"""作者 long：独立应用真实DNS/HTTPS与核心同源端口归因，回执只包含摘要。"""
import argparse,base64,hashlib,importlib.util,json,re,subprocess,time
from pathlib import Path
REPO=Path(__file__).resolve().parents[1]
bootstrap=argparse.ArgumentParser(add_help=False)
bootstrap.add_argument('--artifacts',type=Path,required=True)
bootstrap.add_argument('--client-package',choices=['com.lonnnnnng.flclash.motion.test','com.lonnnnnng.flclash.networkprobe'],default='com.lonnnnnng.flclash.networkprobe')
bootstrap.add_argument('--device-serial',default='wsvwypiz7xwslvl7')
bootstrap.add_argument('--device-model')
settings,remaining=bootstrap.parse_known_args()
ART=settings.artifacts.resolve()
spec=importlib.util.spec_from_file_location('endpoint',REPO/'tool/android_endpoint_nodes_acceptance.py'); helpers=importlib.util.module_from_spec(spec);spec.loader.exec_module(helpers)
helpers.TEST_PACKAGE=settings.client_package
helpers.configure_target(settings.device_serial,settings.device_model)
adb=helpers.adb
state=json.loads((ART/'run.json').read_text()); TOKEN=state['token']
assert re.fullmatch('[a-f0-9]{32}',TOKEN)
helpers.assert_target()
def command(name,port=0):
 assert re.fullmatch('[a-z0-9-]+',name) and not (ART/(name+'.json')).exists(), 'unique command name required'
 adb('shell','am','start','-W','-n',helpers.PACKAGE+'/com.lonnnnnng.flclash.StabilityWindowAcceptanceActivity','--es','token',TOKEN,'--es','command',name,'--ei','sourcePort',str(port),timeout=45)
 end=time.monotonic()+100
 while time.monotonic()<end:
  events=helpers.report_items(ART,TOKEN,name)
  found=[e for e in events if e.get('command')==name]
  if found:
   result=found[-1];(ART/(name+'.json')).write_text(json.dumps(result,indent=2)+'\n');return result
  time.sleep(.5)
 raise TimeoutError(name)
def probe(name,port):
 assert re.fullmatch('[a-z0-9-]+',name) and not (ART/('probe-'+name+'.json')).exists(), 'unique probe name required'
 # 作者 long：只重建独立客户端，不结束核心会话；避免Activity复用导致本次请求不执行。
 adb('shell','am','force-stop',helpers.TEST_PACKAGE)
 observer='switch-arm-'+name
 adb('shell','am','start','-W','-n',helpers.PACKAGE+'/com.lonnnnnng.flclash.StabilityWindowAcceptanceActivity','--es','token',TOKEN,'--es','command',observer,'--ei','sourcePort',str(port),timeout=45)
 time.sleep(.5)
 adb('shell','am','start','-W','-n',helpers.TEST_PACKAGE+'/com.lonnnnnng.flclash.NetworkSwitchProbeActivity','--es','token',TOKEN,'--es','command',name,'--ei','sourcePort',str(port),'--ez','bindVpn',str(name.startswith('bound-')).lower(),timeout=45)
 result=None; observed=None;end=time.monotonic()+95
 while time.monotonic()<end:
  raw=adb('logcat','-d','-v','raw','-s','KClashSwitchProbe:I','*:S').decode(errors='replace')
  for line in raw.splitlines():
   try:item=json.loads(line)
   except ValueError:continue
   if item.get('token')==TOKEN and item.get('command')==name:result=item
  events=helpers.report_items(ART,TOKEN,observer)
  found=[e for e in events if e.get('command')==observer]
  if found:observed=found[-1]
  if result and observed:break
  time.sleep(.5)
 assert result is not None and observed is not None,'incomplete probe evidence'
 (ART/('probe-'+name+'.json')).write_text(json.dumps(result,indent=2)+'\n')
 (ART/(observer+'.json')).write_text(json.dumps(observed,indent=2)+'\n')
 details=observed.get('networkSwitch',{})
 attributed=any(x.get('nonDirect') and x.get('upload',0)>0 and x.get('download',0)>0 for x in details.get('connections',[]))
 summary={'case':name,'dnsPassed':result.get('dnsPassed',False),'httpsPassed':result.get('passed',False),'http':result.get('http'),'failureType':result.get('failureType'),'failedStep':result.get('failedStep'),'attributedNonDirect':attributed,'sourcePort':port,'elapsedMillis':result.get('elapsedMillis'),'observer':details.get('observer'),'transports':[{'handle':x.get('handle'),'wifi':x.get('wifi'),'cellular':x.get('cellular'),'vpn':x.get('vpn'),'validated':x.get('validated'),'internet':x.get('internet')} for x in details.get('networks',[])],'runningSinceElapsedMs':details.get('runningSinceElapsedMs')}
 if result.get('requestStartedElapsedMs') is not None and details.get('runningSinceElapsedMs') is not None:
  summary['requestSinceCoreReadyMillis']=result['requestStartedElapsedMs']-details['runningSinceElapsedMs']
 if result.get('tlsCompletedElapsedMs') is not None:
  summary['tlsMillis']=result['tlsCompletedElapsedMs']-result['tlsStartedElapsedMs']
 if result.get('failureChain'):summary['failureChain']=result['failureChain']
 (ART/('summary-'+name+'.json')).write_text(json.dumps(summary,indent=2)+'\n')
 print(json.dumps(summary),flush=True)
 return summary
if __name__=='__main__':
 p=argparse.ArgumentParser(parents=[bootstrap]);p.add_argument('mode',choices=['command','probe','init']);p.add_argument('name',nargs='?');p.add_argument('--port',type=int,default=44000);a=p.parse_args()
 if a.mode=='command':
  result=command(a.name,a.port); n=result.get('networkSwitch',{}); print(json.dumps({'command':a.name,'passed':result.get('passed'),'failureType':result.get('failureType'),'runtime':n.get('runtime'),'vpnAlive':n.get('vpnAlive'),'runningSinceElapsedMs':n.get('runningSinceElapsedMs'),'socksPassed':n.get('socksRequest',{}).get('passed')}),flush=True)
 if a.mode=='probe':probe(a.name,a.port)
 if a.mode=='init':
  adb('shell','am','start','-W','-n',helpers.TEST_PACKAGE+'/com.lonnnnnng.flclash.NetworkSwitchProbeActivity','--es','token',TOKEN,'--es','command','init')
  urls=re.findall(r'https://[^\s`<>]+',(REPO.parent/'AGENTS.md').read_text());assert len(urls)==1
  data=json.dumps({'token':TOKEN,'authorizedUrl':urls[0]}).encode();local=ART/('probe-input-'+TOKEN+'.json');local.write_bytes(data)
  remote='/sdcard/Android/data/'+helpers.TEST_PACKAGE+'/files/switch-'+TOKEN+'.json'
  adb('push',str(local),remote);assert hashlib.sha256(adb('exec-out','cat',remote)).digest()==hashlib.sha256(data).digest();local.unlink()
  (ART/'probe-input-manifest.json').write_text(json.dumps({'path':remote,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)},indent=2)+'\n')
  print('private authorized target copied over USB only')
