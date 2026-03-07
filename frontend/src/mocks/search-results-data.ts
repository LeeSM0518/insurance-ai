import type { SearchResultItem } from '@/types/policy';

export const MOCK_SEARCH_RESULTS: SearchResultItem[] = [
  {
    id: 'art-001',
    insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000001', name: '삼성생명', type: 'LIFE' },
    productId: 'prod-001',
    productName: '무배당 삼성 암보험',
    contentType: 'EXEMPTION',
    articleNumber: '제5조',
    articleTitle: '보장 제외',
    content:
      '이 보험의 보장개시일은 계약일로부터 90일이 지난 날의 다음 날로 합니다. 이 기간을 면책기간이라 하며, 면책기간 중 발생한 암은 보장하지 않습니다.',
    highlight:
      '이 보험의 <mark>보장개시일</mark>은 계약일로부터 <mark>90일</mark>이 지난 날의 다음 날로 합니다. 이 기간을 <mark>면책기간</mark>이라 하며...',
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
    content:
      '피보험자가 보험기간 중 암으로 진단 확정된 경우 암진단금을 지급합니다. 다만, 보장개시일 이전에 발생한 경우는 제외합니다.',
    highlight:
      '피보험자가 보험기간 중 <mark>암</mark>으로 진단 확정된 경우 암진단금을 지급합니다. 다만, <mark>보장개시일</mark> 이전에 발생한 경우는 제외합니다.',
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
    content:
      '회사는 다음 중 하나에 해당하는 사유로 보험금 지급사유가 발생한 때에는 보험금을 지급하지 않습니다. 1. 피보험자가 고의로 자신을 해친 경우',
    highlight:
      '회사는 다음 중 하나에 해당하는 사유로 보험금 지급사유가 발생한 때에는 <mark>보험금</mark>을 지급하지 않습니다.',
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
    content:
      '갑상선암, 기타피부암, 제자리암, 경계성종양의 경우 소액암으로 분류하며, 소액암진단금을 별도로 지급합니다.',
    highlight:
      '<mark>갑상선암</mark>, 기타피부암, 제자리암, 경계성종양의 경우 소액암으로 분류하며, 소액암진단금을 별도로 지급합니다.',
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
    content:
      '일반암 진단금 3,000만원, 소액암 진단금 300만원, 유사암 진단금 300만원을 보험기간 내 최초 1회에 한하여 지급합니다.',
    highlight:
      '일반<mark>암</mark> 진단금 3,000만원, 소액<mark>암</mark> 진단금 300만원을 보험기간 내 최초 1회에 한하여 지급합니다.',
    relevanceScore: 0.55,
    dataSource: 'ASSOCIATION',
    supplementary: { hasSurrenderValues: true, hasPremiumExamples: true, hasPriceIndices: true },
  },
];
