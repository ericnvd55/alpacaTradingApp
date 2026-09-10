variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "GCP zone for the database VM"
  type        = string
  default     = "us-central1-a"
}

variable "cluster_name" {
  description = "GKE Autopilot cluster name"
  type        = string
  default     = "trading-cluster"
}

variable "github_repo" {
  description = "GitHub repository in owner/repo format for Workload Identity Federation"
  type        = string
  default     = "ericnvd55/alpacaTradingApp"
}

variable "scheduler_paused" {
  description = "Whether the Mon-Fri 9am-5pm ET scale-up/scale-down Cloud Scheduler jobs are paused. Defaults to paused (app off by default); set false only via the 'Enable Market-Hours Schedule' workflow."
  type        = bool
  default     = true
}
