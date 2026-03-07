import type { ChatSession, ChatMessage, Source } from '@/types/chat';

// 현재 시각 기준 상대 시간 생성
const now = new Date();
const hoursAgo = (h: number) => new Date(now.getTime() - h * 60 * 60 * 1000).toISOString();
const daysAgo = (d: number) => new Date(now.getTime() - d * 24 * 60 * 60 * 1000).toISOString();

export const MOCK_SESSIONS: ChatSession[] = [
  {
    id: 'session-001',
    title: '삼성생명 암보험 면책기간',
    lastMessagePreview: '면책기간은 계약일로부터 90일이 지난 날의 다음 날부터 시작됩니다.',
    createdAt: hoursAgo(2),
    updatedAt: hoursAgo(2),
  },
  {
    id: 'session-002',
    title: '실손보험 4세대 보장범위',
    lastMessagePreview: '4세대 실손보험은 급여와 비급여를 구분하여 보장합니다.',
    createdAt: daysAgo(1),
    updatedAt: daysAgo(1),
  },
  {
    id: 'session-003',
    title: '치아보험 임플란트 보장',
    lastMessagePreview: '임플란트는 보통 1~2년의 면책기간이 적용됩니다.',
    createdAt: daysAgo(3),
    updatedAt: daysAgo(3),
  },
];

export const MOCK_SOURCES: Source[] = [
  {
    articleId: 'art-001',
    insuranceCompany: '삼성생명',
    productId: 'prod-001',
    productName: '무배당 삼성 암보험',
    articleNumber: '제5조',
    articleTitle: '보장 제외',
    relevanceScore: 0.92,
    dataSource: 'ASSOCIATION',
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
  },
];

export const MOCK_MESSAGES: Record<string, ChatMessage[]> = {
  'session-001': [
    {
      id: 'msg-001',
      role: 'USER',
      content: '삼성생명 암보험의 면책기간이 어떻게 되나요?',
      createdAt: hoursAgo(2),
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
      createdAt: hoursAgo(2),
    },
  ],
  'session-002': [
    {
      id: 'msg-003',
      role: 'USER',
      content: '4세대 실손보험이 뭔가요?',
      createdAt: daysAgo(1),
    },
    {
      id: 'msg-004',
      role: 'ASSISTANT',
      content:
        '**4세대 실손보험**에 대해 설명드리겠습니다.\n\n4세대 실손보험은 2021년 7월부터 판매된 새로운 형태의 실손의료보험입니다.\n\n### 주요 특징\n\n1. **급여/비급여 분리**: 급여 항목과 비급여 항목을 별도 특약으로 구분\n2. **비급여 자기부담률 상향**: 비급여 항목의 자기부담률이 30%로 상향\n3. **재가입 주기**: 5년마다 재가입 (1~3세대는 15년)',
      suggestedQuestions: ['3세대와 4세대의 차이점은?', '비급여 자기부담률이 높아지면 불리한 건가요?'],
      createdAt: daysAgo(1),
    },
  ],
  'session-003': [
    {
      id: 'msg-005',
      role: 'USER',
      content: '치아보험에서 임플란트도 보장되나요?',
      createdAt: daysAgo(3),
    },
    {
      id: 'msg-006',
      role: 'ASSISTANT',
      content:
        '네, **치아보험**에서 임플란트 보장이 가능합니다.\n\n### 보장 조건\n\n- 보통 **1~2년의 면책기간**이 적용됩니다\n- 보장 한도는 보험사마다 다르며, 일반적으로 개당 50~100만원 수준입니다\n- 연간 보장 횟수 제한이 있을 수 있습니다',
      suggestedQuestions: ['면책기간이 가장 짧은 치아보험은?', '임플란트 vs 브릿지 보장 차이는?'],
      createdAt: daysAgo(3),
    },
  ],
};
