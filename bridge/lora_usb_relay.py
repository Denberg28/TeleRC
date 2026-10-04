#!/usr/bin/env python3
"""Local UDP endpoint for existing PC TeleRC, backed by a T3-S3 USB gateway.

pip install pyserial
python bridge/lora_usb_relay.py --serial COM7
PC TeleRC: target 127.0.0.1, target port 14551, local port 14550.
"""
import argparse
import binascii
import select
import socket
import struct
import time

MAX_PAYLOAD = 280


def encode(payload: bytes) -> bytes:
    if not 0 < len(payload) <= MAX_PAYLOAD:
        raise ValueError("Invalid datagram size")
    body = struct.pack("<H", len(payload)) + payload
    return b"\xa5\x5a" + body + struct.pack("<H", binascii.crc_hqx(body, 0xFFFF))


class Parser:
    def __init__(self):
        self.buffer = bytearray()
        self.last_at = 0.0

    def feed(self, data: bytes, now: float):
        if self.buffer and now - self.last_at > 0.020:
            self.buffer.clear()
        self.last_at = now
        self.buffer.extend(data)
        result = []
        while self.buffer:
            sync = self.buffer.find(b"\xa5\x5a")
            if sync < 0:
                self.buffer[:] = self.buffer[-1:] if self.buffer[-1] == 0xA5 else b""
                break
            if sync:
                del self.buffer[:sync]
            if len(self.buffer) < 4:
                break
            length = struct.unpack_from("<H", self.buffer, 2)[0]
            if not 0 < length <= MAX_PAYLOAD:
                del self.buffer[:2]
                continue
            total = length + 6
            if len(self.buffer) < total:
                break
            frame = self.buffer[:total]
            del self.buffer[:total]
            crc = struct.unpack_from("<H", frame, total - 2)[0]
            if binascii.crc_hqx(frame[2:-2], 0xFFFF) == crc:
                result.append(bytes(frame[4:-2]))
        return result


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("--serial", required=True, help="T3-S3 USB port, e.g. COM7")
    args.add_argument("--listen-port", type=int, default=14551)
    args.add_argument("--app-port", type=int, default=14550)
    options = args.parse_args()
    if options.listen_port == options.app_port:
        args.error("Relay and app ports must differ")
    import serial

    parser = Parser()
    peer = ("127.0.0.1", options.app_port)
    with serial.Serial(options.serial, 115200, timeout=0, write_timeout=0) as port, socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
        udp.bind(("127.0.0.1", options.listen_port))
        udp.setblocking(False)
        print(f"LoRa USB relay ready on 127.0.0.1:{options.listen_port}; app port {options.app_port}")
        try:
            while True:
                readable, _, _ = select.select([udp], [], [], 0.005)
                if readable:
                    # Bound local input work; base firmware provides the final latest-command mailbox.
                    for _ in range(8):
                        try:
                            packet, sender = udp.recvfrom(MAX_PAYLOAD + 1)
                        except BlockingIOError:
                            break
                        if sender == peer and 0 < len(packet) <= MAX_PAYLOAD:
                            frame = encode(packet)
                            if port.write(frame) != len(frame):
                                raise OSError("Incomplete USB write; relay stopped")
                for packet in parser.feed(port.read(512), time.monotonic()):
                    udp.sendto(packet, peer)
        finally:
            try:
                port.write(encode(b"TELERC_DISCONNECT_V1"))
            except (OSError, serial.SerialException):
                pass  # Independent motor watchdog handles missing traffic.


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
