import { CONTENT_TYPE_LABELS, type ContentType } from '@/types/search';

interface PolicyContentProps {
  articleNumber: string;
  articleTitle: string;
  contentType: string;
  content: string;
}

export function PolicyContent({ articleNumber, articleTitle, contentType, content }: PolicyContentProps) {
  const displayTitle =
    [articleNumber, articleTitle].filter(Boolean).join(' ') ||
    CONTENT_TYPE_LABELS[contentType as ContentType] ||
    contentType;

  return (
    <section>
      <h2 className="text-lg font-semibold mb-3">{displayTitle}</h2>
      <div className="whitespace-pre-wrap text-base leading-relaxed text-foreground/90">{content}</div>
    </section>
  );
}
