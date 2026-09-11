data "google_project" "current" {
  project_id = var.project_id
}

locals {
  # Pub/Sub's own service agent needs publish rights on the DLQ topics and
  # subscribe rights on the source subscriptions to forward undeliverable
  # messages after max_delivery_attempts.
  pubsub_service_agent = "service-${data.google_project.current.number}@gcp-sa-pubsub.iam.gserviceaccount.com"

  # Consumer pods (trading-consumers module) don't exist yet — these are the
  # subscriptions they'll pull from once built. See docs/design.md
  # "Pub/Sub design" for the fan-out rationale and delivery semantics.
  order_events_subscribers = ["order-state", "portfolio", "risk", "notify"]
}

# ── Topics ──────────────────────────────────────────────────────────────
resource "google_pubsub_topic" "order_events" {
  name       = "order-events"
  depends_on = [google_project_service.apis]
}

resource "google_pubsub_topic" "market_data" {
  name       = "market-data"
  depends_on = [google_project_service.apis]
}

resource "google_pubsub_topic" "order_events_dlq" {
  name       = "order-events-dlq"
  depends_on = [google_project_service.apis]
}

resource "google_pubsub_topic" "market_data_dlq" {
  name       = "market-data-dlq"
  depends_on = [google_project_service.apis]
}

# ── Publisher IAM: trading-strategy pod (Workload Identity SA) ────────────
resource "google_pubsub_topic_iam_member" "trading_app_publishes_order_events" {
  topic  = google_pubsub_topic.order_events.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.trading_app.email}"
}

resource "google_pubsub_topic_iam_member" "trading_app_publishes_market_data" {
  topic  = google_pubsub_topic.market_data.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.trading_app.email}"
}

# ── Dead-letter forwarding IAM ─────────────────────────────────────────
resource "google_pubsub_topic_iam_member" "dlq_publish_order_events" {
  topic  = google_pubsub_topic.order_events_dlq.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${local.pubsub_service_agent}"
}

resource "google_pubsub_topic_iam_member" "dlq_publish_market_data" {
  topic  = google_pubsub_topic.market_data_dlq.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${local.pubsub_service_agent}"
}

# ── Subscriptions: order-events fan-out (Order state, Portfolio, Risk, Notify) ──
resource "google_pubsub_subscription" "order_events" {
  for_each = toset(local.order_events_subscribers)

  name                       = "${each.value}-order-events-sub"
  topic                      = google_pubsub_topic.order_events.name
  ack_deadline_seconds       = 60 # sized for Autopilot pod cold-start, not just processing time
  message_retention_duration = "604800s" # 7 days — safety net if a consumer is scaled to zero longer than expected
  enable_message_ordering    = true # ordering key = client_order_id, set by the publisher

  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.order_events_dlq.id
    max_delivery_attempts = 5
  }

  depends_on = [google_pubsub_topic_iam_member.dlq_publish_order_events]
}

resource "google_pubsub_subscription_iam_member" "order_events_dlq_subscriber" {
  for_each = google_pubsub_subscription.order_events

  subscription = each.value.name
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:${local.pubsub_service_agent}"
}

# ── Subscription: market-data (Bar store) ──────────────────────────────
resource "google_pubsub_subscription" "bar_store" {
  name                       = "bar-store-market-data-sub"
  topic                      = google_pubsub_topic.market_data.name
  ack_deadline_seconds       = 60
  message_retention_duration = "604800s"
  enable_message_ordering    = true # ordering key = symbol, set by the publisher

  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.market_data_dlq.id
    max_delivery_attempts = 5
  }

  depends_on = [google_pubsub_topic_iam_member.dlq_publish_market_data]
}

resource "google_pubsub_subscription_iam_member" "bar_store_dlq_subscriber" {
  subscription = google_pubsub_subscription.bar_store.name
  role         = "roles/pubsub.subscriber"
  member       = "serviceAccount:${local.pubsub_service_agent}"
}
