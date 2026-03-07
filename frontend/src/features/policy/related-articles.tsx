import { Link } from 'react-router';
import { FileText } from 'lucide-react';

interface RelatedArticle {
  id: string;
  articleNumber: string;
  articleTitle: string;
}

interface RelatedArticlesProps {
  articles: RelatedArticle[];
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
            >
              <FileText className="size-3.5 shrink-0" />
              {article.articleNumber} {article.articleTitle}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
