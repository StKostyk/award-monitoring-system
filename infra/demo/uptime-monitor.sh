#!/usr/bin/env bash
# Creates or updates the external uptime check of the demo server: a Route 53 HTTPS health check that expects the
# discovery document (string "issuer"), a CloudWatch alarm on its status and an SNS topic that mails the alarm and
# the recovery.
# Usage: uptime-monitor.sh <demo-host> <alert-email>   (needs the AWS CLI with the account credentials)
# Running it again with the same arguments changes nothing; a new e-mail address adds a subscription. A health check
# of the same address with another type or interval (which Route 53 cannot update) is replaced.
set -euo pipefail
# Git Bash on Windows would rewrite the resource path into a Windows path.
export MSYS_NO_PATHCONV=1

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <demo-host> <alert-email>" >&2
  exit 2
fi

host="$1"
email="$2"
fqdn="${host}.sslip.io"
path="/.well-known/openid-configuration"
search="issuer"
type="HTTPS_STR_MATCH"
interval=30
threshold=3
name="award-demo-uptime"
# Route 53 publishes health check metrics only in us-east-1, so the alarm and its topic live there.
region="us-east-1"

topic_arn=$(aws sns create-topic --region "$region" --name "$name" --query TopicArn --output text)

if ! aws sns list-subscriptions-by-topic --region "$region" --topic-arn "$topic_arn" \
    --query 'Subscriptions[].Endpoint' --output text | tr '\t' '\n' | grep -Fxq "$email"; then
  aws sns subscribe --region "$region" --topic-arn "$topic_arn" --protocol email \
    --notification-endpoint "$email" > /dev/null
  echo "Subscription created: confirm it through the link AWS mails to the alert address"
fi

existing=$(aws route53 list-health-checks --output text \
  --query "HealthChecks[?HealthCheckConfig.FullyQualifiedDomainName=='${fqdn}'].[Id,HealthCheckConfig.Type,HealthCheckConfig.RequestInterval]")
health_check_id=""
stale_ids=()
while read -r id existing_type existing_interval; do
  [ -n "${id:-}" ] || continue
  if [ -z "$health_check_id" ] && [ "$existing_type" = "$type" ] && [ "$existing_interval" = "$interval" ]; then
    health_check_id="$id"
  else
    stale_ids+=("$id")
  fi
done <<< "$existing"

if [ -n "$health_check_id" ]; then
  aws route53 update-health-check --health-check-id "$health_check_id" --resource-path "$path" \
    --search-string "$search" --failure-threshold "$threshold" --enable-sni > /dev/null
else
  config=$(cat <<EOF
{"Type": "${type}", "FullyQualifiedDomainName": "${fqdn}", "Port": 443, "ResourcePath": "${path}",
 "SearchString": "${search}", "RequestInterval": ${interval}, "FailureThreshold": ${threshold}, "EnableSNI": true}
EOF
)
  health_check_id=$(aws route53 create-health-check --caller-reference "${name}-${host}-$(date +%s)" \
    --health-check-config "$config" --query HealthCheck.Id --output text)
fi
aws route53 change-tags-for-resource --resource-type healthcheck --resource-id "$health_check_id" \
  --add-tags Key=Name,Value="$name"

aws cloudwatch put-metric-alarm --region "$region" --alarm-name "$name" \
  --alarm-description "https://${fqdn}${path} does not return the discovery document (health check ${health_check_id})" \
  --namespace AWS/Route53 --metric-name HealthCheckStatus \
  --dimensions Name=HealthCheckId,Value="$health_check_id" \
  --statistic Minimum --period 60 --evaluation-periods 2 --datapoints-to-alarm 2 \
  --comparison-operator LessThanThreshold --threshold 1 --treat-missing-data missing \
  --alarm-actions "$topic_arn" --ok-actions "$topic_arn"

for id in "${stale_ids[@]}"; do
  aws route53 delete-health-check --health-check-id "$id"
  echo "Replaced health check ${id}"
done

echo "Health check ${health_check_id} on https://${fqdn}${path}; alarm ${name} in ${region} mails ${email}"
