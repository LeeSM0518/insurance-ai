export interface CheatsheetRequest {
  category: string;
  companyNames: string[];
  recommendedProduct?: string;
  customerAge?: number;
  customerGender?: 'MALE' | 'FEMALE';
  focusAreas?: string[];
  chatSessionId?: string;
}

export interface ReportResponse {
  id: string;
  reportType: 'CHEATSHEET' | 'COMPARISON' | 'FOLLOWUP';
  title: string | null;
  content: string;
  metadata: Record<string, unknown> | null;
  shareToken: string | null;
  shareExpiresAt: string | null;
  chatSessionId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ReportSummaryResponse {
  id: string;
  reportType: string;
  title: string | null;
  createdAt: string;
}
