#!/bin/bash
set -euo pipefail

PACKAGE_NAME="@insurance-ai/api-client"
OUTPUT_DIR="../api-client"

echo "=== Insurance AI API Client Generator ==="

# [1/7] DB 컨테이너 확인
if command -v docker >/dev/null 2>&1; then
  echo "[1/7] Checking database..."
  if [ ! "$(docker ps -q -f name=insurance-database)" ]; then
    echo "Starting database container..."
    docker compose -f ../docker-compose.yml up -d insurance-database
    sleep 5
  else
    echo "Database container is already running."
  fi
fi

# [2/7] 환경 변수 로드
if [ -f .env ]; then
  echo "[2/7] Loading environment variables..."
  set -a
  source .env
  set +a
fi

# 기존 openapi.json 파일 먼저 제거 (캐시 문제 방지)
if [ -f "$OUTPUT_DIR/openapi.json" ]; then
  echo "Removing existing openapi.json..."
  rm -f "$OUTPUT_DIR/openapi.json"
fi

# [3/7] OpenAPI JSON 생성 (8888 포트 사용 - 개발 서버와 충돌 방지)
echo "[3/7] Generating OpenAPI spec..."
SPRING_PROFILES_ACTIVE=local,openapi-gen ./gradlew generateOpenApiDocs --info --no-configuration-cache

# 생성된 파일 확인
if [ ! -f "build/openapi/openapi.json" ]; then
  echo "[ERROR] OpenAPI spec not generated at build/openapi/openapi.json"
  exit 1
fi

OPENAPI_SIZE=$(wc -c < "build/openapi/openapi.json" | tr -d ' ')
if [ "$OPENAPI_SIZE" -lt 100 ]; then
  echo "[ERROR] OpenAPI spec is too small (${OPENAPI_SIZE} bytes). Check server springdoc configuration."
  echo "Content: $(cat build/openapi/openapi.json)"
  exit 1
fi

echo "OpenAPI spec generated successfully (${OPENAPI_SIZE} bytes)"

# [4/7] 출력 디렉토리 설정
echo "[4/7] Setting up output directory..."

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# .openapi-generator-ignore
cat <<EOF > "$OUTPUT_DIR/.openapi-generator-ignore"
.gitignore
.npmignore
git_push.sh
EOF

# package.json
cat <<EOF > "$OUTPUT_DIR/package.json"
{
  "name": "$PACKAGE_NAME",
  "version": "0.0.0",
  "type": "module",
  "main": "dist/index.js",
  "module": "dist/index.js",
  "types": "dist/index.d.ts",
  "exports": {
    ".": {
      "import": "./dist/index.js",
      "require": "./dist/index.js",
      "types": "./dist/index.d.ts"
    }
  },
  "scripts": {
    "build": "rm -rf dist && tsc -p tsconfig.json"
  },
  "devDependencies": {
    "typescript": "^5.8.2"
  },
  "dependencies": {
    "axios": "^1.8.2"
  }
}
EOF

# tsconfig.json
cat <<EOF > "$OUTPUT_DIR/tsconfig.json"
{
  "compilerOptions": {
    "target": "ESNext",
    "module": "ESNext",
    "moduleResolution": "node",
    "declaration": true,
    "declarationMap": true,
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "outDir": "dist",
    "rootDir": "src",
    "baseUrl": "src"
  },
  "include": ["src"],
  "exclude": ["node_modules", "dist"]
}
EOF

# OpenAPI JSON 복사
cp build/openapi/openapi.json "$OUTPUT_DIR/openapi.json"

# [5/7] TypeScript 클라이언트 생성
echo "[5/7] Generating TypeScript client..."
docker run --rm \
  -v "$(pwd)/$OUTPUT_DIR":/local \
  openapitools/openapi-generator-cli generate \
  -i /local/openapi.json \
  -g typescript-axios \
  -o /local/src \
  --additional-properties=withSeparateModelsAndApi=true,apiPackage=apis,modelPackage=models,useSingleRequestParameter=true

# [6/7] 빌드
echo "[6/7] Building TypeScript client..."
cd "$OUTPUT_DIR"
npm install
npm run build

# [7/7] frontend에 API 클라이언트 설치
echo "[7/7] Installing API client in frontend..."
cd ../frontend
npm install ../api-client

echo ""
echo "=== Generation Complete ==="
echo "Output: $OUTPUT_DIR/dist"
echo "API client installed in frontend/"
