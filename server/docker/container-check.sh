#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.prod.yml"
ACTIVE_SLOT_FILE=".active-slot"

# 색상 출력
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "$*"; }

main() {
  log "${YELLOW}========== 컨테이너 상태 확인 ==========${NC}"
  echo ""

  # 활성 슬롯
  if [ -f "$ACTIVE_SLOT_FILE" ]; then
    local active_slot
    active_slot=$(cat "$ACTIVE_SLOT_FILE")
    log "활성 슬롯: ${GREEN}${active_slot}${NC}"
  else
    log "활성 슬롯: ${RED}미설정${NC}"
  fi
  echo ""

  # Docker Compose 서비스 상태
  log "${YELLOW}[서비스 상태]${NC}"
  docker compose -f "$COMPOSE_FILE" --profile blue --profile green ps -a
  echo ""

  # DB 헬스체크
  log "${YELLOW}[DB 상태]${NC}"
  if docker compose -f "$COMPOSE_FILE" exec -T insurance-database pg_isready -U insurance 2>/dev/null; then
    log "  PostgreSQL: ${GREEN}정상${NC}"
  else
    log "  PostgreSQL: ${RED}비정상${NC}"
  fi
  echo ""

  # 앱 헬스체크
  log "${YELLOW}[앱 헬스체크]${NC}"
  for slot in blue green; do
    local container="insurance-server-${slot}"
    if docker compose -f "$COMPOSE_FILE" --profile "$slot" ps --status running "$container" 2>/dev/null | grep -q "$container"; then
      local health
      health=$(docker compose -f "$COMPOSE_FILE" --profile "$slot" \
        exec -T "$container" wget -qO- http://localhost:8080/actuator/health 2>/dev/null || echo "unreachable")
      log "  ${container}: ${GREEN}running${NC} — ${health}"
    else
      log "  ${container}: ${RED}stopped${NC}"
    fi
  done
  echo ""

  # Nginx 상태
  log "${YELLOW}[Nginx 상태]${NC}"
  if docker compose -f "$COMPOSE_FILE" ps --status running insurance-nginx 2>/dev/null | grep -q insurance-nginx; then
    log "  Nginx: ${GREEN}running${NC}"
    docker compose -f "$COMPOSE_FILE" exec -T insurance-nginx nginx -t 2>&1 | sed 's/^/  /'
  else
    log "  Nginx: ${RED}stopped${NC}"
  fi
  echo ""

  # 디스크 사용량
  log "${YELLOW}[볼륨 디스크 사용량]${NC}"
  docker system df -v 2>/dev/null | grep -E "VOLUME|postgres_data|app_logs|letsencrypt" | head -10 || true

  log "${YELLOW}==========================================${NC}"
}

main "$@"
