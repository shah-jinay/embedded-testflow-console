-- Additional indexes to support query API filter columns.

-- Test run filters: environment, individual status lookup
CREATE INDEX idx_test_run_environment ON test_run (environment);
CREATE INDEX idx_test_run_status ON test_run (status);
CREATE INDEX idx_test_run_created_at ON test_run (created_at DESC);

-- Device filters used in test-run board_revision and device-list queries
CREATE INDEX idx_device_board_revision ON device (board_revision);
CREATE INDEX idx_device_environment ON device (environment);
CREATE INDEX idx_device_status ON device (status);

-- Firmware lookup used in dashboard failure-by-firmware aggregation
CREATE INDEX idx_firmware_build_is_rc ON firmware_build (is_release_candidate);
