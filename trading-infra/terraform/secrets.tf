resource "google_secret_manager_secret" "alpaca_api_key" {
  secret_id = "ALPACA_API_KEY"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret" "alpaca_api_secret" {
  secret_id = "ALPACA_API_SECRET"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

# Secret values are never stored in Terraform state.
# After terraform apply, populate them once:
#   gcloud secrets versions add ALPACA_API_KEY --data-file=<(echo -n "$ALPACA_API_KEY")
#   gcloud secrets versions add ALPACA_API_SECRET --data-file=<(echo -n "$ALPACA_API_SECRET")
