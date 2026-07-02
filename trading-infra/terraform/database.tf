resource "google_compute_instance" "postgres" {
  name         = "trading-db"
  machine_type = "e2-micro" # always-free tier
  zone         = var.zone

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
      size  = 30           # 30 GB standard — within always-free limit
      type  = "pd-standard"
    }
  }

  network_interface {
    network    = google_compute_network.vpc.name
    subnetwork = google_compute_subnetwork.subnet.name
    # No access_config block = private IP only; use IAP tunnel for admin SSH
  }

  metadata_startup_script = file("${path.module}/scripts/install-postgres.sh")

  service_account {
    email  = google_service_account.trading_app.email
    scopes = ["cloud-platform"]
  }

  tags = ["postgres"]

  depends_on = [google_project_service.apis]
}
