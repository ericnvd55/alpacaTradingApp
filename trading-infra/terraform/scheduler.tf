# Service account for Cloud Scheduler to call the Kubernetes API
resource "google_service_account" "scheduler" {
  account_id   = "trading-scheduler"
  display_name = "Trading App Scheduler"
}

resource "google_project_iam_member" "scheduler_gke_developer" {
  project = var.project_id
  role    = "roles/container.developer"
  member  = "serviceAccount:${google_service_account.scheduler.email}"
}

locals {
  k8s_scale_path = "/apis/apps/v1/namespaces/default/deployments/trading-strategy/scale"
}

# Scale to 0 after market close — 9 PM UTC (5 PM ET), Mon–Fri
resource "google_cloud_scheduler_job" "scale_down" {
  name             = "trading-scale-down"
  description      = "Scale trading pods to 0 after market close"
  schedule         = "0 21 * * 1-5"
  time_zone        = "UTC"
  attempt_deadline = "30s"

  http_target {
    uri         = "https://${google_container_cluster.trading.endpoint}${local.k8s_scale_path}"
    http_method = "PATCH"
    body        = base64encode(jsonencode({ spec = { replicas = 0 } }))

    headers = {
      "Content-Type" = "application/merge-patch+json"
    }

    oauth_token {
      service_account_email = google_service_account.scheduler.email
      scope                 = "https://www.googleapis.com/auth/cloud-platform"
    }
  }

  depends_on = [google_project_service.apis, google_container_cluster.trading]
}

# Scale to 1 before market open — 1 PM UTC (9 AM ET), Mon–Fri
resource "google_cloud_scheduler_job" "scale_up" {
  name             = "trading-scale-up"
  description      = "Scale trading pods to 1 before market open"
  schedule         = "0 13 * * 1-5"
  time_zone        = "UTC"
  attempt_deadline = "30s"

  http_target {
    uri         = "https://${google_container_cluster.trading.endpoint}${local.k8s_scale_path}"
    http_method = "PATCH"
    body        = base64encode(jsonencode({ spec = { replicas = 1 } }))

    headers = {
      "Content-Type" = "application/merge-patch+json"
    }

    oauth_token {
      service_account_email = google_service_account.scheduler.email
      scope                 = "https://www.googleapis.com/auth/cloud-platform"
    }
  }

  depends_on = [google_project_service.apis, google_container_cluster.trading]
}
