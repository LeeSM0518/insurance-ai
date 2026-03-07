import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { Search, MessageSquare } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

interface SearchBarProps {
  onSearch: (query: string) => void;
  defaultValue?: string;
}

export function SearchBar({ onSearch, defaultValue = '' }: SearchBarProps) {
  const [query, setQuery] = useState(defaultValue);
  const navigate = useNavigate();

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;
    onSearch(trimmed);
  };

  const handleAiClick = () => {
    const trimmed = query.trim();
    if (trimmed) {
      navigate(`/chat?q=${encodeURIComponent(trimmed)}`);
    } else {
      navigate('/chat');
    }
  };

  return (
    <div className="space-y-2">
      <form onSubmit={handleSubmit} role="search" className="flex gap-2">
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="예: 암보험 면책기간 비교"
          aria-label="약관 검색"
          autoFocus
          className="h-10 flex-1"
        />
        <Button type="submit" size="icon" aria-label="검색" className="size-10 shrink-0">
          <Search className="size-4" />
        </Button>
      </form>
      <button
        type="button"
        onClick={handleAiClick}
        className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
      >
        <MessageSquare className="size-3.5" />
        AI에게 바로 질문하기
      </button>
    </div>
  );
}
