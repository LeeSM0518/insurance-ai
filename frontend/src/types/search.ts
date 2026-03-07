// 보험 종류
export type InsuranceType = 'LIFE' | 'NON_LIFE';

// 내용 유형
export type ContentType =
  | 'COVERAGE'
  | 'EXEMPTION'
  | 'REDUCTION'
  | 'QUALIFICATION'
  | 'SPECIAL_TERMS'
  | 'QNA'
  | 'COVERAGE_DETAIL'
  | 'CLAIM_DOCUMENTS'
  | 'ARTICLE';

// 보험사
export interface InsuranceCompany {
  id: string;
  name: string;
  type: InsuranceType;
}

// 카테고리
export interface Category {
  code: string;
  name: string;
  insuranceType: InsuranceType;
  subcategories?: { code: string; name: string }[];
}

// 검색 필터
export interface SearchFilters {
  insuranceCompanyIds: string[];
  insuranceType: InsuranceType | null;
  categories: string[];
  contentTypes: ContentType[];
}

// 내용 유형 라벨 매핑
export const CONTENT_TYPE_LABELS: Record<ContentType, string> = {
  COVERAGE: '보장내용',
  EXEMPTION: '면책사항',
  REDUCTION: '감액기간',
  QUALIFICATION: '가입자격',
  SPECIAL_TERMS: '특별약관',
  QNA: '문답식 해설',
  COVERAGE_DETAIL: '담보별 보장내용',
  CLAIM_DOCUMENTS: '청구 서류',
  ARTICLE: '기타 조문',
};

// 보험 종류 라벨
export const INSURANCE_TYPE_LABELS: Record<InsuranceType, string> = {
  LIFE: '생보',
  NON_LIFE: '손보',
};
