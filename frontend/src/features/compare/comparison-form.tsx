import { useEffect, useMemo, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useSearchFilters } from '@/hooks/use-search-filters';
import type { ComparisonRequest, ComparisonCategoryInfo } from '@insurance-ai/api-client';

interface ComparisonFormProps {
  onSubmit: (request: ComparisonRequest) => void;
  isLoading: boolean;
  categories: ComparisonCategoryInfo[];
  categoriesLoading: boolean;
  onLoadCategories: () => void;
}

export function ComparisonForm({
  onSubmit,
  isLoading,
  categories,
  categoriesLoading,
  onLoadCategories,
}: ComparisonFormProps) {
  const { companies, loading: filtersLoading } = useSearchFilters();
  const [category, setCategory] = useState('');
  const [selectedCompanyIds, setSelectedCompanyIds] = useState<string[]>([]);
  const [customerAge, setCustomerAge] = useState('');
  const [customerGender, setCustomerGender] = useState<'MALE' | 'FEMALE' | ''>('');

  useEffect(() => {
    onLoadCategories();
  }, [onLoadCategories]);

  // 선택한 카테고리의 insuranceType
  const selectedCategoryType = useMemo(() => {
    return categories.find((c) => c.code === category)?.insuranceType ?? null;
  }, [categories, category]);

  // 카테고리 insuranceType에 맞는 보험사만 필터링
  const filteredCompanies = useMemo(() => {
    if (!selectedCategoryType) return companies;
    return companies.filter((c) => c.type === selectedCategoryType);
  }, [companies, selectedCategoryType]);

  // 카테고리 변경 시 보험사 선택 초기화
  const handleCategoryChange = (code: string) => {
    setCategory(code);
    setSelectedCompanyIds([]);
  };

  const isValid = category && selectedCompanyIds.length >= 2 && selectedCompanyIds.length <= 5;

  const toggleCompany = (id: string) => {
    setSelectedCompanyIds((prev) => {
      if (prev.includes(id)) return prev.filter((c) => c !== id);
      if (prev.length >= 5) return prev;
      return [...prev, id];
    });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValid) return;

    onSubmit({
      companyIds: selectedCompanyIds,
      category,
      age: customerAge ? parseInt(customerAge, 10) : undefined,
      gender: customerGender || undefined,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* 보험 유형 */}
      <div className="space-y-2">
        <label className="text-sm font-medium text-foreground">보험 유형 *</label>
        {categoriesLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            <span>카테고리 로딩 중...</span>
          </div>
        ) : categories.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {categories.map((cat) => (
              <button
                key={cat.code}
                type="button"
                onClick={() => handleCategoryChange(cat.code)}
                className={`px-3 py-1.5 rounded-full text-sm border transition-colors ${
                  category === cat.code
                    ? 'bg-primary text-primary-foreground border-primary'
                    : 'bg-background text-foreground border-border hover:border-primary/50'
                }`}
              >
                {cat.name}
                <span className="ml-1 text-xs opacity-70">({cat.productCount})</span>
              </button>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">비교 가능한 카테고리가 없습니다</p>
        )}
      </div>

      {/* 비교 보험사 */}
      <div className="space-y-2">
        <label className="text-sm font-medium text-foreground">
          비교 보험사 * <span className="text-muted-foreground font-normal">({selectedCompanyIds.length}/5, 최소 2개)</span>
        </label>
        {!category ? (
          <p className="text-sm text-muted-foreground">보험 유형을 먼저 선택하세요</p>
        ) : filtersLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            <span>보험사 목록 로딩 중...</span>
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {filteredCompanies.map((company) => (
              <button
                key={company.id}
                type="button"
                onClick={() => toggleCompany(company.id)}
                disabled={!selectedCompanyIds.includes(company.id) && selectedCompanyIds.length >= 5}
                className={`px-3 py-1.5 rounded-full text-sm border transition-colors ${
                  selectedCompanyIds.includes(company.id)
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
            분석 중...
          </>
        ) : (
          '비교 분석하기'
        )}
      </Button>
    </form>
  );
}
