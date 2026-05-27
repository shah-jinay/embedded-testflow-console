#!/usr/bin/env python3
"""
ETFC Demo Seed Script
Populates the backend with ~200 realistic test runs via the ingestion REST API.

Usage:
    python3 scripts/seed.py [BASE_URL]
    ./scripts/seed.sh              # delegates here

BASE_URL defaults to $ETFC_URL or http://localhost:8080.
The script is idempotent: running it twice does not create duplicates.
"""

import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from itertools import cycle

# ── Configuration ──────────────────────────────────────────────────────────────

BASE_URL = (
    sys.argv[1].rstrip("/") if len(sys.argv) > 1
    else os.environ.get("ETFC_URL", "http://localhost:8080")
) + "/api"

API_KEY = os.environ.get("ETFC_API_KEY", "demo-key")

# Deterministic random so every seed run produces the same data layout.
RNG = random.Random(42)

# ── Terminal colors ────────────────────────────────────────────────────────────

BOLD = "\033[1m"; GRN = "\033[0;32m"; BLU = "\033[0;34m"
CYN = "\033[0;36m"; YLW = "\033[1;33m"; RED = "\033[0;31m"; RST = "\033[0m"

def section(m): print(f"\n{BOLD}{BLU}==> {m}{RST}")
def ok(m):      print(f"  {GRN}✓{RST}  {m}")
def info(m):    print(f"  {CYN}·{RST}  {m}")
def die(m):     print(f"{RED}{m}{RST}", file=sys.stderr); sys.exit(1)

# ── HTTP helpers ───────────────────────────────────────────────────────────────

def _request(method, path, body=None):
    url = BASE_URL + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if API_KEY:
        headers["Authorization"] = f"Bearer {API_KEY}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    while True:
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read())
        except urllib.error.HTTPError as exc:
            if exc.code == 429:
                wait = int(exc.headers.get("Retry-After", 5)) + 1
                print(f"  {YLW}rate-limited — sleeping {wait}s…{RST}")
                time.sleep(wait)
                continue
            snippet = exc.read().decode()[:400]
            die(f"HTTP {exc.code} {method} {path}\n  {snippet}")

def post(path, body):  return _request("POST",  path, body)
def patch(path, body): return _request("PATCH", path, body)

def check_health():
    try:
        resp = _request("GET", "/health")
        if resp.get("status") != "UP":
            die(f"Backend unhealthy (status={resp.get('status')}). Is the stack running?")
    except SystemExit:
        raise
    except Exception as exc:
        die(f"Cannot reach {BASE_URL}: {exc}\nRun 'docker compose up' first.")

# ── Catalog data ───────────────────────────────────────────────────────────────

SUITES = [
    {
        "name": "Boot Sequence",
        "targetComponent": "MCU Boot",
        "owner": "firmware-team",
        "severity": "CRITICAL",
        "description": "Validates cold-start through firmware handoff",
        "cases": [
            {"name": "cold-boot-timing",      "expectedBehavior": "Device boots within 500 ms of power-on",                "timeoutMs": 5000,   "criticality": "CRITICAL"},
            {"name": "watchdog-recovery",      "expectedBehavior": "Watchdog fires and device recovers within 2 s",         "timeoutMs": 10000,  "criticality": "CRITICAL"},
            {"name": "flash-crc-check",        "expectedBehavior": "CRC of flash matches embedded manifest",                "timeoutMs": 3000,   "criticality": "CRITICAL"},
            {"name": "ram-self-test",          "expectedBehavior": "All RAM banks pass BIST with zero errors",              "timeoutMs": 8000,   "criticality": "HIGH"},
            {"name": "clock-init",             "expectedBehavior": "System and peripheral clocks match target frequencies", "timeoutMs": 2000,   "criticality": "HIGH"},
            {"name": "peripheral-enumeration", "expectedBehavior": "All expected peripherals respond to probe",             "timeoutMs": 4000,   "criticality": "HIGH"},
            {"name": "secure-boot-verify",     "expectedBehavior": "Signature on firmware image passes verification",       "timeoutMs": 6000,   "criticality": "CRITICAL"},
            {"name": "boot-log-integrity",     "expectedBehavior": "Boot log written to UART with no corruption",          "timeoutMs": 3000,   "criticality": "MEDIUM"},
        ],
    },
    {
        "name": "Peripheral Bus",
        "targetComponent": "HAL Layer",
        "owner": "drivers-team",
        "severity": "HIGH",
        "description": "Exercises SPI, I2C, UART, DMA, GPIO and ADC peripherals",
        "cases": [
            {"name": "spi-loopback",           "expectedBehavior": "Full-duplex SPI loopback at 10 MHz returns exact bytes",         "timeoutMs": 2000,  "criticality": "HIGH"},
            {"name": "i2c-scan",               "expectedBehavior": "I2C bus scan returns all expected device addresses",             "timeoutMs": 1000,  "criticality": "HIGH"},
            {"name": "uart-framing",           "expectedBehavior": "UART transmits and receives 1000 bytes without framing errors",  "timeoutMs": 3000,  "criticality": "HIGH"},
            {"name": "dma-transfer",           "expectedBehavior": "DMA memory-to-memory 64 KB transfer completes in one burst",    "timeoutMs": 5000,  "criticality": "HIGH"},
            {"name": "gpio-interrupt-latency", "expectedBehavior": "GPIO edge-to-ISR latency is under 10 us",                       "timeoutMs": 1000,  "criticality": "MEDIUM"},
            {"name": "adc-calibration",        "expectedBehavior": "ADC offset and gain error within +-0.5 LSB after calibration",  "timeoutMs": 4000,  "criticality": "HIGH"},
            {"name": "pwm-frequency-accuracy", "expectedBehavior": "PWM output frequency within +-0.1% of setpoint",               "timeoutMs": 2000,  "criticality": "MEDIUM"},
            {"name": "usb-enumeration",        "expectedBehavior": "USB device enumerates on host within 3 s",                      "timeoutMs": 10000, "criticality": "HIGH"},
        ],
    },
    {
        "name": "Power Management",
        "targetComponent": "PMIC",
        "owner": "power-team",
        "severity": "HIGH",
        "description": "Sleep current, wakeup latency, battery and thermal behaviour",
        "cases": [
            {"name": "sleep-current-draw",     "expectedBehavior": "Deep-sleep current below 10 uA at 3.3 V",                         "timeoutMs": 30000,  "criticality": "HIGH"},
            {"name": "wakeup-latency",         "expectedBehavior": "RTC wakeup completes within 5 ms",                                "timeoutMs": 5000,   "criticality": "HIGH"},
            {"name": "battery-level-accuracy", "expectedBehavior": "Reported SOC within +-3% of calibrated reference",                "timeoutMs": 8000,   "criticality": "MEDIUM"},
            {"name": "charging-state-machine", "expectedBehavior": "Charger transitions CC to CV to done without glitches",           "timeoutMs": 60000,  "criticality": "HIGH"},
            {"name": "brownout-detection",     "expectedBehavior": "BOR triggers at configured threshold +-50 mV",                    "timeoutMs": 5000,   "criticality": "CRITICAL"},
            {"name": "power-rail-sequencing",  "expectedBehavior": "PMIC rails come up in specified order within 100 ms",             "timeoutMs": 3000,   "criticality": "HIGH"},
            {"name": "ldo-output-voltage",     "expectedBehavior": "LDO output within +-1% under full load",                          "timeoutMs": 5000,   "criticality": "HIGH"},
            {"name": "switch-mode-efficiency", "expectedBehavior": "SMPS efficiency above 85% at 50% load",                           "timeoutMs": 10000,  "criticality": "MEDIUM"},
            {"name": "thermal-shutdown",       "expectedBehavior": "Over-temperature shutdown activates at 85 C +-5 C",               "timeoutMs": 120000, "criticality": "CRITICAL"},
        ],
    },
    {
        "name": "Network Connectivity",
        "targetComponent": "WiFi+BT Stack",
        "owner": "network-team",
        "severity": "HIGH",
        "description": "WiFi association, BLE pairing, MQTT and TLS integration",
        "cases": [
            {"name": "wifi-association",    "expectedBehavior": "Device associates to AP within 5 s",                         "timeoutMs": 10000, "criticality": "HIGH"},
            {"name": "wifi-rssi-threshold", "expectedBehavior": "Connection maintained above -80 dBm RSSI",                   "timeoutMs": 5000,  "criticality": "MEDIUM"},
            {"name": "ble-advertisement",   "expectedBehavior": "BLE advertisement visible to scanner within 1 s",            "timeoutMs": 5000,  "criticality": "MEDIUM"},
            {"name": "ble-pairing",         "expectedBehavior": "BLE pairing completes in under 3 s",                         "timeoutMs": 10000, "criticality": "HIGH"},
            {"name": "mqtt-reconnect",      "expectedBehavior": "MQTT client reconnects within 10 s of broker restart",       "timeoutMs": 15000, "criticality": "HIGH"},
            {"name": "tls-handshake",       "expectedBehavior": "TLS 1.3 handshake completes within 2 s",                    "timeoutMs": 5000,  "criticality": "CRITICAL"},
            {"name": "dns-resolution",      "expectedBehavior": "DNS query resolves within 1 s on good network",              "timeoutMs": 3000,  "criticality": "HIGH"},
            {"name": "ntp-sync",            "expectedBehavior": "NTP sync achieves accuracy within +-100 ms",                 "timeoutMs": 10000, "criticality": "MEDIUM"},
        ],
    },
    {
        "name": "OTA Update",
        "targetComponent": "OTA Engine",
        "owner": "firmware-team",
        "severity": "CRITICAL",
        "description": "Firmware download, verification, delta-patch and rollback",
        "cases": [
            {"name": "signature-verification", "expectedBehavior": "Firmware image signature verified before flash",              "timeoutMs": 10000, "criticality": "CRITICAL"},
            {"name": "download-integrity",     "expectedBehavior": "SHA-256 of downloaded image matches manifest",               "timeoutMs": 15000, "criticality": "CRITICAL"},
            {"name": "rollback-on-failure",    "expectedBehavior": "Failed update rolls back to previous firmware",              "timeoutMs": 30000, "criticality": "CRITICAL"},
            {"name": "delta-patch-apply",      "expectedBehavior": "Delta patch applied to base image yields correct target",    "timeoutMs": 20000, "criticality": "HIGH"},
            {"name": "version-bump",           "expectedBehavior": "Reported version matches newly installed image",             "timeoutMs": 5000,  "criticality": "HIGH"},
            {"name": "dual-bank-switch",       "expectedBehavior": "Execution switches to new bank after successful OTA",       "timeoutMs": 10000, "criticality": "CRITICAL"},
            {"name": "progress-reporting",     "expectedBehavior": "Progress events emitted every 5% of download",              "timeoutMs": 20000, "criticality": "MEDIUM"},
            {"name": "post-update-self-test",  "expectedBehavior": "Self-test suite passes on first boot after OTA",            "timeoutMs": 30000, "criticality": "HIGH"},
        ],
    },
    {
        "name": "Sensor Fusion",
        "targetComponent": "IMU Pipeline",
        "owner": "sensors-team",
        "severity": "MEDIUM",
        "description": "Accelerometer, gyroscope, magnetometer and fusion accuracy",
        "cases": [
            {"name": "accelerometer-offset",     "expectedBehavior": "Accel zero-g offset within +-20 mg after calibration",            "timeoutMs": 5000,  "criticality": "HIGH"},
            {"name": "gyroscope-noise",          "expectedBehavior": "Gyro noise density below 0.01 dps/sqrt-Hz at 100 Hz",            "timeoutMs": 5000,  "criticality": "HIGH"},
            {"name": "magnetometer-calibration", "expectedBehavior": "Hard and soft iron calibration reduces error below 1 uT",         "timeoutMs": 10000, "criticality": "HIGH"},
            {"name": "quaternion-accuracy",      "expectedBehavior": "Fusion quaternion error below 2 degrees RMS after 60 s of motion","timeoutMs": 60000, "criticality": "HIGH"},
            {"name": "fusion-latency",           "expectedBehavior": "Fusion output available within 10 ms of sensor data",             "timeoutMs": 2000,  "criticality": "HIGH"},
            {"name": "interrupt-jitter",         "expectedBehavior": "Data-ready interrupt jitter below 50 us",                         "timeoutMs": 2000,  "criticality": "MEDIUM"},
            {"name": "oversampling-stability",   "expectedBehavior": "8x oversampling reduces noise by at least 6 dB",                  "timeoutMs": 5000,  "criticality": "MEDIUM"},
            {"name": "temperature-compensation", "expectedBehavior": "Accel sensitivity drift below 0.1%/deg across 0-60 C",           "timeoutMs": 30000, "criticality": "HIGH"},
            {"name": "shock-detection",          "expectedBehavior": "Free-fall and shock events detected with zero false negatives",   "timeoutMs": 10000, "criticality": "CRITICAL"},
        ],
    },
]

DEVICES = [
    {"externalDeviceId": "DEV-ALPHA-001",   "boardRevision": "rev-A", "mcuFamily": "STM32H7",   "environment": "production"},
    {"externalDeviceId": "DEV-ALPHA-002",   "boardRevision": "rev-A", "mcuFamily": "STM32H7",   "environment": "production"},
    {"externalDeviceId": "DEV-BETA-001",    "boardRevision": "rev-B", "mcuFamily": "STM32F4",   "environment": "staging"},
    {"externalDeviceId": "DEV-BETA-002",    "boardRevision": "rev-B", "mcuFamily": "STM32F4",   "environment": "staging"},
    {"externalDeviceId": "DEV-GAMMA-001",   "boardRevision": "rev-A", "mcuFamily": "NRF52840",  "environment": "production"},
    {"externalDeviceId": "DEV-GAMMA-002",   "boardRevision": "rev-B", "mcuFamily": "NRF52840",  "environment": "staging"},
    {"externalDeviceId": "DEV-DELTA-001",   "boardRevision": "rev-A", "mcuFamily": "ESP32S3",   "environment": "ci"},
    {"externalDeviceId": "DEV-DELTA-002",   "boardRevision": "rev-B", "mcuFamily": "ESP32S3",   "environment": "ci"},
    {"externalDeviceId": "DEV-EPSILON-001", "boardRevision": "rev-A", "mcuFamily": "RP2040",    "environment": "staging"},
    {"externalDeviceId": "DEV-EPSILON-002", "boardRevision": "rev-C", "mcuFamily": "RP2040",    "environment": "ci"},
]

FIRMWARE = [
    {"version": "v1.0.0",     "branch": "main",        "commitHash": "a1b2c3d4e5f6", "releaseCandidate": False},
    {"version": "v1.1.0",     "branch": "main",        "commitHash": "b2c3d4e5f6a7", "releaseCandidate": False},
    {"version": "v1.2.0",     "branch": "main",        "commitHash": "c3d4e5f6a7b8", "releaseCandidate": False},
    {"version": "v1.3.0-rc1", "branch": "release/1.3", "commitHash": "d4e5f6a7b8c9", "releaseCandidate": True},
    {"version": "v2.0.0-rc1", "branch": "release/2.0", "commitHash": "e5f6a7b8c9d0", "releaseCandidate": True},
]

# ── Status distribution ────────────────────────────────────────────────────────

# 60 % PASSED · 20 % FAILED · 10 % TIMED_OUT · 10 % NEEDS_REVIEW
STATUS_WHEEL = cycle(["PASSED"] * 6 + ["FAILED"] * 2 + ["TIMED_OUT"] + ["NEEDS_REVIEW"])

FAIL_CATS = ["FIRMWARE_RESPONSE_MISMATCH", "DEVICE_UNAVAILABLE", "ENVIRONMENT_FAILURE", "COMMUNICATION_TIMEOUT"]

# ── Seeding ────────────────────────────────────────────────────────────────────

def seed_suites():
    """Registers all suites and their cases. Returns {name: {id, cases: [name]}}."""
    section("Test Suites & Cases")
    catalog = {}
    for suite in SUITES:
        body = {k: v for k, v in suite.items() if k != "cases"}
        resp = post("/test-suites", body)
        sid = resp["id"]
        for case in suite["cases"]:
            post(f"/test-suites/{sid}/cases", case)
        catalog[suite["name"]] = {"id": sid, "cases": [c["name"] for c in suite["cases"]]}
        ok(f'{suite["name"]}  ({len(suite["cases"])} cases)')
    return catalog


def seed_devices():
    section("Devices")
    for d in DEVICES:
        post("/devices", d)
        ok(f'{d["externalDeviceId"]}  {d["mcuFamily"]} / {d["boardRevision"]} / {d["environment"]}')


def seed_firmware():
    section("Firmware Builds")
    for fw in FIRMWARE:
        post("/firmware-builds", fw)
        tag = "RC" if fw["releaseCandidate"] else "stable"
        ok(f'{fw["version"]}  branch={fw["branch"]}  [{tag}]')


def build_results(case_names, run_status, idx):
    """Builds result records matching the run's terminal status."""
    fail_case_idx = idx % len(case_names)
    fail_cat = FAIL_CATS[idx % len(FAIL_CATS)]

    results = []
    for i, name in enumerate(case_names):
        is_fault_case = (i == fail_case_idx)

        if run_status == "PASSED" or not is_fault_case:
            results.append({
                "caseName": name,
                "attemptNumber": 1,
                "status": "PASSED",
                "durationMs": RNG.randint(150, 4500),
            })
        elif run_status == "FAILED":
            msg = {
                "FIRMWARE_RESPONSE_MISMATCH": f"Expected 0x{RNG.randint(0,255):02X} but received 0x{RNG.randint(0,255):02X} at address 0x{RNG.randint(0,0xFFFF):04X}",
                "DEVICE_UNAVAILABLE":         "Hardware fault: peripheral did not respond within timeout window",
                "ENVIRONMENT_FAILURE":        "Test environment not ready: prerequisite condition not met",
                "COMMUNICATION_TIMEOUT":      "Operation exceeded allowed timeout of 5000 ms",
            }[fail_cat]
            results.append({
                "caseName": name, "attemptNumber": 1, "status": "FAILED",
                "durationMs": RNG.randint(200, 3000),
                "failureCategory": fail_cat, "failureMessage": msg,
            })
        elif run_status == "TIMED_OUT":
            results.append({
                "caseName": name, "attemptNumber": 1, "status": "TIMED_OUT",
                "durationMs": 30000, "failureCategory": "TIMEOUT",
                "failureMessage": "Test exceeded maximum allowed wall-clock time",
            })
        elif run_status == "NEEDS_REVIEW":
            results.append({
                "caseName": name, "attemptNumber": 1, "status": "NEEDS_REVIEW",
                "durationMs": RNG.randint(1000, 6000), "failureCategory": "FLAKY_TEST",
                "failureMessage": "Non-deterministic outcome; requires manual inspection",
            })
    return results


def create_run(fw_ver, suite_name, device_id, env, days_ago, idx, catalog):
    run_status = next(STATUS_WHEEL)

    fw_slug    = fw_ver.replace(".", "-")
    suite_slug = suite_name.lower().replace(" ", "-")
    external_id = f"seed-{fw_slug}-{suite_slug}-{device_id.lower()}"

    now = datetime.now(timezone.utc)
    started   = now - timedelta(days=days_ago, hours=idx % 20)
    completed = started + timedelta(minutes=RNG.randint(3, 30))

    run_resp = post("/test-runs", {
        "externalRunId":   external_id,
        "deviceExternalId": device_id,
        "firmwareVersion":  fw_ver,
        "suiteName":       suite_name,
        "environment":     env,
        "startedAt":       started.strftime("%Y-%m-%dT%H:%M:%SZ"),
    })
    run_id = run_resp["id"]
    current_status = run_resp["status"]

    # Idempotent re-run: already past the initial transition phase, nothing to do
    if current_status not in ("QUEUED", "RUNNING"):
        return

    # State machine: QUEUED → RUNNING → <terminal>
    if current_status == "QUEUED":
        patch(f"/test-runs/{run_id}/status", {"status": "RUNNING"})

    patch(f"/test-runs/{run_id}/status", {
        "status":      run_status,
        "completedAt": completed.strftime("%Y-%m-%dT%H:%M:%SZ"),
    })

    if run_status not in ("QUEUED", "BLOCKED"):
        cases = catalog[suite_name]["cases"]
        post(f"/test-runs/{run_id}/results", {"results": build_results(cases, run_status, idx)})


def seed_runs(catalog):
    section("Test Runs")

    all_suites   = [s["name"] for s in SUITES]
    prod_devices = [d["externalDeviceId"] for d in DEVICES if d["environment"] in ("production", "staging")]
    rc_devices   = [d["externalDeviceId"] for d in DEVICES if d["environment"] in ("staging", "ci")][:6]

    idx = 0

    # ── Batch 1: stable firmware × all suites × all production+staging devices ──
    for i, fw in enumerate(["v1.0.0", "v1.1.0", "v1.2.0"]):
        for j, suite in enumerate(all_suites):
            for k, dev in enumerate(prod_devices):
                days = max(1, 28 - i * 8 - j * 2 - k)
                create_run(fw, suite, dev, "production", days, idx, catalog)
                idx += 1
                if idx % 20 == 0:
                    info(f"{idx} runs created…")

    # ── Batch 2: RC firmware × all suites × staging+ci devices ────────────────
    for i, fw in enumerate(["v1.3.0-rc1", "v2.0.0-rc1"]):
        for j, suite in enumerate(all_suites):
            for k, dev in enumerate(rc_devices):
                days = max(1, 5 - i * 2 + k % 3)
                create_run(fw, suite, dev, "staging", days, idx, catalog)
                idx += 1
                if idx % 20 == 0:
                    info(f"{idx} runs created…")

    # ── Batch 3: in-flight runs ────────────────────────────────────────────────
    in_flight = [
        ("v2.0.0-rc1", "Boot Sequence",       "DEV-DELTA-001",   "ci"),
        ("v2.0.0-rc1", "Sensor Fusion",        "DEV-DELTA-002",   "ci"),
        ("v1.3.0-rc1", "OTA Update",           "DEV-EPSILON-001", "staging"),
        ("v1.3.0-rc1", "Network Connectivity", "DEV-EPSILON-002", "staging"),
        ("v1.2.0",     "Power Management",     "DEV-GAMMA-002",   "staging"),
    ]
    for fw, suite, dev, env in in_flight:
        fw_slug    = fw.replace(".", "-")
        suite_slug = suite.lower().replace(" ", "-")
        run_resp = post("/test-runs", {
            "externalRunId":   f"seed-inflight-{fw_slug}-{suite_slug}-{dev.lower()}",
            "deviceExternalId": dev,
            "firmwareVersion":  fw,
            "suiteName":       suite,
            "environment":     env,
        })
        patch(f"/test-runs/{run_resp['id']}/status", {"status": "RUNNING"})
        idx += 1

    return idx


# ── Entry point ────────────────────────────────────────────────────────────────

def main():
    print(f"\n{BOLD}ETFC Demo Seed Script{RST}")
    print(f"Target: {BASE_URL}\n")

    check_health()

    catalog = seed_suites()
    seed_devices()
    seed_firmware()
    total_runs = seed_runs(catalog)

    total_cases = sum(len(s["cases"]) for s in SUITES)
    print(f"\n{GRN}{BOLD}Done!{RST}  Seeded:")
    print(f"  {len(SUITES)} suites · {total_cases} cases")
    print(f"  {len(DEVICES)} devices · {len(FIRMWARE)} firmware builds")
    print(f"  {total_runs} test runs")


if __name__ == "__main__":
    main()
