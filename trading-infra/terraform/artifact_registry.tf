resource "google_artifact_registry_repository" "trading_app" {
  location      = var.region
  repository_id = "trading-app"
  format        = "DOCKER"
  description   = "Docker images for the trading application"

  depends_on = [google_project_service.apis]
}
