// 데이터 출처 유형
export type DataSource = 'ASSOCIATION' | 'PRODUCT_SUMMARY' | 'FULL_POLICY';

// 약관 조문 상세
export interface PolicyArticle {
  id: string;
  insuranceCompany: {
    id: string;
    name: string;
    type: 'LIFE' | 'NON_LIFE';
  };
  productId: string;
  productName: string;
  articleNumber: string;
  articleTitle: string;
  content: string;
  contentType: string;
  sectionType: string;
  effectiveDate: string;
  dataSource: DataSource;
  dataSourceId?: string;
  hasPdfSource: boolean;
  relatedArticles: {
    id: string;
    articleNumber: string;
    articleTitle: string;
    contentType: string;
  }[];
}

// 검색 결과 아이템
export interface SearchResultItem {
  id: string;
  insuranceCompany: {
    id: string;
    name: string;
    type: 'LIFE' | 'NON_LIFE';
  };
  productId: string;
  productName: string;
  contentType: string;
  articleNumber: string;
  articleTitle: string;
  content: string;
  highlight: string;
  relevanceScore: number;
  dataSource: DataSource;
  supplementary: {
    hasSurrenderValues: boolean;
    hasPremiumExamples: boolean;
    hasPriceIndices: boolean;
  };
}

// 검색 결과 응답
export interface SearchResponse {
  query: string;
  totalCount: number;
  page: number;
  size: number;
  hasNext: boolean;
  results: SearchResultItem[];
}

// 보충 데이터
export interface SurrenderValue {
  elapsedMonths: number;
  premiumPaid: number;
  surrenderValue: number;
  surrenderRate: number;
}

export interface PremiumExample {
  gender: string;
  age: number;
  insurancePeriod: string;
  paymentPeriod: string;
  premium: number;
}

// 가격지수 유형
export type IndexType = 'PRICE' | 'EXTRA_PREMIUM' | 'CONTRACT_COST' | 'COVERAGE';

export const INDEX_TYPE_LABELS: Record<IndexType, string> = {
  PRICE: '보험가격지수',
  EXTRA_PREMIUM: '부가보험료지수',
  CONTRACT_COST: '계약체결비용지수',
  COVERAGE: '보장범위지수',
};

export interface PriceIndex {
  gender: string;
  indexType: string;
  indexValue: number;
}
