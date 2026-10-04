import importlib.util
from pathlib import Path
import unittest
spec = importlib.util.spec_from_file_location("relay", Path(__file__).parents[1] / "lora_usb_relay.py")
relay = importlib.util.module_from_spec(spec)
spec.loader.exec_module(relay)


class RelayTests(unittest.TestCase):
    def test_fragments_and_multiple_frames(self):
        parser = relay.Parser()
        frame = relay.encode(b"neutral")
        self.assertEqual(parser.feed(b"startup log\n" + frame[:3], 1), [])
        self.assertEqual(parser.feed(frame[3:] + relay.encode(b"arm"), 1.005), [b"neutral", b"arm"])

    def test_corrupt_frame_rejected_and_next_frame_recovers(self):
        bad = bytearray(relay.encode(b"drive"))
        bad[5] ^= 1
        self.assertEqual(relay.Parser().feed(bad + relay.encode(b"stop"), 1), [b"stop"])

    def test_partial_frame_expires(self):
        parser = relay.Parser()
        frame = relay.encode(b"drive")
        parser.feed(frame[:4], 1)
        self.assertEqual(parser.feed(frame[4:], 1.021), [])
        self.assertEqual(parser.feed(relay.encode(b"stop"), 1.022), [b"stop"])

    def test_bounds(self):
        for packet in [b"", b"x" * 281]:
            with self.assertRaises(ValueError):
                relay.encode(packet)
        packet = b"x" * 280
        self.assertEqual(relay.Parser().feed(relay.encode(packet), 1), [packet])
