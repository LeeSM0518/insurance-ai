import type { PolicyArticle, SurrenderValue, PremiumExample, PriceIndex } from '@/types/policy';

export const MOCK_POLICY_ARTICLE: PolicyArticle = {
  id: 'art-001',
  insuranceCompany: { id: 'c0a80101-0000-0000-0000-000000000001', name: '삼성생명', type: 'LIFE' },
  productId: 'prod-001',
  productName: '무배당 삼성 암보험',
  articleNumber: '제5조',
  articleTitle: '보장 제외',
  content: `① 회사는 이 계약의 보장개시일 이전에 암으로 진단 확정된 경우에는 보험금을 지급하지 아니하며, 이 경우 보험료를 납입한 날로부터 계약이 무효가 됩니다.

② 이 보험의 보장개시일은 계약일로부터 그 날을 포함하여 90일이 지난 날의 다음 날로 합니다. 이 기간을 면책기간(免責期間)이라 합니다.

③ 제2항에도 불구하고 계약이 자동갱신된 경우에는 자동갱신일을 보장개시일로 합니다.

④ 면책기간 중에 암으로 진단 확정된 경우에는 이 계약을 무효로 하며, 이미 납입한 보험료를 보험계약자에게 돌려드립니다.`,
  contentType: '면책사항',
  sectionType: '주계약',
  effectiveDate: '2024-01-01',
  dataSource: 'ASSOCIATION',
  relatedArticles: [
    { id: 'art-006', articleNumber: '제3조', articleTitle: '보장 내용' },
    { id: 'art-007', articleNumber: '제6조', articleTitle: '보험금 지급사유' },
    { id: 'art-008', articleNumber: '제10조', articleTitle: '해약환급금' },
  ],
};

export const MOCK_SURRENDER_VALUES: SurrenderValue[] = [
  { elapsedMonths: 12, premiumPaid: 360000, surrenderValue: 108000, surrenderRate: 30.0 },
  { elapsedMonths: 24, premiumPaid: 720000, surrenderValue: 302400, surrenderRate: 42.0 },
  { elapsedMonths: 36, premiumPaid: 1080000, surrenderValue: 572400, surrenderRate: 53.0 },
];

export const MOCK_PREMIUM_EXAMPLES: PremiumExample[] = [
  { gender: 'M', age: 40, insurancePeriod: '80세 만기', paymentPeriod: '20년납', premium: 30000 },
  { gender: 'F', age: 40, insurancePeriod: '80세 만기', paymentPeriod: '20년납', premium: 27500 },
];

export const MOCK_PRICE_INDICES: PriceIndex[] = [
  { gender: 'M', indexType: '표준체', indexValue: 1.0 },
  { gender: 'F', indexType: '표준체', indexValue: 0.92 },
];
