#include "MiniMavlink.h"
#include <cassert>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>
#include <iostream>

int main(int argc, char **argv) {
  assert(argc == 2);
  const std::string root = argv[1];
  for (const char *name : {"neutral", "drive", "release", "arm", "disarm"}) {
    std::ifstream file(root + "/" + name + ".bin", std::ios::binary);
    assert(file.good());
    std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(file)), {});
    mini_mav::Message message;
    size_t used = 0;
    assert(mini_mav::decode(bytes.data(), bytes.size(), message, used));
    assert(used == bytes.size() && message.system == 255 && message.component == 190);
    const std::string kind = name;
    if (kind == "arm" || kind == "disarm") {
      assert(message.id == 76 && message.length == 33);
      assert(mini_mav::u16(message.payload + 28) == 400);
      assert(message.payload[30] == 1 && message.payload[31] == 1);
      float value = -1; memcpy(&value, message.payload, 4);
      assert(value == (kind == "arm" ? 1.0f : 0.0f));
      for (int i = 4; i < 28; ++i) assert(message.payload[i] == 0); // no force arm
    } else {
      assert(message.id == 70 && message.length == 18);
      assert(message.payload[16] == 1 && message.payload[17] == 1);
      assert(mini_mav::u16(message.payload) == (kind == "drive" ? 1700 : kind == "release" ? 0 : 1500));
      assert(mini_mav::u16(message.payload+2) == (kind == "drive" ? 1200 : kind == "release" ? 0 : 1500));
    }
    bytes[6] ^= 1;
    assert(!mini_mav::decode(bytes.data(), bytes.size(), message, used));
  }
  std::cout << "PASS: Android production MAVLink packets accepted by MiniRover 1.1.0 parser\n";
}
