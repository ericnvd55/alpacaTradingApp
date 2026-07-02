resource "google_compute_network" "vpc" {
  name                    = "trading-vpc"
  auto_create_subnetworks = false
  depends_on              = [google_project_service.apis]
}

resource "google_compute_subnetwork" "subnet" {
  name          = "trading-subnet"
  ip_cidr_range = "10.0.0.0/24"
  region        = var.region
  network       = google_compute_network.vpc.id

  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = "10.1.0.0/16"
  }

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = "10.2.0.0/20"
  }
}

resource "google_compute_firewall" "allow_postgres_from_pods" {
  name    = "allow-postgres-from-pods"
  network = google_compute_network.vpc.name

  allow {
    protocol = "tcp"
    ports    = ["5432", "6432"] # PostgreSQL + PgBouncer
  }

  source_ranges = ["10.1.0.0/16"] # GKE pod CIDR
  target_tags   = ["postgres"]
}

resource "google_compute_firewall" "allow_iap_ssh" {
  name    = "allow-iap-ssh"
  network = google_compute_network.vpc.name

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["35.235.240.0/20"] # IAP tunnel range
  target_tags   = ["postgres"]
}
