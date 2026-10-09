#!/usr/bin/env bash
# Simulated drive for the Autoradio emulator: one GPS fix per second heading north,
# speed ramps 0 -> 110 km/h, cruises, then slows to 50 km/h. Run while the emulator is up.
ADB="${ADB:-$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe}"
lat=48.8566; lon=2.3522
speeds=(0 10 20 30 40 50 60 70 80 90 100 110 110 110 110 110 100 90 80 70 60 50 50 50 50 50 30 10 0)
while true; do
  for v in "${speeds[@]}"; do
    lat=$(awk -v a="$lat" -v v="$v" 'BEGIN{printf "%.6f", a + v/3.6/111320}')
    knots=$(awk -v v="$v" 'BEGIN{printf "%.2f", v/1.852}')
    "$ADB" emu geo fix "$lon" "$lat" 35 8 "$knots" >/dev/null
    echo "$v km/h"
    sleep 1
  done
done
