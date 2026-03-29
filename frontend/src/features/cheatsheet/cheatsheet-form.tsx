import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useSearchFilters } from '@/hooks/use-search-filters';
import type { CheatsheetRequest } from '@/types/report';

const CATEGORY_OPTIONS = [
  { value: '암보험', label: '암보험' },
  { value: '실손보험', label: '실손보험' },
  { value: '종신보험', label: '종신보험' },
  { value: '건강보험', label: '건강보험' },
  { value: '어린이보험', label: '어린이보험' },
  { value: '연금보험', label: '연금보험' },
];

interface CheatsheetFormProps {
  onSubmit: (request: CheatsheetRequest) => void;
  isLoading: boolean;
}

export function CheatsheetForm({ onSubmit, isLoading }: CheatsheetFormProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const { companies, loading: filtersLoading } = useSearchFilters();
  const [category, setCategory] = useState('');
  const [selectedCompanies, setSelectedCompanies] = useState<string[]>([]);

  // URL 파라미터 기반 preselect
  useEffect(() => {
    const preCategory = searchParams.get('category');
    const preCompanies = searchParams.get('companyNames')?.split(',').filter(Boolean);
    if (preCategory) setCategory(preCategory);
    if (preCompanies && preCompanies.length > 0) setSelectedCompanies(preCompanies);
    if (preCategory || preCompanies) {
      setSearchParams({}, { replace: true });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const [recommendedProduct, setRecommendedProduct] = useState('');
  const [customerAge, setCustomerAge] = useState('');
  const [customerGender, setCustomerGender] = useState<'MALE' | 'FEMALE' | ''>('');

  const isValid = category && selectedCompanies.length >= 2 && selectedCompanies.length <= 5;

  const toggleCompany = (name: string) => {
    setSelectedCompanies((prev) => {
      if (prev.includes(name)) return prev.filter((c) => c !== name);
      if (prev.length >= 5) return prev;
      return [...prev, name];
    });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValid) return;

    onSubmit({
      category,
      companyNames: selectedCompanies,
      recommendedProduct: recommendedProduct || undefined,
      customerAge: customerAge ? parseInt(customerAge, 10) : undefined,
      customerGender: customerGender || undefined,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* 보험 유형 */}
      <div className="space-y-2">
        <label className="text-sm font-medium text-foreground">보험 유형 *</label>
        <div className="flex flex-wrap gap-2">
          {CATEGORY_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              onClick={() => setCategory(opt.value)}
              className={`px-3 py-1.5 rounded-full text-sm border transition-colors ${
                category === opt.value
                  ? 'bg-primary text-primary-foreground border-primary'
                  : 'bg-background text-foreground border-border hover:border-primary/50'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* 비교 보험사 */}
      <div className="space-y-2">
        <label className="text-sm font-medium text-foreground">
          비교 보험사 * <span className="text-muted-foreground font-normal">({selectedCompanies.length}/5, 최소 2개)</span>
        </label>
        {filtersLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            <span>보험사 목록 로딩 중...</span>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {companies.map((company) => (
              <button
                key={company.id}
                type="button"
                onClick={() => toggleCompany(company.name)}
                disabled={!selectedCompanies.includes(company.name) && selectedCompanies.length >= 5}
                className={`px-3 py-1.5 rounded-full text-sm border transition-colors ${
                  selectedCompanies.includes(company.name)
                    ? 'bg-primary text-primary-foreground border-primary'
                    : 'bg-background text-foreground border-border hover:border-primary/50 disabled:opacity-40 disabled:cursor-not-allowed'
                }`}
              >
                {company.name}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* 추천 상품 */}
      <div className="space-y-2">
        <label className="text-sm font-medium text-foreground">추천 상품 (선택)</label>
        <input
          type="text"
          value={recommendedProduct}
          onChange={(e) => setRecommendedProduct(e.target.value)}
          placeholder="예: 무배당 NEW 암보험"
          className="w-full px-3 py-2 rounded-md border border-border bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
        />
      </div>

      {/* 고객 정보 */}
      <div className="space-y-2">
        <label className="text-sm font-medium text-foreground">고객 정보 (선택)</label>
        <div className="flex gap-4">
          <input
            type="number"
            value={customerAge}
            onChange={(e) => setCustomerAge(e.target.value)}
            placeholder="나이"
            min={1}
            max={100}
            className="w-24 px-3 py-2 rounded-md border border-border bg-background text-foreground text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
          />
          <div className="flex gap-2">
            {(['MALE', 'FEMALE'] as const).map((g) => (
              <button
                key={g}
                type="button"
                onClick={() => setCustomerGender(customerGender === g ? '' : g)}
                className={`px-3 py-2 rounded-md text-sm border transition-colors ${
                  customerGender === g
                    ? 'bg-primary text-primary-foreground border-primary'
                    : 'bg-background text-foreground border-border hover:border-primary/50'
                }`}
              >
                {g === 'MALE' ? '남성' : '여성'}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* 제출 버튼 */}
      <Button type="submit" disabled={!isValid || isLoading} className="w-full">
        {isLoading ? (
          <>
            <Loader2 className="size-4 animate-spin mr-2" />
            생성 중...
          </>
        ) : (
          '치트시트 생성하기'
        )}
      </Button>
    </form>
  );
}
