"""Independent loopback ground-rover fixture; no physical hardware claims."""
import socket,select,time,json,struct
from pathlib import Path
wire=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);wire.bind(('0.0.0.0',14550))
control=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);control.bind(('0.0.0.0',14551))
peer=None;armed=False;silence=False;wrong=False;seq=0;last=0;events=[];boot=123456;passive=False;fc=False;owner=1;elrs=False
def crc(data,extra):
 c=65535
 for b in data+bytes([extra]):
  t=(b^(c&255));t=(t^(t<<4))&255;c=((c>>8)^(t<<8)^(t<<3)^(t>>4))&65535
 return c
def frame(msg,payload,extra):
 global seq
 b=bytes([254,len(payload),seq%256,2 if wrong else 1,1,msg])+payload;seq+=1
 return b+struct.pack('<H',crc(b[1:],extra))
def heartbeat():return frame(0,struct.pack('<I5B',boot,10,3 if fc else 0,64|(128 if armed else 0),4 if armed else 3,3),50)
while True:
 for s in select.select([wire,control],[],[],.02)[0]:
  data,addr=s.recvfrom(8192)
  if s is control:
   if data==b'SILENCE':silence=True
   if data==b'RESTORE':silence=False;wrong=False
   if data==b'WRONG':wrong=True
   if data==b'REBOOT':boot+=1;armed=False
   if data==b'OBSERVER_START':passive=True;armed=True
   if data==b'OBSERVER_END':passive=False;armed=False
   if data==b'ELRS_START':fc=False;boot=123456;armed=False;owner=2;elrs=True
   if data==b'FC_START':fc=True;boot=0;armed=False;elrs=False;owner=1
   if data==b'EXIT':Path('go-fixture-events.json').write_text(json.dumps(events));raise SystemExit
   continue
  peer=addr
  if data==b'TELERC_DISCOVER_V1':continue
  if len(data)<8 or data[0]!=254 or len(data)!=data[1]+8:continue
  extra={70:124,76:152}.get(data[5]);p=data[6:-2]
  if extra is None or crc(data[1:-2],extra)!=int.from_bytes(data[-2:],'little'):continue
  if data[3:5]!=bytes([255,190]):continue
  if passive:raise AssertionError('Observer sent a control packet')
  if data[5]==70:
   assert p[4:16]==b'\xff'*12,'Go changed an auxiliary channel'
   s,d=struct.unpack('<HH',p[:4]);events.append({'at':time.monotonic(),'rc':[s,d]})
   if s==0 or d==0:
    assert not fc,'FC released overrides before disarm confirmation'
    armed=False
    if elrs:owner=0
  elif int.from_bytes(p[28:30],'little')==176:
   assert fc and struct.unpack('<ff',p[:8])==(1.,4.) and p[8:28]==bytes(20)
   boot=4;events.append({'at':time.monotonic(),'hold':True})
  elif int.from_bytes(p[28:30],'little')==31010:
   assert elrs and struct.unpack('<f',p[:4])[0]==1.
   owner=1;armed=False;events.append({'at':time.monotonic(),'take':True})
  elif int.from_bytes(p[28:30],'little')==400:
   arm=struct.unpack('<f',p[:4])[0];assert p[4:28]==bytes(24)
   armed=arm==1;events.append({'at':time.monotonic(),'arm':armed})
   if not silence:wire.sendto(frame(77,struct.pack('<HB',400,0),143),peer)
 now=time.monotonic()
 if peer and not silence and now-last>.2:
  wire.sendto(heartbeat(),peer)
  own=struct.pack('<II',int(now*1000)&0xffffffff,258 if passive else 256+owner)+b'TELERC_OWN'
  wire.sendto(frame(252,own,44),peer);last=now
