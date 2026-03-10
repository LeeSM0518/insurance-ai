#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.prod.yml"
ACTIVE_SLOT_FILE=".active-slot"
HEALTH_TIMEOUT=120
DRAIN_TIMEOUT=30

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

# 현재 활성 슬롯 확인
get_active_slot() {
  if [ -f "$ACTIVE_SLOT_FILE" ]; then
    cat "$ACTIVE_SLOT_FILE"
  else
    echo "none"
  fi
}

# Nginx 설정 렌더링 (upstream 전환)
render_nginx_conf() {
  local slot="$1"
  ACTIVE_SLOT="$slot" envsubst '${ACTIVE_SLOT} ${DOMAIN}' \
    < nginx-ssl.conf.template > nginx-ssl.conf
  log "Nginx 설정 렌더링 완료 (upstream → insurance-server-${slot})"
}

# Nginx 리로드
reload_nginx() {
  docker compose -f "$COMPOSE_FILE" exec -T insurance-nginx nginx -s reload
  log "Nginx 리로드 완료"
}

# 헬스체크 대기
wait_for_health() {
  local slot="$1"
  local container="insurance-server-${slot}"
  local elapsed=0

  log "헬스체크 대기 중... (${container}, 최대 ${HEALTH_TIMEOUT}초)"

  while [ $elapsed -lt $HEALTH_TIMEOUT ]; do
    if docker compose -f "$COMPOSE_FILE" --profile "$slot" \
      exec -T "$container" wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then
      log "헬스체크 통과 (${elapsed}초)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  log "ERROR: 헬스체크 실패 (${HEALTH_TIMEOUT}초 초과)"
  return 1
}

# 메인 배포 프로세스
main() {
  log "========== 배포 시작 =========="

  # 1. 현재 활성 슬롯 확인
  local current_slot
  current_slot=$(get_active_slot)
  local new_slot

  if [ "$current_slot" = "blue" ]; then
    new_slot="green"
  else
    new_slot="blue"
  fi

  log "현재 슬롯: ${current_slot} → 새 슬롯: ${new_slot}"

  # 2. Docker 이미지 빌드
  log "Docker 이미지 빌드 중..."
  docker compose -f "$COMPOSE_FILE" --profile "$new_slot" build "insurance-server-${new_slot}"

  # 3. DB 컨테이너 확인 (없으면 기동)
  if ! docker compose -f "$COMPOSE_FILE" ps --status running insurance-database | grep -q insurance-database; then
    log "DB 컨테이너 기동 중..."
    docker compose -f "$COMPOSE_FILE" up -d insurance-database
    sleep 5
  fi

  # 4. 새 슬롯 기동
  log "새 슬롯 기동 중... (${new_slot})"
  docker compose -f "$COMPOSE_FILE" --profile "$new_slot" up -d "insurance-server-${new_slot}"

  # 5. 헬스체크 대기
  if ! wait_for_health "$new_slot"; then
    log "ERROR: 새 슬롯 기동 실패. 롤백합니다."
    docker compose -f "$COMPOSE_FILE" --profile "$new_slot" stop "insurance-server-${new_slot}"
    exit 1
  fi

  # 6. Nginx upstream 전환
  render_nginx_conf "$new_slot"

  # Nginx 컨테이너가 없으면 기동, 있으면 리로드
  if ! docker compose -f "$COMPOSE_FILE" ps --status running insurance-nginx | grep -q insurance-nginx; then
    log "Nginx 컨테이너 기동 중..."
    docker compose -f "$COMPOSE_FILE" up -d insurance-nginx insurance-certbot
  else
    reload_nginx
  fi

  # 7. Connection draining
  if [ "$current_slot" != "none" ]; then
    log "Connection draining 대기 중... (${DRAIN_TIMEOUT}초)"
    sleep "$DRAIN_TIMEOUT"

    # 8. 기존 슬롯 종료 (컨테이너는 유지 → 롤백용)
    log "기존 슬롯 종료 중... (${current_slot})"
    docker compose -f "$COMPOSE_FILE" --profile "$current_slot" stop "insurance-server-${current_slot}"
  fi

  # 9. 활성 슬롯 파일 업데이트
  echo "$new_slot" > "$ACTIVE_SLOT_FILE"

  log "========== 배포 완료 (활성: ${new_slot}) =========="
}

main "$@"
