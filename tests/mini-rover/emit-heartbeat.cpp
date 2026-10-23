#include "MiniMavlink.h"
#include <cstdio>
int main() {
  // Identical to TeleRC_MiniRover.ino heartbeat(): generic ground rover, disarmed.
  const uint8_t payload[9] = {0,0,0,0,10,0,64,3,3};
  uint8_t frame[17];
  const size_t size = mini_mav::encode(frame, 0, 1, 1, 0, payload, 9, 50);
  return fwrite(frame, 1, size, stdout) == size ? 0 : 1;
}
