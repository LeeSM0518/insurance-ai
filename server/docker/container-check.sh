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

# 실제 서빙 중인 SSL 인증서의 만료일과 잔여 일수 확인
check_ssl_cert() {
  local domain end_date end_epoch now_epoch days_left

  if [ ! -f .env.production ]; then
    log "  ${RED}.env.production 없음 — 확인 불가${NC}"
    return
  fi
  domain=$(grep -E '^DOMAIN=' .env.production | cut -d= -f2- | tr -d "\"'")
  if [ -z "$domain" ]; then
    log "  ${RED}DOMAIN 미설정 — 확인 불가${NC}"
    return
  fi

  # localhost는 ::1로 먼저 해석될 수 있어 IPv4를 명시한다 (Nginx는 0.0.0.0만 listen)
  end_date=$(echo | openssl s_client -connect "127.0.0.1:443" -servername "$domain" 2>/dev/null \
    | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)
  if [ -z "$end_date" ]; then
    log "  ${RED}인증서를 읽을 수 없음 (Nginx 미기동 또는 443 응답 없음)${NC}"
    return
  fi

  end_epoch=$(date -d "$end_date" +%s 2>/dev/null || echo 0)
  now_epoch=$(date +%s)
  if [ "$end_epoch" = "0" ]; then
    log "  만료일: $end_date ${YELLOW}(잔여 일수 계산 실패)${NC}"
    return
  fi
  days_left=$(( (end_epoch - now_epoch) / 86400 ))

  if [ "$days_left" -lt 0 ]; then
    log "  ${RED}만료됨${NC} — $end_date ($(( -days_left ))일 경과, HTTPS 접속 불가)"
  elif [ "$days_left" -lt 30 ]; then
    log "  ${YELLOW}갱신 필요${NC} — $end_date (잔여 ${days_left}일)"
  else
    log "  ${GREEN}정상${NC} — $end_date (잔여 ${days_left}일)"
  fi
}

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

  # SSL 인증서 (파일이 아니라 실제 서빙 중인 인증서를 확인)
  # 갱신된 파일이 Nginx에 반영되지 않는 경우를 잡기 위해 소켓에서 직접 읽는다
  log "${YELLOW}[SSL 인증서]${NC}"
  check_ssl_cert
  echo ""

  # 디스크 사용량
  log "${YELLOW}[볼륨 디스크 사용량]${NC}"
  docker system df -v 2>/dev/null | grep -E "VOLUME|postgres_data|app_logs|letsencrypt" | head -10 || true

  log "${YELLOW}==========================================${NC}"
}

main "$@"
