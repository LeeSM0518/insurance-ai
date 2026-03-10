import { Badge } from '@/components/ui/badge';
import type { PolicyArticle } from '@/types/policy';
import { CONTENT_TYPE_LABELS } from '@/types/search';
import type { ContentType } from '@/types/search';

interface PolicyMetaProps {
  article: PolicyArticle;
}

const SECTION_TYPE_LABELS: Record<string, string> = {
  MAIN: '주계약',
  RIDER: '특약',
};

const DATA_SOURCE_LABELS: Record<string, string> = {
  ASSOCIATION: '생보/손보협회 비교공시',
  PRODUCT_SUMMARY: '상품요약서',
  FULL_POLICY: '약관 전문',
};

export function PolicyMeta({ article }: PolicyMetaProps) {
  return (
    <div className="space-y-1">
      <div className="flex items-center gap-2">
        <span className="text-base font-medium text-primary">{article.insuranceCompany.name}</span>
        <Badge variant="outline" className="text-[10px]">
          {article.insuranceCompany.type === 'LIFE' ? '생보' : '손보'}
        </Badge>
      </div>
      <h1 className="text-xl font-semibold">{article.productName}</h1>
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <span>{CONTENT_TYPE_LABELS[article.contentType as ContentType] ?? article.contentType}</span>
        <span>·</span>
        <span>{SECTION_TYPE_LABELS[article.sectionType] ?? article.sectionType}</span>
      </div>
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <span>적용일: {article.effectiveDate}</span>
        <span>·</span>
        <span>출처: {DATA_SOURCE_LABELS[article.dataSource] || article.dataSource}</span>
      </div>
    </div>
  );
}
