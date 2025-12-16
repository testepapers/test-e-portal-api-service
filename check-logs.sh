#!/bin/bash

# Script to check Cloud Run logs for the failed deployment

PROJECT_ID="test-e-portal-api-service"
SERVICE_NAME="test-e-portal-api-service-test"
REGION="asia-south2"

echo "🔍 Fetching logs for failed deployment..."
echo "==========================================="
echo ""

# Get the latest revision
REVISION=$(gcloud run revisions list \
  --service=$SERVICE_NAME \
  --region=$REGION \
  --project=$PROJECT_ID \
  --format="value(name)" \
  --limit=1)

echo "Latest revision: $REVISION"
echo ""

# Fetch logs from the last 10 minutes
echo "📋 Container logs:"
echo "==================="
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=$SERVICE_NAME AND resource.labels.revision_name=$REVISION" \
  --project=$PROJECT_ID \
  --limit=50 \
  --format="table(timestamp,textPayload,jsonPayload.message)" \
  --freshness=10m

echo ""
echo "🔗 View full logs in console:"
echo "https://console.cloud.google.com/run/detail/$REGION/$SERVICE_NAME/logs?project=$PROJECT_ID"
