interface PolicyContentProps {
  articleNumber: string;
  articleTitle: string;
  content: string;
}

export function PolicyContent({ articleNumber, articleTitle, content }: PolicyContentProps) {
  return (
    <section>
      <h2 className="text-base font-semibold mb-3">
        {articleNumber} ({articleTitle})
      </h2>
      <div className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/90">{content}</div>
    </section>
  );
}
