CREATE TABLE release_state (
  environment             VARCHAR(100) PRIMARY KEY,
  current_system_version  BIGINT       NOT NULL
);

CREATE TABLE deployment (
  environment      VARCHAR(100) NOT NULL,
  system_version   BIGINT       NOT NULL,
  service_name     VARCHAR(255) NOT NULL,
  service_version  INT          NOT NULL,
  deployed_at      TIMESTAMP    NOT NULL,
  PRIMARY KEY (environment, system_version)
);

CREATE INDEX idx_deployment_service ON deployment (environment, service_name, system_version DESC);
