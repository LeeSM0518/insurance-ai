#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.prod.yml"
ACTIVE_SLOT_FILE=".active-slot"
HEALTH_TIMEOUT=120

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# Nginx 설정 렌더링
render_nginx_conf() {
  local slot="$1"
  ACTIVE_SLOT="$slot" envsubst '${ACTIVE_SLOT} ${DOMAIN}' \
    < nginx-ssl.conf.template > nginx-ssl.conf
}

# 헬스체크 대기
wait_for_health() {
  local slot="$1"
  local container="insurance-server-${slot}"
  local elapsed=0

  while [ $elapsed -lt $HEALTH_TIMEOUT ]; do
    if docker compose -f "$COMPOSE_FILE" --profile "$slot" \
      exec -T "$container" wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then
      log "헬스체크 통과 (${elapsed}초)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  log "ERROR: 헬스체크 실패"
  return 1
}

main() {
  log "========== 롤백 시작 =========="

  if [ ! -f "$ACTIVE_SLOT_FILE" ]; then
    log "ERROR: 활성 슬롯 파일이 없습니다. 롤백 불가."
    exit 1
  fi

  local current_slot
  current_slot=$(cat "$ACTIVE_SLOT_FILE")
  local prev_slot

  if [ "$current_slot" = "blue" ]; then
    prev_slot="green"
  else
    prev_slot="blue"
  fi

  log "현재: ${current_slot} → 롤백 대상: ${prev_slot}"

  # 1. 이전 슬롯 재시작
  log "이전 슬롯 재시작 중... (${prev_slot})"
  docker compose -f "$COMPOSE_FILE" --profile "$prev_slot" start "insurance-server-${prev_slot}"

  # 2. 헬스체크 확인
  if ! wait_for_health "$prev_slot"; then
    log "ERROR: 이전 슬롯 기동 실패. 롤백 중단."
    exit 1
  fi

  # 3. Nginx 전환
  render_nginx_conf "$prev_slot"
  docker compose -f "$COMPOSE_FILE" exec -T insurance-nginx nginx -s reload
  log "Nginx 전환 완료"

  # 4. 현재 슬롯 종료
  log "현재 슬롯 종료 중... (${current_slot})"
  docker compose -f "$COMPOSE_FILE" --profile "$current_slot" stop "insurance-server-${current_slot}"

  # 5. 활성 슬롯 업데이트
  echo "$prev_slot" > "$ACTIVE_SLOT_FILE"

  log "========== 롤백 완료 (활성: ${prev_slot}) =========="
}

main "$@"
