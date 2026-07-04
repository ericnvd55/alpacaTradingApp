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

# Grants the scheduler's Google identity permission to patch the deployment's
# scale subresource. GKE's built-in authenticator maps a Google-issued OIDC
# token's email claim to this Kubernetes "User" subject.
resource "kubernetes_role" "scheduler_scale" {
  metadata {
    name      = "trading-scheduler-scale"
    namespace = "default"
  }

  rule {
    api_groups = ["apps"]
    resources  = ["deployments/scale"]
    verbs      = ["get", "patch", "update"]
  }
}

resource "kubernetes_role_binding" "scheduler_scale" {
  metadata {
    name      = "trading-scheduler-scale"
    namespace = "default"
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role.scheduler_scale.metadata[0].name
  }

  subject {
    kind      = "User"
    name      = google_service_account.scheduler.email
    api_group = "rbac.authorization.k8s.io"
  }
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

    oidc_token {
      service_account_email = google_service_account.scheduler.email
    }
  }

  depends_on = [google_project_service.apis, google_container_cluster.trading, kubernetes_role_binding.scheduler_scale]
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

    oidc_token {
      service_account_email = google_service_account.scheduler.email
    }
  }

  depends_on = [google_project_service.apis, google_container_cluster.trading, kubernetes_role_binding.scheduler_scale]
}
