import type { InsuranceCompany, Category } from '@/types/search';

// 보험사 목록 (GET /api/v1/insurance-companies 응답 구조)
export const MOCK_INSURANCE_COMPANIES: InsuranceCompany[] = [
  { id: 'c0a80101-0000-0000-0000-000000000001', name: '삼성생명', type: 'LIFE' },
  { id: 'c0a80101-0000-0000-0000-000000000002', name: '한화생명', type: 'LIFE' },
  { id: 'c0a80101-0000-0000-0000-000000000003', name: '교보생명', type: 'LIFE' },
  { id: 'c0a80101-0000-0000-0000-000000000004', name: '현대해상', type: 'NON_LIFE' },
  { id: 'c0a80101-0000-0000-0000-000000000005', name: 'DB손해보험', type: 'NON_LIFE' },
  { id: 'c0a80101-0000-0000-0000-000000000006', name: 'KB손해보험', type: 'NON_LIFE' },
  { id: 'c0a80101-0000-0000-0000-000000000007', name: '메리츠화재', type: 'NON_LIFE' },
  { id: 'c0a80101-0000-0000-0000-000000000008', name: '동양생명', type: 'LIFE' },
];

// 카테고리 목록 (GET /api/v1/categories 응답 구조)
export const MOCK_CATEGORIES: Category[] = [
  {
    code: 'PROTECTION',
    name: '보장성보험',
    insuranceType: 'LIFE',
    subcategories: [
      { code: 'WHOLE_LIFE', name: '종신' },
      { code: 'TERM_LIFE', name: '정기' },
      { code: 'DISEASE', name: '질병' },
      { code: 'CANCER', name: '암' },
      { code: 'CI', name: 'CI' },
      { code: 'ACCIDENT', name: '상해' },
      { code: 'CHILD', name: '어린이' },
      { code: 'DENTAL', name: '치아' },
      { code: 'NURSING', name: '간병/치매' },
      { code: 'OTHER_PROTECTION', name: '기타' },
    ],
  },
  { code: 'SAVINGS', name: '저축성보험', insuranceType: 'LIFE' },
  { code: 'VARIABLE_PROTECTION', name: '변액보험(보장성)', insuranceType: 'LIFE' },
  { code: 'VARIABLE_SAVINGS', name: '변액보험(저축성)', insuranceType: 'LIFE' },
  { code: 'PENSION', name: '연금저축', insuranceType: 'LIFE' },
  { code: 'HEALTH', name: '실손의료보험', insuranceType: 'LIFE' },
  {
    code: 'LONG_TERM',
    name: '장기보장성보험',
    insuranceType: 'NON_LIFE',
    subcategories: [
      { code: 'LT_ACCIDENT', name: '상해' },
      { code: 'LT_DISEASE', name: '질병' },
      { code: 'LT_CANCER', name: '암' },
      { code: 'LT_DRIVER', name: '운전자' },
      { code: 'LT_CHILD', name: '어린이' },
      { code: 'LT_FIRE', name: '화재' },
      { code: 'LT_COMPREHENSIVE', name: '종합' },
      { code: 'LT_EXPENSE', name: '비용' },
      { code: 'LT_NURSING', name: '간병/치매' },
      { code: 'LT_DENTAL', name: '치아' },
      { code: 'LT_PET', name: '반려동물' },
      { code: 'LT_OTHER', name: '기타' },
    ],
  },
  {
    code: 'NL_HEALTH',
    name: '실손의료보험(손보)',
    insuranceType: 'NON_LIFE',
    subcategories: [
      { code: 'STANDARD', name: '표준형' },
      { code: 'PRE_EXISTING', name: '유병자형' },
      { code: 'SENIOR', name: '노후실손' },
    ],
  },
  { code: 'AUTO', name: '자동차보험', insuranceType: 'NON_LIFE' },
  { code: 'NL_SAVINGS', name: '저축성보험(손보)', insuranceType: 'NON_LIFE' },
];

// 추천 검색어 (GET /api/v1/search/suggestions 응답 구조)
export const MOCK_SUGGESTIONS: string[] = [
  '암보험 면책기간',
  '실손 4세대',
  '치아보험 비교',
  '간병보험 보장',
  '운전자보험 벌금',
];
