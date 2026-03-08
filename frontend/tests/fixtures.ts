import type { Page } from '@playwright/test';

// Mock 데이터 — 테스트용

const MOCK_SESSIONS = [
  {
    id: 'session-001',
    title: '삼성생명 암보험 면책기간',
    lastMessagePreview: '면책기간은 계약일로부터 90일이 지난 날의 다음 날부터 시작됩니다.',
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
  },
  {
    id: 'session-002',
    title: '실손보험 4세대 보장범위',
    lastMessagePreview: '4세대 실손보험은 급여와 비급여를 구분하여 보장합니다.',
    createdAt: '2026-01-01T08:00:00Z',
    updatedAt: '2026-01-01T08:00:00Z',
  },
  {
    id: 'session-003',
    title: '치아보험 임플란트 보장',
    lastMessagePreview: '임플란트는 보통 1~2년의 면책기간이 적용됩니다.',
    createdAt: '2025-12-30T08:00:00Z',
    updatedAt: '2025-12-30T08:00:00Z',
  },
];

const MOCK_SOURCES = [
  {
    articleId: 'art-001',
    insuranceCompany: '삼성생명',
    productId: 'prod-001',
    productName: '무배당 삼성 암보험',
    articleNumber: '제5조',
    articleTitle: '보장 제외',
    relevanceScore: 0.92,
    dataSource: 'ASSOCIATION',
    dataSourceId: 'ds-001',
  },
  {
    articleId: 'art-002',
    insuranceCompany: '한화생명',
    productId: 'prod-002',
    productName: '한화 무배당 건강보험',
    articleNumber: '제3조',
    articleTitle: '보장 내용',
    relevanceScore: 0.87,
    dataSource: 'ASSOCIATION',
    dataSourceId: 'ds-002',
  },
  {
    articleId: 'art-003',
    insuranceCompany: '현대해상',
    productId: 'prod-003',
    productName: '현대해상 암보험 플러스',
    articleNumber: '제7조',
    articleTitle: '보험금을 지급하지 않는 사유',
    relevanceScore: 0.74,
    dataSource: 'PRODUCT_SUMMARY',
    dataSourceId: 'ds-003',
  },
];

const MOCK_MESSAGES: Record<string, unknown[]> = {
  'session-001': [
    {
      id: 'msg-001',
      role: 'USER',
      content: '삼성생명 암보험의 면책기간이 어떻게 되나요?',
      sources: [],
      createdAt: '2026-01-01T10:00:00Z',
    },
    {
      id: 'msg-002',
      role: 'ASSISTANT',
      content:
        '삼성생명 무배당 암보험의 **면책기간**에 대해 설명드리겠습니다.\n\n## 면책기간\n\n면책기간은 계약일로부터 **90일**이 지난 날의 다음 날부터 시작됩니다 [1]. 이 기간 중 발생한 암은 보장하지 않습니다.\n\n### 주요 내용\n\n- **일반암**: 90일 면책기간 적용\n- **소액암** (갑상선암, 기타피부암 등): 동일하게 90일 적용 [2]\n- 면책기간 중 암 진단 시 보험료를 환급하고 계약이 소멸됩니다\n\n> 참고: 면책기간은 대부분의 암보험에 공통적으로 적용되는 사항입니다 [3].',
      sources: MOCK_SOURCES,
      suggestedQuestions: [
        '소액암과 일반암의 보장 차이는 뭔가요?',
        '면책기간 중 암 진단 시 어떻게 되나요?',
        '다른 보험사의 면책기간도 90일인가요?',
      ],
      createdAt: '2026-01-01T10:00:01Z',
    },
  ],
  'session-002': [
    {
      id: 'msg-003',
      role: 'USER',
      content: '4세대 실손보험이 뭔가요?',
      sources: [],
      createdAt: '2026-01-01T08:00:00Z',
    },
    {
      id: 'msg-004',
      role: 'ASSISTANT',
      content:
        '**4세대 실손보험**에 대해 설명드리겠습니다.\n\n4세대 실손보험은 2021년 7월부터 판매된 새로운 형태의 실손의료보험입니다.\n\n### 주요 특징\n\n1. **급여/비급여 분리**: 급여 항목과 비급여 항목을 별도 특약으로 구분\n2. **비급여 자기부담률 상향**: 비급여 항목의 자기부담률이 30%로 상향\n3. **재가입 주기**: 5년마다 재가입 (1~3세대는 15년)',
      sources: [],
      suggestedQuestions: ['3세대와 4세대의 차이점은?', '비급여 자기부담률이 높아지면 불리한 건가요?'],
      createdAt: '2026-01-01T08:00:01Z',
    },
  ],
  'session-003': [
    {
      id: 'msg-005',
      role: 'USER',
      content: '치아보험에서 임플란트도 보장되나요?',
      sources: [],
      createdAt: '2025-12-30T08:00:00Z',
    },
    {
      id: 'msg-006',
      role: 'ASSISTANT',
      content:
        '네, **치아보험**에서 임플란트 보장이 가능합니다.\n\n### 보장 조건\n\n- 보통 **1~2년의 면책기간**이 적용됩니다\n- 보장 한도는 보험사마다 다르며, 일반적으로 개당 50~100만원 수준입니다\n- 연간 보장 횟수 제한이 있을 수 있습니다',
      sources: [],
      suggestedQuestions: ['면책기간이 가장 짧은 치아보험은?', '임플란트 vs 브릿지 보장 차이는?'],
      createdAt: '2025-12-30T08:00:01Z',
    },
  ],
};

const MOCK_SEARCH_RESULTS = {
  query: '암보험',
  totalCount: 5,
  page: 0,
  size: 20,
  hasNext: false,
  results: [
    {
      id: 'art-001',
      insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000001', name: '삼성생명', type: 'LIFE' },
      productId: 'prod-001',
      productName: '무배당 삼성 암보험',
      contentType: 'EXEMPTION',
      articleNumber: '제5조',
      articleTitle: '보장 제외',
      content: '이 보험의 보장개시일은 계약일로부터 90일이 지난 날의 다음 날로 합니다.',
      highlight: '이 보험의 <mark>보장개시일</mark>은 계약일로부터 <mark>90일</mark>이 지난 날의 다음 날로 합니다. 이 기간을 <mark>면책기간</mark>이라 하며...',
      relevanceScore: 0.92,
      dataSource: 'ASSOCIATION',
      supplementary: { hasSurrenderValues: true, hasPremiumExamples: true, hasPriceIndices: false },
    },
    {
      id: 'art-002',
      insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000002', name: '한화생명', type: 'LIFE' },
      productId: 'prod-002',
      productName: '한화 무배당 건강보험',
      contentType: 'COVERAGE',
      articleNumber: '제3조',
      articleTitle: '보장 내용',
      content: '피보험자가 보험기간 중 암으로 진단 확정된 경우 암진단금을 지급합니다.',
      highlight: '피보험자가 보험기간 중 <mark>암</mark>으로 진단 확정된 경우 암진단금을 지급합니다.',
      relevanceScore: 0.87,
      dataSource: 'ASSOCIATION',
      supplementary: { hasSurrenderValues: true, hasPremiumExamples: false, hasPriceIndices: false },
    },
    {
      id: 'art-003',
      insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000004', name: '현대해상', type: 'NON_LIFE' },
      productId: 'prod-003',
      productName: '현대해상 암보험 플러스',
      contentType: 'EXEMPTION',
      articleNumber: '제7조',
      articleTitle: '보험금을 지급하지 않는 사유',
      content: '회사는 다음 중 하나에 해당하는 사유로 보험금 지급사유가 발생한 때에는 보험금을 지급하지 않습니다.',
      highlight: '회사는 다음 중 하나에 해당하는 사유로 보험금 지급사유가 발생한 때에는 <mark>보험금</mark>을 지급하지 않습니다.',
      relevanceScore: 0.74,
      dataSource: 'PRODUCT_SUMMARY',
      supplementary: { hasSurrenderValues: false, hasPremiumExamples: true, hasPriceIndices: true },
    },
    {
      id: 'art-004',
      insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000003', name: '교보생명', type: 'LIFE' },
      productId: 'prod-004',
      productName: '교보 무배당 실속 암보험',
      contentType: 'SPECIAL_TERMS',
      articleNumber: '특약 제2조',
      articleTitle: '소액암 보장 특별약관',
      content: '갑상선암, 기타피부암, 제자리암, 경계성종양의 경우 소액암으로 분류하며, 소액암진단금을 별도로 지급합니다.',
      highlight: '<mark>갑상선암</mark>, 기타피부암, 제자리암, 경계성종양의 경우 소액암으로 분류하며, 소액암진단금을 별도로 지급합니다.',
      relevanceScore: 0.65,
      dataSource: 'FULL_POLICY',
      supplementary: { hasSurrenderValues: false, hasPremiumExamples: false, hasPriceIndices: false },
    },
    {
      id: 'art-005',
      insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000005', name: 'DB손해보험', type: 'NON_LIFE' },
      productId: 'prod-005',
      productName: 'DB 다이렉트 암보험',
      contentType: 'COVERAGE_DETAIL',
      articleNumber: '별표1',
      articleTitle: '담보별 보장내용',
      content: '일반암 진단금 3,000만원, 소액암 진단금 300만원을 보험기간 내 최초 1회에 한하여 지급합니다.',
      highlight: '일반<mark>암</mark> 진단금 3,000만원, 소액<mark>암</mark> 진단금 300만원을 보험기간 내 최초 1회에 한하여 지급합니다.',
      relevanceScore: 0.55,
      dataSource: 'ASSOCIATION',
      supplementary: { hasSurrenderValues: true, hasPremiumExamples: true, hasPriceIndices: true },
    },
  ],
};

const MOCK_POLICY_ARTICLE = {
  id: 'art-001',
  insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000001', name: '삼성생명', type: 'LIFE' },
  productId: 'prod-001',
  productName: '무배당 삼성 암보험',
  articleNumber: '제5조',
  articleTitle: '보장 제외',
  content: `① 회사는 이 계약의 보장개시일 이전에 암으로 진단 확정된 경우에는 보험금을 지급하지 아니하며, 이 경우 보험료를 납입한 날로부터 계약이 무효가 됩니다.\n\n② 이 보험의 보장개시일은 계약일로부터 그 날을 포함하여 90일이 지난 날의 다음 날로 합니다. 이 기간을 면책기간(免責期間)이라 합니다.\n\n③ 제2항에도 불구하고 계약이 자동갱신된 경우에는 자동갱신일을 보장개시일로 합니다.\n\n④ 면책기간 중에 암으로 진단 확정된 경우에는 이 계약을 무효로 하며, 이미 납입한 보험료를 보험계약자에게 돌려드립니다.`,
  contentType: '면책사항',
  sectionType: '주계약',
  effectiveDate: '2024-01-01',
  dataSource: 'ASSOCIATION',
  dataSourceId: 'ds-001',
  hasPdfSource: true,
  relatedArticles: [
    { id: 'art-006', articleNumber: '제3조', articleTitle: '보장 내용', contentType: 'COVERAGE' },
    { id: 'art-007', articleNumber: '제6조', articleTitle: '보험금 지급사유', contentType: 'COVERAGE' },
    { id: 'art-008', articleNumber: '제10조', articleTitle: '해약환급금', contentType: 'COVERAGE' },
  ],
};

const MOCK_POLICY_ARTICLES_MAP: Record<string, typeof MOCK_POLICY_ARTICLE> = {
  'art-001': MOCK_POLICY_ARTICLE,
  'art-002': {
    id: 'art-002',
    insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000002', name: '한화생명', type: 'LIFE' },
    productId: 'prod-002',
    productName: '한화 무배당 건강보험',
    articleNumber: '제3조',
    articleTitle: '보장 내용',
    content: '① 회사는 피보험자가 보험기간 중에 암으로 진단 확정되었을 때 암진단보험금을 지급합니다.',
    contentType: '보장 내용',
    sectionType: '주계약',
    effectiveDate: '2024-03-01',
    dataSource: 'ASSOCIATION',
    dataSourceId: 'ds-002',
    hasPdfSource: true,
    relatedArticles: [
      { id: 'art-009', articleNumber: '제5조', articleTitle: '보험금을 지급하지 않는 사유', contentType: 'EXEMPTION' },
    ],
  },
  'art-003': {
    id: 'art-003',
    insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000003', name: '현대해상', type: 'NON_LIFE' },
    productId: 'prod-003',
    productName: '현대해상 암보험 플러스',
    articleNumber: '제7조',
    articleTitle: '보험금을 지급하지 않는 사유',
    content: '① 회사는 다음 중 어느 하나의 사유로 보험금 지급사유가 발생한 경우에는 보험금을 지급하지 않습니다.',
    contentType: '면책사항',
    sectionType: '주계약',
    effectiveDate: '2024-06-01',
    dataSource: 'PRODUCT_SUMMARY',
    dataSourceId: 'ds-003',
    hasPdfSource: true,
    relatedArticles: [],
  },
};

const MOCK_SURRENDER_VALUES = {
  productId: 'prod-001',
  values: [
    { elapsedMonths: 12, premiumPaid: 360000, surrenderValue: 108000, surrenderRate: 30.0 },
    { elapsedMonths: 24, premiumPaid: 720000, surrenderValue: 302400, surrenderRate: 42.0 },
    { elapsedMonths: 36, premiumPaid: 1080000, surrenderValue: 572400, surrenderRate: 53.0 },
  ],
};

const MOCK_PREMIUM_EXAMPLES = {
  productId: 'prod-001',
  examples: [
    { gender: 'M', age: 40, insurancePeriod: '80세 만기', paymentPeriod: '20년납', premium: 30000 },
    { gender: 'F', age: 40, insurancePeriod: '80세 만기', paymentPeriod: '20년납', premium: 27500 },
  ],
};

const MOCK_PRICE_INDICES = {
  productId: 'prod-001',
  indices: [
    { gender: 'M', indexType: '표준체', indexValue: 1.0, baseDate: '2024-01-01' },
    { gender: 'F', indexType: '표준체', indexValue: 0.92, baseDate: '2024-01-01' },
  ],
};

// SSE 스트리밍 Mock 응답 생성
const MOCK_STREAMING_RESPONSE = `암보험의 면책기간에 대해 설명드리겠습니다.\n\n## 면책기간이란?\n\n면책기간은 보험 가입 후 일정 기간 동안 보험금을 지급하지 않는 기간을 말합니다 [1].\n\n### 주요 내용\n\n1. **일반암**: 대부분 90일의 면책기간이 적용됩니다 [1]\n2. **갑상선암/기타피부암**: 면책기간 없이 바로 보장되는 경우가 많습니다 [2]`;

function buildSseBody(sources = MOCK_SOURCES): string {
  const events = [
    `data: ${JSON.stringify({ type: 'searching', data: { message: '검색 중...' } })}\n\n`,
    `data: ${JSON.stringify({ type: 'sources_found', data: { sources } })}\n\n`,
    `data: ${JSON.stringify({ type: 'generating', data: { message: '답변 생성 중...' } })}\n\n`,
    ...MOCK_STREAMING_RESPONSE.split('').map(
      (char) => `data: ${JSON.stringify({ type: 'token', data: { content: char } })}\n\n`,
    ),
    `data: ${JSON.stringify({
      type: 'done',
      data: {
        messageId: 'new-msg-001',
        suggestedQuestions: [
          '면책기간 중 다른 보험사로 가입하면 어떻게 되나요?',
          '갑상선암도 면책기간이 있는 보험이 있나요?',
          '면책기간이 없는 암보험 상품이 있나요?',
        ],
      },
    })}\n\n`,
  ];
  return events.join('');
}

/**
 * 인증된 사용자 상태를 시뮬레이션합니다.
 * localStorage에 토큰을 설정하고 필수 API들을 mock합니다.
 * 반드시 page.goto() 호출 전에 실행해야 합니다.
 */
export async function mockAuthenticatedUser(page: Page) {
  await page.addInitScript(() => {
    localStorage.setItem('insurance_ai_access_token', 'mock-access-token');
    localStorage.setItem('insurance_ai_refresh_token', 'mock-refresh-token');
  });

  // 사용자 정보 API
  await page.route('**/api/v1/users/me', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        statusCode: 200,
        data: {
          id: '550e8400-e29b-41d4-a716-446655440000',
          nickname: '테스트 사용자',
          email: 'test@example.com',
          createdAt: '2026-01-01T00:00:00Z',
        },
      }),
    }),
  );

  // 채팅 세션 목록 API
  await page.route('**/api/v1/chat/sessions', (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, statusCode: 200, data: MOCK_SESSIONS }),
      });
    }
    if (route.request().method() === 'POST') {
      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          statusCode: 201,
          data: {
            id: 'new-session-001',
            title: null,
            lastMessagePreview: null,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
        }),
      });
    }
    return route.continue();
  });

  // 채팅 메시지 이력 API
  await page.route('**/api/v1/chat/sessions/*/messages', (route) => {
    if (route.request().method() === 'GET') {
      const url = route.request().url();
      const sessionIdMatch = url.match(/sessions\/([^/]+)\/messages/);
      const sessionId = sessionIdMatch?.[1] ?? '';
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          statusCode: 200,
          data: {
            sessionId,
            messages: MOCK_MESSAGES[sessionId] ?? [],
          },
        }),
      });
    }
    if (route.request().method() === 'POST') {
      // SSE 스트리밍 응답
      return route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: buildSseBody(),
      });
    }
    return route.continue();
  });

  // 채팅 세션 reply API (초기 메시지 AI 응답)
  await page.route('**/api/v1/chat/sessions/*/reply', (route) => {
    return route.fulfill({
      status: 200,
      contentType: 'text/event-stream',
      body: buildSseBody(),
    });
  });

  // 채팅 세션 삭제 API
  await page.route('**/api/v1/chat/sessions/*', (route) => {
    if (route.request().method() === 'DELETE') {
      return route.fulfill({ status: 204 });
    }
    return route.continue();
  });

  // 검색 API
  await page.route('**/api/v1/search', (route) => {
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, statusCode: 200, data: MOCK_SEARCH_RESULTS }),
    });
  });

  // 약관 상세 API
  await page.route('**/api/v1/policies/pdf/**', (route) => {
    // PDF blob 응답
    return route.fulfill({
      status: 200,
      contentType: 'application/pdf',
      body: Buffer.from('%PDF-1.4 mock pdf content'),
    });
  });

  await page.route(/\/api\/v1\/policies\/[^/]+$/, (route) => {
    const url = route.request().url();
    const articleId = url.split('/').pop() ?? '';
    const article = MOCK_POLICY_ARTICLES_MAP[articleId];
    if (article) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, statusCode: 200, data: article }),
      });
    }
    return route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ success: false, statusCode: 404, message: '약관을 찾을 수 없습니다' }),
    });
  });

  // 보충 데이터 API
  await page.route('**/api/v1/products/*/surrender-values', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, statusCode: 200, data: MOCK_SURRENDER_VALUES }),
    }),
  );

  await page.route('**/api/v1/products/*/premium-examples', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, statusCode: 200, data: MOCK_PREMIUM_EXAMPLES }),
    }),
  );

  await page.route('**/api/v1/products/*/indices', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ success: true, statusCode: 200, data: MOCK_PRICE_INDICES }),
    }),
  );
}
