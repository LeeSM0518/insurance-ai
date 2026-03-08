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
  gender: 'M' | 'F';
  age: number;
  insurancePeriod: string;
  paymentPeriod: string;
  premium: number;
}

export interface PriceIndex {
  gender: 'M' | 'F';
  indexType: string;
  indexValue: number;
}
