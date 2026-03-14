import type { DataSource } from './policy';

// SSE 이벤트 타입
export type SSEEventType = 'searching' | 'sources_found' | 'generating' | 'token' | 'done' | 'error';

// 메시지 역할
export type MessageRole = 'USER' | 'ASSISTANT';

// 채팅 메시지
export interface ChatMessage {
  id: string;
  role: MessageRole;
  content: string;
  sources?: Source[];
  suggestedQuestions?: string[];
  createdAt: string;
}

// 출처 정보
export interface Source {
  articleId: string;
  insuranceCompany: string;
  productId: string;
  productName: string;
  contentType: string;
  articleNumber: string;
  articleTitle: string;
  relevanceScore: number;
  dataSource: DataSource;
  dataSourceId?: string;
  hasPdfSource?: boolean;
  linkedPdfDataSourceId?: string;
}

// 채팅 세션
export interface ChatSession {
  id: string;
  title: string;
  lastMessagePreview: string;
  createdAt: string;
  updatedAt: string;
}
