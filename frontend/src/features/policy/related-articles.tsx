import { Link } from 'react-router';
import { FileText } from 'lucide-react';
import { trackEvent } from '@/lib/analytics';
import { CONTENT_TYPE_LABELS, type ContentType } from '@/types/search';

interface RelatedArticle {
  id: string;
  articleNumber: string;
  articleTitle: string;
  contentType: string;
}

interface RelatedArticlesProps {
  articles: RelatedArticle[];
}

function getDisplayLabel(article: RelatedArticle): string {
  const parts = [article.articleNumber, article.articleTitle].filter(Boolean);
  if (parts.length > 0) return parts.join(' ');
  return CONTENT_TYPE_LABELS[article.contentType as ContentType] ?? article.contentType;
}

export function RelatedArticles({ articles }: RelatedArticlesProps) {
  if (articles.length === 0) return null;

  return (
    <section>
      <h2 className="text-sm font-semibold mb-2">관련 조문</h2>
      <ul className="space-y-1.5">
        {articles.map((article) => (
          <li key={article.id}>
            <Link
              to={`/policies/${article.id}`}
              className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-primary hover:bg-muted transition-colors"
              onClick={() => trackEvent('policy', 'related_click', article.id)}
            >
              <FileText className="size-3.5 shrink-0" />
              {getDisplayLabel(article)}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
