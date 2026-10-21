"""Independent MAVLink fixture for Wi-Fi, source-port fallback and radio authority."""
import socket,select,time,json,struct
from pathlib import Path
wire=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);wire.bind(('0.0.0.0',14550))
control=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);control.bind(('0.0.0.0',14551))
peer=None;armed=False;silence=False;seq=0;last=0;events=[];boot=123456;fc=False;owner=0;radio=False;rf=False;passive=False;transition=0;last_rc=0;neutral_at=None

def crc(data,extra):
 c=65535
 for b in data+bytes([extra]):
  t=(b^(c&255));t=(t^(t<<4))&255;c=((c>>8)^(t<<8)^(t<<3)^(t>>4))&65535
 return c

def frame(msg,payload,extra):
 global seq
 b=bytes([254,len(payload),seq%256,1,1,msg])+payload;seq+=1
 return b+struct.pack('<H',crc(b[1:],extra))

def telemetry():
 hb=frame(0,struct.pack('<I5B',boot,10,3 if fc else 0,64|(128 if armed else 0),4 if armed else 3,3),50)
 if fc:
  p=bytearray(42);struct.pack_into('<H',p,16,1000 if radio else 2000);p[40]=16;p[41]=200
  return hb+frame(65,p,118)
 flags=2048+owner+(256 if rf else 0)+(1024 if radio else 0)
 return hb+frame(252,struct.pack('<II',int(time.monotonic()*1000)&0xffffffff,flags)+b'TELERC_OWN',44)

while True:
 for sock in select.select([wire,control],[],[],.02)[0]:
  data,addr=sock.recvfrom(8192)
  now=time.monotonic()
  if sock is control:
   print('CONTROL',data.decode(),flush=True)
   if data==b'SILENCE':silence=True
   if data==b'RESTORE':silence=False
   if data==b'REBOOT':boot+=1;armed=False;owner=0;neutral_at=None
   if data==b'ELRS_START':fc=False;boot=123456;armed=False;owner=0;rf=True;radio=False
   if data==b'SA_RADIO':radio=True;owner=2;armed=False;transition=now;neutral_at=None
   if data==b'SA_DRIVE':radio=True;owner=2;armed=True;transition=now
   if data==b'SA_PHONE':radio=False;owner=0;armed=False;neutral_at=None
   if data==b'OBSERVER_START':passive=True;radio=True;owner=2;armed=True;rf=True
   if data==b'OBSERVER_END':passive=False;radio=False;owner=0;armed=False
   if data==b'FC_START':fc=True;boot=0;armed=False;radio=False;owner=0;rf=False;neutral_at=None
   if data==b'EXIT':Path('go-fixture-events.json').write_text(json.dumps(events));raise SystemExit
   if peer and not silence:wire.sendto(telemetry(),peer)
   continue
  peer=addr
  if data==b'TELERC_DISCOVER_V1':continue
  if len(data)<8 or data[0]!=254 or len(data)!=data[1]+8:continue
  extra={0:50,70:124,76:152}.get(data[5]);p=data[6:-2]
  if extra is None or crc(data[1:-2],extra)!=int.from_bytes(data[-2:],'little'):continue
  if data[3:5]!=bytes([255,190]):continue
  if data[5]==0:continue
  command=int.from_bytes(p[28:30],'little') if data[5]==76 else 0
  if command==511:
   assert fc and struct.unpack('<ff',p[:8])==(65.,200000.) and p[8:28]==bytes(20)
   events.append({'at':now,'request_rc':True});continue
  if passive:raise AssertionError('Observer sent actuator command')
  if radio:
   events.append({'at':now,'ignored_after_switch':True})
   assert now-transition<.5,'Phone continued actuator commands after confirmed SA takeover'
   continue
  if data[5]==70:
   assert p[4:16]==b'\xff'*12,'Go changed an auxiliary channel'
   steer,drive=struct.unpack('<HH',p[:4]);events.append({'at':now,'rc':[steer,drive],'source_port':addr[1]})
   if steer==0 or drive==0:
    assert not fc,'FC released overrides before confirmed disarm'
    armed=False;owner=0;neutral_at=None;continue
   if not fc and owner==0 and not rf and 1475<=steer<=1525 and 1475<=drive<=1525:owner=1
   if not fc and owner!=1:continue
   if 1475<=steer<=1525 and 1475<=drive<=1525:
    if neutral_at is None or now-last_rc>=.25:neutral_at=now
   else:neutral_at=None
   last_rc=now
  elif command==176:
   assert fc and struct.unpack('<ff',p[:8])==(1.,4.) and p[8:28]==bytes(20)
   boot=4;events.append({'at':now,'hold':True})
  elif command==31010:
   assert not fc and struct.unpack('<f',p[:4])[0]==1.
   owner=1;armed=False;neutral_at=None;events.append({'at':now,'take':True})
  elif command==400:
   arm=struct.unpack('<f',p[:4])[0];assert p[4:28]==bytes(24)
   if arm==1:
    assert neutral_at is not None and now-neutral_at>=.25,'ARM without successfully sent neutral dwell'
    assert fc and boot==0 or not fc and owner==1
   armed=arm==1
   if not armed and not fc:owner=0
   events.append({'at':now,'arm':armed})
   if not silence:wire.sendto(frame(77,struct.pack('<HB',400,0),143),peer)
 now=time.monotonic()
 if armed and not radio and now-last_rc>=.5:armed=False
 Path('go-fixture-events.json').write_text(json.dumps(events))
 if peer and not silence and now-last>.2:wire.sendto(telemetry(),peer);last=now
