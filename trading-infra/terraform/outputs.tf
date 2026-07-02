output "cluster_name" {
  description = "GKE cluster name"
  value       = google_container_cluster.trading.name
}

output "cluster_endpoint" {
  description = "GKE cluster API endpoint"
  value       = google_container_cluster.trading.endpoint
  sensitive   = true
}

output "artifact_registry_url" {
  description = "Docker registry URL — use as image prefix in Helm values"
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.trading_app.repository_id}"
}

output "wif_provider" {
  description = "Set as WIF_PROVIDER in GitHub repository secrets"
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "wif_service_account" {
  description = "Set as WIF_SERVICE_ACCOUNT in GitHub repository secrets"
  value       = google_service_account.github_deploy.email
}

output "postgres_private_ip" {
  description = "Private IP of the PostgreSQL VM — use in Spring datasource URL"
  value       = google_compute_instance.postgres.network_interface[0].network_ip
}
