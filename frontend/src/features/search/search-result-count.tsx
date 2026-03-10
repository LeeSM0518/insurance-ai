interface SearchResultCountProps {
  count: number;
}

export function SearchResultCount({ count }: SearchResultCountProps) {
  return (
    <p role="status" aria-live="polite" className="text-base text-muted-foreground">
      <span className="font-semibold text-foreground">{count}건</span>의 약관 조문이 검색되었습니다
    </p>
  );
}
