import { Badge } from '@/components/ui/badge';
import type { SearchFilters, InsuranceCompany, Category, InsuranceType, ContentType } from '@/types/search';
import { CONTENT_TYPE_LABELS, INSURANCE_TYPE_LABELS } from '@/types/search';

interface FilterChipsProps {
  filters: SearchFilters;
  onFiltersChange: (filters: SearchFilters) => void;
  companies: InsuranceCompany[];
  categories: Category[];
}

export function FilterChips({ filters, onFiltersChange, companies, categories }: FilterChipsProps) {
  const toggleCompany = (id: string) => {
    const ids = filters.insuranceCompanyIds.includes(id)
      ? filters.insuranceCompanyIds.filter((c) => c !== id)
      : [...filters.insuranceCompanyIds, id];
    onFiltersChange({ ...filters, insuranceCompanyIds: ids });
  };

  const toggleInsuranceType = (type: InsuranceType) => {
    onFiltersChange({
      ...filters,
      insuranceType: filters.insuranceType === type ? null : type,
      // 생/손보 변경 시 카테고리 필터 초기화
      categories: filters.insuranceType === type ? filters.categories : [],
    });
  };

  const toggleCategory = (code: string) => {
    const cats = filters.categories.includes(code)
      ? filters.categories.filter((c) => c !== code)
      : [...filters.categories, code];
    onFiltersChange({ ...filters, categories: cats });
  };

  const toggleContentType = (type: ContentType) => {
    const types = filters.contentTypes.includes(type)
      ? filters.contentTypes.filter((t) => t !== type)
      : [...filters.contentTypes, type];
    onFiltersChange({ ...filters, contentTypes: types });
  };

  // 생/손보 필터가 선택되면 해당 보험종류 카테고리만 표시
  const filteredCategories = filters.insuranceType
    ? categories.filter((c) => c.insuranceType === filters.insuranceType)
    : categories;

  return (
    <section aria-labelledby="filter-label" className="space-y-3">
      <h2 id="filter-label" className="text-sm font-medium text-muted-foreground">
        필터
      </h2>

      {/* 보험사 */}
      <div className="flex gap-1.5 overflow-x-auto desktop:flex-wrap scrollbar-hide">
        {companies.map((company) => (
          <Badge
            key={company.id}
            variant={filters.insuranceCompanyIds.includes(company.id) ? 'default' : 'outline'}
            className="shrink-0 cursor-pointer"
            role="checkbox"
            aria-checked={filters.insuranceCompanyIds.includes(company.id)}
            onClick={() => toggleCompany(company.id)}
          >
            {company.name}
          </Badge>
        ))}
      </div>

      {/* 생/손보 */}
      <div className="flex gap-1.5">
        {(Object.entries(INSURANCE_TYPE_LABELS) as [InsuranceType, string][]).map(([type, label]) => (
          <Badge
            key={type}
            variant={filters.insuranceType === type ? 'default' : 'outline'}
            className="shrink-0 cursor-pointer"
            role="checkbox"
            aria-checked={filters.insuranceType === type}
            onClick={() => toggleInsuranceType(type)}
          >
            {label}
          </Badge>
        ))}
      </div>

      {/* 상품 카테고리 */}
      <div className="flex gap-1.5 overflow-x-auto desktop:flex-wrap scrollbar-hide">
        {filteredCategories.map((cat) => (
          <Badge
            key={cat.code}
            variant={filters.categories.includes(cat.code) ? 'default' : 'outline'}
            className="shrink-0 cursor-pointer"
            role="checkbox"
            aria-checked={filters.categories.includes(cat.code)}
            onClick={() => toggleCategory(cat.code)}
          >
            {cat.name}
          </Badge>
        ))}
      </div>

      {/* 내용 유형 */}
      <div className="flex gap-1.5 overflow-x-auto desktop:flex-wrap scrollbar-hide">
        {(Object.entries(CONTENT_TYPE_LABELS) as [ContentType, string][]).map(([type, label]) => (
          <Badge
            key={type}
            variant={filters.contentTypes.includes(type) ? 'default' : 'outline'}
            className="shrink-0 cursor-pointer"
            role="checkbox"
            aria-checked={filters.contentTypes.includes(type)}
            onClick={() => toggleContentType(type)}
          >
            {label}
          </Badge>
        ))}
      </div>
    </section>
  );
}
